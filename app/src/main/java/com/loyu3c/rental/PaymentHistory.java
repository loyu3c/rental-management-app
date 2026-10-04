package com.loyu3c.rental;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual payments only; deposit offsets and generated settlement receipts are excluded. */
public final class PaymentHistory {
    private PaymentHistory() {}
    private static JSONArray rows(JSONObject b,String key){JSONArray a=b.optJSONArray(key);return a==null?new JSONArray():a;}
    private static JSONObject find(JSONArray rows,String id) throws Exception {for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("id")))return rows.getJSONObject(i);return null;}
    private static JSONObject event(JSONObject b,JSONObject source,String leaseId,String kind,String fallbackTenant,String fallbackCode) throws Exception {
        JSONObject lease=find(rows(b,"leases"),leaseId),account=find(rows(b,"accounts"),source.optString("accountId"));
        return new JSONObject().put("id",kind+":"+source.optString("id")).put("leaseId",leaseId).put("kind",kind).put("date",source.getString("date")).put("amount",source.getLong("amount"))
            .put("tenant",lease==null?fallbackTenant:lease.optString("tenant")).put("code",lease==null?fallbackCode:lease.optString("unitCodeSnapshot"))
            .put("account",account==null?source.optString("account","尚未對應實際帳戶"):account.optString("name")).put("note",source.optString("note"));
    }
    public static JSONObject query(JSONObject backup,String month,String kind,String search) throws Exception {
        if(!month.isEmpty())YearMonth.parse(month);
        List<JSONObject> all=new ArrayList<>();JSONArray receipts=rows(backup,"receipts"),deposits=rows(backup,"deposits"),settlements=rows(backup,"settlements");
        for(int i=0;i<receipts.length();i++){JSONObject r=receipts.getJSONObject(i);if(r.optBoolean("cash",true)&&!r.optBoolean("void"))all.add(event(backup,r,r.getString("leaseId"),"租金水電","",""));}
        for(int i=0;i<deposits.length();i++){JSONObject r=deposits.getJSONObject(i);String k=r.getString("kind");if(!r.optBoolean("void")&&(k.equals("收到押金")||k.equals("退還押金")))all.add(event(backup,r,r.getString("leaseId"),k,r.optString("tenantSnapshot"),r.optString("unitCodeSnapshot")));}
        for(int i=0;i<settlements.length();i++){JSONObject s=settlements.getJSONObject(i);JSONArray ps=s.getJSONArray("payments");for(int j=0;j<ps.length();j++)all.add(event(backup,ps.getJSONObject(j),s.getString("leaseId"),"結算補繳",s.optString("tenantSnapshot"),s.optString("unitCodeSnapshot")));}
        all.sort(Comparator.comparing((JSONObject r)->r.optString("date")).reversed().thenComparing(r->r.optString("id")));
        String term=search.trim().toLowerCase(Locale.ROOT);JSONArray result=new JSONArray();long rent=0,received=0,refunded=0,topups=0;
        for(JSONObject r:all){String k=r.getString("kind"),haystack=r.getString("date")+" "+r.getString("tenant")+" "+r.getString("code")+" "+r.getString("account")+" "+r.getString("note")+" "+r.getLong("amount")+" "+DisplayText.number(r.getLong("amount"));
            if(!month.isEmpty()&&!r.getString("date").startsWith(month+"-")||!kind.equals("全部")&&!kind.equals(k)||!haystack.toLowerCase(Locale.ROOT).contains(term))continue;
            result.put(r);long amount=r.getLong("amount");switch(k){case "租金水電":rent+=amount;break;case "收到押金":received+=amount;break;case "退還押金":refunded+=amount;break;case "結算補繳":topups+=amount;break;}
        }
        return new JSONObject().put("rows",result).put("rent",rent).put("depositReceived",received).put("depositRefunded",refunded).put("topups",topups).put("net",rent+received+topups-refunded);
    }
}
