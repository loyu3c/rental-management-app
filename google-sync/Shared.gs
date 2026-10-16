/** Dedicated, allowlisted snapshots; Drive permissions are verified before a URL is returned. */
function gmail_(s){if(typeof s!=='string'||!/^[-A-Za-z0-9.!#$%&'*+\/=?^_`{|}~]+@gmail\.com$/i.test(s))throw new Error('INVALID_REPORT');return s.toLowerCase();}
function subject_(r){if(!r||!['owner','tenant'].includes(r.audience)||typeof r.subjectId!=='string'||!/^[-a-zA-Z0-9]{1,80}$/.test(r.subjectId))throw new Error('INVALID_REPORT');return r.audience+'_'+r.subjectId;}
function permissions_(id){let result=[],page;do{const r=Drive.Permissions.list(id,{fields:'nextPageToken,permissions(id,type,role,emailAddress)',pageToken:page,pageSize:100});result=result.concat(r.permissions||[]);page=r.nextPageToken;}while(page);return result;}
function private_(id){permissions_(id).filter(p=>p.role!=='owner').forEach(p=>Drive.Permissions.remove(id,p.id));if(permissions_(id).some(p=>p.role!=='owner'))throw new Error('PERMISSION_FAILED');}
function share_(id,email){const list=permissions_(id);if(!list.some(p=>p.role==='owner'&&String(p.emailAddress).toLowerCase()===email))Drive.Permissions.create({type:'user',role:'reader',emailAddress:email},id,{sendNotificationEmail:false,fields:'id'});const now=permissions_(id);if(!now.some(p=>p.type==='user'&&String(p.emailAddress).toLowerCase()===email&&['reader','owner'].includes(p.role))||now.some(p=>p.role!=='owner'&&(p.type!=='user'||p.role!=='reader'||String(p.emailAddress).toLowerCase()!==email)))throw new Error('PERMISSION_FAILED');}
function ownerTables_(r){
  const base=tables_(r);base.forEach(t=>{t.name=t.name.replace('租務_'+r.month+'_','');});
  base[0].rows=base[0].rows.filter(row=>row[0]!=='分享');base[0].rows.push(['個資','只含此房東對應不動產；不顯示租客姓名、Gmail、照片、備註。'],['出租狀態基準日',text_(r.asOf)]);
  base[1].rows[0].push("出租中","未出租","停用");r.properties.forEach((p,i)=>base[1].rows[i+1].push(money_(p.rented||0),money_(p.vacant||0),money_(p.disabled||0)));
  base[2].rows.forEach((row,i)=>{row.splice(2,1);});base[2].name='單位帳務';
  const names=new Map(r.properties.map(p=>[p.id,p.name]));if(!Array.isArray(r.units)||!Array.isArray(r.accounts)||r.units.length>5000||r.accounts.length>500)throw new Error('INVALID_REPORT');
  const units=[['不動產','單位','出租狀態','啟用出租','起租日','到期日']];r.units.forEach(u=>{if(!names.has(u.propertyId)||typeof u.enabled!=='boolean')throw new Error('INVALID_REPORT');units.push([names.get(u.propertyId),text_(u.code),text_(u.status),u.enabled?'啟用':'停用',text_(u.start||''),text_(u.end||'')]);});
  const accounts=[['帳戶代稱','類型','月底帳面餘額']];r.accounts.forEach(a=>accounts.push([text_(a.name),text_(a.type),a.withheld?"歸屬／起算日待核對，不公開餘額":money_(a.balance)]));
  base.push({name:'出租概況',rows:units,filter:true},{name:'帳戶摘要',rows:accounts,filter:true});return base;
}
function tenantTables_(r,contractUrl){
  if(!r||!r.terms||!Array.isArray(r.bills)||!Array.isArray(r.payments)||!Array.isArray(r.deposits)||r.bills.length>5000||r.payments.length>5000||r.deposits.length>5000)throw new Error('INVALID_REPORT');const t=r.terms,d=r.depositSummary;
  const terms=[['項目','內容'],['出租單位',text_(t.code)],['起租日',text_(t.start)],['到期日',text_(t.end)],['退租日',text_(t.terminatedOn||'')],['計價方式',text_(t.pricing)],['租金',money_(t.rent)],['繳款週期',text_(t.cycle)],['約定押金',money_(t.deposit)],['已收押金',money_(d.received)],['已退押金',money_(d.refunded)],['已抵付押金',money_(d.applied)],['持有押金',money_(d.held)],['PDF 合約',contractUrl||'尚未附上'],['說明','此檔僅含本份租約；帳單不代表已收款，手機變更後需重新同步。']];
  const bills=[['月份','類別','應繳日','租金','水費','電費','應收合計','已抵付','未繳','上期電度','本期電度','每度電價','租金起日','租金迄日']];r.bills.forEach(b=>bills.push([text_(b.month),text_(b.kind),text_(b.due),...['rent','water','electricity','total','paid','remaining'].map(k=>money_(b[k])),...['previous','current','rate'].map(k=>b[k]===undefined?'':money_(b[k])),text_(b.periodStart||''),text_(b.periodEnd||'')]));
  const payments=[['收款日','類型','實收／抵付金額','尚未分配','分配至月份／項目']];r.payments.forEach(p=>{if(!Array.isArray(p.allocations))throw new Error('INVALID_REPORT');payments.push([text_(p.date),text_(p.kind),money_(p.amount),money_(p.unallocated),p.allocations.map(a=>text_(a.month)+' '+text_(a.kind)+' $'+money_(a.amount)).join('\n')]);});
  const deposits=[['日期','類型','金額']];r.deposits.forEach(p=>deposits.push([text_(p.date),text_(p.kind),money_(p.amount)]));return [{name:'租約與押金',rows:terms,filter:false},{name:'租金水電帳單',rows:bills,filter:true},{name:'歷史繳費',rows:payments,filter:true},{name:'押金明細',rows:deposits,filter:true}];
}
function sharedPost_(request){
  const props=PropertiesService.getScriptProperties();let lock,record,key;
  try{
    if(!sameToken_(request.token,props.getProperty('SYNC_TOKEN')))return {ok:false,code:'UNAUTHORIZED'};
    if(typeof request.syncId!=='string'||!/^[-a-zA-Z0-9]{1,80}$/.test(request.syncId))throw new Error('INVALID_REPORT');
    if(request.mode==='sharedCheck')return {ok:true,syncId:request.syncId,capability:'audience-v2'};
    if(!['sharedSync','sharedRevoke'].includes(request.mode))throw new Error('INVALID_REPORT');const r=request.report;key='AUDIENCE_'+subject_(r);const email=gmail_(r.email);
    // Validate data before touching existing readers.
    if(request.mode==='sharedSync'){if(r.audience==='owner')ownerTables_(r.data);else tenantTables_(r.data,'');}
    lock=LockService.getScriptLock();if(!lock.tryLock(20000)){lock=null;return {ok:false,code:'BUSY'};}
    record=JSON.parse(props.getProperty(key)||'{}');
    if(record.sheet)private_(record.sheet);if(record.pdf)private_(record.pdf);
    if(request.mode==='sharedRevoke'){record.email='';props.setProperty(key,JSON.stringify(record));return {ok:true,syncId:request.syncId,status:'revoked',subjectId:r.subjectId,audience:r.audience,syncedAt:new Date().toISOString()};}
    if(!record.sheet){record.sheet=SpreadsheetApp.create('暖色帳本－'+(r.audience==='owner'?'房東月報':'租約報表')).getId();props.setProperty(key,JSON.stringify(record));private_(record.sheet);}
    let contractUrl='';
    if(r.audience==='tenant'&&r.contract){if(typeof r.contract.base64!=='string'||r.contract.base64.length>34000000||!/^[-a-zA-Z0-9+/=\r\n]+$/.test(r.contract.base64))throw new Error('INVALID_REPORT');const bytes=Utilities.base64Decode(r.contract.base64);if(bytes.length>25000000||bytes.length<5||bytes.slice(0,5).map(x=>String.fromCharCode(x)).join('')!=='%PDF-')throw new Error('INVALID_REPORT');const blob=Utilities.newBlob(bytes,'application/pdf','租賃合約.pdf');if(record.pdf)Drive.Files.update({},record.pdf,blob);else{record.pdf=Drive.Files.create({name:'暖色帳本－租賃合約.pdf',mimeType:'application/pdf'},blob,{fields:'id'}).id;props.setProperty(key,JSON.stringify(record));}private_(record.pdf);contractUrl='https://drive.google.com/file/d/'+record.pdf+'/view';}
    else if(record.pdf){Drive.Files.update({trashed:true},record.pdf);record.pdf='';props.setProperty(key,JSON.stringify(record));}
    const tables=r.audience==='owner'?ownerTables_(r.data):tenantTables_(r.data,contractUrl),timestamp=new Date().toISOString();tables.push({name:'同步狀態',rows:[['項目','內容'],['更新時間',timestamp],['查看者',email],['說明','每次更新替換完整快照，僅供指定帳號查看。']],filter:false});
    const existing=Sheets.Spreadsheets.get(record.sheet,{fields:'sheets.properties'}).sheets||[],wanted=new Set(tables.map(t=>t.name));const requests=requests_(tables,existing);existing.filter(s=>!wanted.has(s.properties.title)).forEach(s=>requests.push({deleteSheet:{sheetId:s.properties.sheetId}}));Sheets.Spreadsheets.batchUpdate({requests},record.sheet);
    if(record.pdf)share_(record.pdf,email);share_(record.sheet,email);record.email=email;props.setProperty(key,JSON.stringify(record));
    return {ok:true,syncId:request.syncId,status:'shared',audience:r.audience,subjectId:r.subjectId,email,url:'https://docs.google.com/spreadsheets/d/'+record.sheet+'/edit',contractUrl,syncedAt:timestamp,month:r.data.month||''};
  }catch(e){if(record)for(const id of [record.sheet,record.pdf])if(id)try{private_(id);}catch(ignored){}return {ok:false,code:e.message==='INVALID_REPORT'?'INVALID_REPORT':'PERMISSION_FAILED'};}
  finally{if(lock)lock.releaseLock();}
}
