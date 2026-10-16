/** Owner-only reports. Deploy as owner/anyone; authenticate every POST with private token. */
function setupRentalSync() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  if (!spreadsheet) throw new Error('請從要使用的試算表開啟Apps Script。');
  const properties = PropertiesService.getScriptProperties();
  properties.setProperty('SPREADSHEET_ID', spreadsheet.getId());
  let token = properties.getProperty('SYNC_TOKEN');
  if (!token) { token = (Utilities.getUuid() + Utilities.getUuid()).replace(/-/g, ''); properties.setProperty('SYNC_TOKEN', token); }
  SpreadsheetApp.getUi().alert('App同步金鑰（只填入App，不要分享）', token, SpreadsheetApp.getUi().ButtonSet.OK);
}
function doGet() { return output_({ok:false, code:'POST_REQUIRED'}); }
function output_(value) { return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON); }
function sameToken_(a,b) { if(typeof a!=='string'||typeof b!=='string'||a.length!==64||b.length!==64)return false;let diff=0;for(let i=0;i<64;i++)diff|=a.charCodeAt(i)^b.charCodeAt(i);return diff===0; }
function text_(v) { if(typeof v!=='string'||v.length>500)throw new Error('INVALID_REPORT');return v; }
function money_(v) { if(!Number.isSafeInteger(v))throw new Error('INVALID_REPORT');return v; }
function tables_(report) {
  if(!report||!/^20\d{2}-(0[1-9]|1[0-2])$/.test(report.month)||!Array.isArray(report.properties)||!Array.isArray(report.tenants)||report.properties.length>100||report.tenants.length>5000)throw new Error('INVALID_REPORT');
  const keys=['billed','paid','remaining','cash','topups','expenses','held','net'];
  const labels=['本月已建帳單應收','截至月底已抵付帳單','本月帳單未付','本月租金水電實收','本月結算補繳實收','本月分攤支出','月底持有押金','本月淨收款（不含押金／轉帳）'];
  const summary=[['月份',report.month]];keys.forEach((k,i)=>summary.push([labels[i],money_(report.total[k])]));summary.push(['口徑','實收按收款日期，帳單按月份，抵付與持有押金截至月底。'],['淨收款','不含押金與轉帳，不是銀行餘額或會計損益。'],['未建帳單','不表示已繳清。'],['分享','本試算表包含所有租客姓名，供房東查看，請勿分享給租客。']);
  const properties=[['不動產','已建帳單應收','月底已抵付','月帳單未付','租金水電實收','結算補繳實收','分攤支出','月底持有押金','淨收款']];const names=new Map();report.properties.forEach(p=>{const id=text_(p.id);if(names.has(id))throw new Error('INVALID_REPORT');names.set(id,text_(p.name));properties.push([p.name,...keys.map(k=>money_(p[k]))]);});
  const tenants=[['不動產','房號／單位','租客','已建帳單應收','月底已抵付','月帳單未付','租金水電實收','結算補繳實收','月底持有押金','狀態']];report.tenants.forEach(t=>{if(!names.has(t.propertyId))throw new Error('INVALID_REPORT');tenants.push([names.get(t.propertyId),text_(t.code),text_(t.name),...['billed','paid','remaining','cash','topups','held'].map(k=>money_(t[k])),text_(t.status)]);});
  return [{name:'租務_'+report.month+'_總覽',rows:summary,filter:false},{name:'租務_'+report.month+'_各不動產',rows:properties,filter:true},{name:'租務_'+report.month+'_租約明細',rows:tenants,filter:true}];
}
function requests_(tables,existing) {
  const requests=[];const byTitle=new Map(existing.map(s=>[s.properties.title,s.properties]));let nextId=Math.max(0,...existing.map(s=>s.properties.sheetId))+1;
  tables.forEach(table=>{
    const old=byTitle.get(table.name);const id=old?old.sheetId:nextId++;const columns=Math.max(...table.rows.map(r=>r.length));const grid={rowCount:Math.max(30,table.rows.length,old?old.gridProperties.rowCount:0),columnCount:Math.max(columns,old?old.gridProperties.columnCount:0),frozenRowCount:1};
    if(!old)requests.push({addSheet:{properties:{sheetId:id,title:table.name,gridProperties:grid}}});else requests.push({updateSheetProperties:{properties:{sheetId:id,gridProperties:grid},fields:'gridProperties'}});
    requests.push({updateCells:{range:{sheetId:id},fields:'userEnteredValue,userEnteredFormat'}});
    const rows=table.rows.map((r,i)=>({values:r.map(v=>({userEnteredValue:typeof v==='number'?{numberValue:v}:{stringValue:v},userEnteredFormat:i===0?{textFormat:{bold:true},backgroundColor:{red:0.93,green:0.87,blue:0.81},wrapStrategy:'WRAP'}:typeof v==='number'?{numberFormat:{type:'NUMBER',pattern:'#,##0;[Red]-#,##0'}}:{wrapStrategy:'CLIP'}}))}));
    requests.push({updateCells:{start:{sheetId:id,rowIndex:0,columnIndex:0},rows,fields:'userEnteredValue,userEnteredFormat'}});
    requests.push({updateDimensionProperties:{range:{sheetId:id,dimension:'COLUMNS',startIndex:0,endIndex:columns},properties:{pixelSize:160},fields:'pixelSize'}});
    requests.push({clearBasicFilter:{sheetId:id}});if(table.filter)requests.push({setBasicFilter:{filter:{range:{sheetId:id,startRowIndex:0,endRowIndex:table.rows.length,startColumnIndex:0,endColumnIndex:columns}}}});
  });return requests;
}
function doPost(e) {
  let lock;
  try {
    if(!e||!e.postData||e.postData.contents.length>37000000)return output_({ok:false,code:'INVALID_REQUEST'});
    const request=JSON.parse(e.postData.contents);if(request.version===2)return output_(sharedPost_(request));const properties=PropertiesService.getScriptProperties();const token=properties.getProperty('SYNC_TOKEN'),id=properties.getProperty('SPREADSHEET_ID');
    if(!token||!id)return output_({ok:false,code:'NOT_CONFIGURED'});
    if(!sameToken_(request.token,token))return output_({ok:false,code:'UNAUTHORIZED'});
    if(request.version!==1||typeof request.syncId!=='string'||!/^[a-zA-Z0-9-]{1,80}$/.test(request.syncId))return output_({ok:false,code:'INVALID_REQUEST'});
    const url='https://docs.google.com/spreadsheets/d/'+id+'/edit';
    if(request.mode==='check')return output_({ok:true,syncId:request.syncId,url});
    if(request.mode!=='sync')return output_({ok:false,code:'INVALID_REQUEST'});
    const tables=tables_(request.report);lock=LockService.getScriptLock();if(!lock.tryLock(20000)){lock=null;return output_({ok:false,code:'BUSY'});}
    const timestamp=new Date().toISOString();tables.push({name:'租務_同步資訊',rows:[['項目','內容'],['最後成功時間',timestamp],['最後同步月份',request.report.month],['操作','同月份重傳覆寫租務報表，不追加交易；其他月份與非租務分頁保留。']],filter:false});
    const existing=Sheets.Spreadsheets.get(id,{fields:'sheets.properties'}).sheets||[];
    Sheets.Spreadsheets.batchUpdate({requests:requests_(tables,existing)},id);
    return output_({ok:true,syncId:request.syncId,url,month:request.report.month,syncedAt:timestamp});
  } catch(error) { return output_({ok:false,code:error.message==='INVALID_REPORT'?'INVALID_REPORT':'SYNC_FAILED'}); }
  finally {if(lock)lock.releaseLock();}
}

