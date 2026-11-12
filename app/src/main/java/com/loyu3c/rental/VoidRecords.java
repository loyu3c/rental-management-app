package com.loyu3c.rental;
import org.json.*;
import java.time.Instant;
import java.util.*;

/** Removed erroneous active rows are archived atomically with a reason and original snapshot. */
public final class VoidRecords {
    public static JSONObject archive(JSONObject backup,String kind,String id,String reason)throws JSONException{
        if(!Arrays.asList("invoices","receipts","leases").contains(kind)||reason.trim().isEmpty())throw new IllegalArgumentException("請填作廢／撤銷原因");
        JSONObject next=new JSONObject(backup.toString()),row=Owners.find(next.getJSONArray(kind),id);if(row==null)throw new IllegalArgumentException("紀錄已變更，請重新查看");String lid=kind.equals("leases")?id:row.getString("leaseId");
        if(Settlements.forLease(next.getJSONArray("settlements"),lid)!=null)throw new IllegalArgumentException("已結算紀錄不可撤銷");
        if(kind.equals("invoices")&&Ledger.paid(id,next.getJSONArray("receipts"))>0)throw new IllegalArgumentException("帳單已有抵付，請先核對收款");
        if(kind.equals("receipts")&&RentCredits.refunds(row).length()>0)throw new IllegalArgumentException("收款已有退款歷史，不能撤銷原收款");
        if(kind.equals("leases")){
            for(String key:new String[]{"invoices","receipts","deposits","meterReadings"}){JSONArray rows=Owners.rows(next,key);for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("leaseId")))throw new IllegalArgumentException("租約仍有帳單、收款、押金或抄表，請先處理關聯紀錄");}
            JSONArray shares=Owners.rows(next,"reportShares");for(int i=0;i<shares.length();i++){JSONObject share=shares.getJSONObject(i);if(share.optString("audience").equals("tenant")&&share.optString("subjectId").equals(id)&&!share.optString("status").equals("revoked"))throw new IllegalArgumentException("租約有分享紀錄，請先核對並處理分享");}
        }
        JSONArray archivedShares=new JSONArray();if(kind.equals("leases")){JSONArray shares=Owners.rows(next,"reportShares"),remaining=new JSONArray();for(int i=0;i<shares.length();i++){JSONObject share=shares.getJSONObject(i);if(share.optString("audience").equals("tenant")&&share.optString("subjectId").equals(id))archivedShares.put(share);else remaining.put(share);}next.put("reportShares",remaining);}
        JSONArray references=new JSONArray();if(kind.equals("receipts")){JSONArray bills=next.getJSONArray("invoices");for(int i=0;i<bills.length();i++)if(lid.equals(bills.getJSONObject(i).getString("leaseId")))references.put(new JSONObject(bills.getJSONObject(i).toString()));}
        JSONArray rows=next.getJSONArray(kind),active=new JSONArray();for(int i=0;i<rows.length();i++)if(!id.equals(rows.getJSONObject(i).getString("id")))active.put(rows.getJSONObject(i));next.put(kind,active);
        JSONArray history=Owners.rows(next,"voidRecords");history.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("kind",kind).put("leaseId",lid).put("voidedAt",Instant.now().toString()).put("reason",reason.trim()).put("previous",row).put("invoices",references).put("shares",archivedShares));next.put("voidRecords",history);return next;
    }
    public static JSONArray allLeases(JSONObject backup)throws JSONException{
        JSONArray result=new JSONArray();JSONArray active=Owners.rows(backup,"leases");for(int i=0;i<active.length();i++)result.put(active.getJSONObject(i));JSONArray history=Owners.rows(backup,"voidRecords");for(int i=0;i<history.length();i++){JSONObject entry=history.getJSONObject(i);if(entry.getString("kind").equals("leases"))result.put(entry.getJSONObject("previous"));}return result;
    }
    public static void validate(JSONObject backup)throws JSONException{
        Set<String> ids=new HashSet<>(),sources=new HashSet<>();JSONArray rows=Owners.rows(backup,"voidRecords");for(int i=0;i<rows.length();i++){JSONObject entry=rows.getJSONObject(i),old=entry.getJSONObject("previous");String kind=entry.getString("kind");if(!Arrays.asList("invoices","receipts","leases").contains(kind)||entry.getString("reason").trim().isEmpty()||entry.getString("id").isEmpty()||!ids.add(entry.getString("id"))||old.getString("id").isEmpty()||!sources.add(kind+":"+old.getString("id"))||Owners.find(Owners.rows(backup,kind),old.getString("id"))!=null)throw new IllegalArgumentException("作廢／撤銷歷史無效");Instant.parse(entry.getString("voidedAt"));if(!entry.getString("leaseId").equals(kind.equals("leases")?old.getString("id"):old.getString("leaseId")))throw new IllegalArgumentException("撤銷歷史關聯無效");}
    }
}
