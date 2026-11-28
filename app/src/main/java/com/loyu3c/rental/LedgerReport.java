package com.loyu3c.rental;
import org.json.*;
import java.util.*;
import java.io.*;

/** One selection for the list and exports. No mutation or pagination of source data. */
final class LedgerReport {
    static String name(JSONObject b,String collection,String id,String key){JSONArray rows=b.optJSONArray(collection);if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&id.equals(r.optString("id")))return r.optString(key);}return id;}
    static String category(JSONObject b,JSONObject r){return r.optBoolean("income")?r.optString("category"):ExpenseCategories.name(b.optJSONArray("expenseCategories"),r.optString("category"));}
    static String splits(JSONObject b,JSONObject r){StringBuilder text=new StringBuilder();JSONArray rows=r.optJSONArray("splits");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject s=rows.optJSONObject(i);if(i>0)text.append("；");text.append(name(b,"properties",s.optString("propertyId"),"name")).append(" $").append(String.format(Locale.US,"%,d",s.optLong("amount")));}return text.toString();}
    static List<JSONObject> select(JSONObject b,String month,int status,String query,boolean transfers)throws JSONException{
        List<JSONObject> result=new ArrayList<>();String q=query.trim().toLowerCase(Locale.ROOT);
        for(String key:transfers?new String[]{"transfers"}:new String[]{"expenses","incomes"}){JSONArray rows=b.optJSONArray(key);if(rows==null)continue;for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);boolean v=r.optBoolean("void");if(!month.isEmpty()&&!r.optString("date").startsWith(month)||status==1&&v||status==2&&!v)continue;
            String c=transfers?"轉帳":category(b,r),accounts=transfers?name(b,"accounts",r.optString("fromId"),"name")+" → "+name(b,"accounts",r.optString("toId"),"name"):name(b,"accounts",r.optString("accountId"),"name");
            String hay=r.optString("date")+" "+c+" "+r.optString("amount")+" "+r.optString("description")+" "+accounts+" "+splits(b,r)+(r.optBoolean("income")?" 收入":" 支出")+(v?" 已撤銷":"");
            if((hay+" "+DisplayText.money(hay)).toLowerCase(Locale.ROOT).contains(q))result.add(r);
        }}result.sort(Comparator.comparing((JSONObject r)->r.optString("date")).reversed());return result;
    }
    static List<JSONObject> select(JSONObject b,String month,int status,String query,boolean transfers,int kind)throws JSONException{
        List<JSONObject> rows=select(b,month,status,query,transfers);if(!transfers&&kind!=0)rows.removeIf(r->kind==1?!r.optBoolean("income"):r.optBoolean("income"));return rows;
    }
    static JSONObject build(JSONObject b,String month,int status,String query,boolean includeVoid)throws JSONException{
        return build(b,month,status,query,includeVoid,0);
    }
    static JSONObject build(JSONObject b,String month,int status,String query,boolean includeVoid,int kind)throws JSONException{
        JSONArray items=new JSONArray();long income=0,expense=0;
        for(JSONObject r:select(b,month,status,query,false,kind)){boolean v=r.optBoolean("void"),in=r.optBoolean("income");if(v&&!includeVoid)continue;
            if(!v){if(in)income=Math.addExact(income,r.getLong("amount"));else expense=Math.addExact(expense,r.getLong("amount"));}
            items.put(new JSONObject().put("record",new JSONObject(r.toString())).put("date",r.getString("date")).put("kind",in?"收入":"支出").put("category",category(b,r)).put("description",r.optString("description")).put("amount",r.getLong("amount")).put("account",name(b,"accounts",r.optString("accountId"),"name")).put("splits",splits(b,r)).put("void",v).put("voidReason",r.optString("voidReason")).put("voidedAt",r.optString("voidedAt",r.optString("voidDate"))));
        }
        return new JSONObject().put("items",items).put("income",income).put("expense",expense).put("conditions",new String[]{"全部收支","收入","支出"}[kind]+" · "+(month.isEmpty()?"全部月份":month)+" · "+new String[]{"全部紀錄","有效紀錄","已撤銷"}[status]+(query.trim().isEmpty()?"":" · 搜尋："+query.trim())+" · "+(includeVoid?"含撤銷":"不含撤銷")).put("createdAt",java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Taipei")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).put("properties",b.getJSONArray("properties"));
    }
    static void workbook(OutputStream out,JSONObject report)throws IOException,JSONException{
        List<Object[]> summary=new ArrayList<>(),rows=new ArrayList<>(),splits=new ArrayList<>();
        summary.add(new Object[]{"收支總覽","內容／金額"});summary.add(new Object[]{"篩選條件",report.getString("conditions")});summary.add(new Object[]{"匯出時間",report.getString("createdAt")});summary.add(new Object[]{"一般收入",report.getLong("income")});summary.add(new Object[]{"支出",report.getLong("expense")});summary.add(new Object[]{"收支差額",report.getLong("income")-report.getLong("expense")});summary.add(new Object[]{"計算口徑","僅一般收入與支出，已撤銷不計合計；不含租金收款、押金及轉帳。分攤表不可重複加總。"});
        rows.add(new Object[]{"日期","類型","項目／說明","金額","分類","帳戶","分攤摘要","狀態","撤銷原因","撤銷時間","紀錄ID","照片數"});
        splits.add(new Object[]{"紀錄ID","日期","項目","不動產","分攤金額","狀態"});
        JSONArray items=report.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject r=items.getJSONObject(i),source=r.getJSONObject("record");String state=r.getBoolean("void")?"已撤銷":"有效";rows.add(new Object[]{r.getString("date"),r.getString("kind"),r.getString("description"),r.getLong("amount"),r.getString("category"),r.getString("account"),r.getString("splits"),state,r.getString("voidReason"),r.getString("voidedAt"),source.getString("id"),ExpensePhotos.photos(source).length()});JSONArray allocations=source.optJSONArray("splits");if(allocations!=null)for(int j=0;j<allocations.length();j++){JSONObject a=allocations.getJSONObject(j);splits.add(new Object[]{source.getString("id"),r.getString("date"),r.getString("description"),name(report,"properties",a.getString("propertyId"),"name"),a.getLong("amount"),state});}}
        OwnerWorkbook.writeTables(out,summary,rows,splits,new String[]{"總覽","收支明細","分攤明細"},new double[][]{{28,95},{16,10,60,18,20,28,65,12,35,28,40,12},{40,16,60,28,18,12}});
    }
}
