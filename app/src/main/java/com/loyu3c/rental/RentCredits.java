package com.loyu3c.rental;

import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Refunds are dated cash outflows attached to their original receipt, never expenses. */
public final class RentCredits {
    public static JSONArray refunds(JSONObject receipt){JSONArray rows=receipt.optJSONArray("refunds");return rows==null?new JSONArray():rows;}
    public static long refunded(JSONObject receipt)throws JSONException{long n=0;JSONArray rows=refunds(receipt);for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).optBoolean("void"))n=Math.addExact(n,Ledger.money(rows.getJSONObject(i),"amount"));return n;}
    public static long available(JSONObject receipt)throws JSONException{return Math.subtractExact(Math.subtractExact(Ledger.money(receipt,"amount"),Ledger.allocated(receipt)),refunded(receipt));}
    public static JSONObject refund(JSONObject backup,String receiptId,String date,String accountId,long amount,String reason)throws JSONException{
        JSONObject next=new JSONObject(backup.toString()),receipt=Owners.find(next.getJSONArray("receipts"),receiptId);
        if(receipt==null||!receipt.optBoolean("cash",true)||receipt.optBoolean("void"))throw new IllegalArgumentException("請選有效的實際收款");
        if(Settlements.forLease(next.getJSONArray("settlements"),receipt.getString("leaseId"))!=null)throw new IllegalArgumentException("已結算來源不能再變更");
        if(LocalDate.parse(date).isBefore(LocalDate.parse(receipt.getString("date")))||amount<=0||amount>available(receipt)||reason.trim().isEmpty())throw new IllegalArgumentException("退款日期不得早於收款日；請填原因及未分配餘額內的金額");
        JSONObject account=Owners.find(next.getJSONArray("accounts"),accountId),lease=Owners.find(next.getJSONArray("leases"),receipt.getString("leaseId"));
        if(account==null||lease==null)throw new IllegalArgumentException("退款帳戶或租約不存在");
        JSONObject unit=Owners.find(next.getJSONArray("units"),lease.getString("unitId"));if(unit==null)throw new IllegalArgumentException("出租單位不存在");Owners.paymentAccount(next,unit.getString("propertyId"),accountId);
        JSONArray rows=refunds(receipt);for(int i=0;i<rows.length();i++){JSONObject old=rows.getJSONObject(i);if(!old.optBoolean("void")&&old.getString("date").equals(date)&&old.getString("accountId").equals(accountId)&&old.getLong("amount")==amount&&old.getString("note").equals(reason.trim()))throw new IllegalArgumentException("已有相同退款，請核對避免重複");}
        rows.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("date",date).put("accountId",accountId).put("amount",amount).put("note",reason.trim()).put("void",false));receipt.put("refunds",rows);validate(next);return next;
    }
    public static JSONObject allocate(JSONObject backup,String receiptId,String invoiceId,long amount)throws JSONException{
        JSONObject next=new JSONObject(backup.toString()),receipt=Owners.find(next.getJSONArray("receipts"),receiptId),bill=Owners.find(next.getJSONArray("invoices"),invoiceId);
        if(receipt==null||bill==null||!receipt.getString("leaseId").equals(bill.getString("leaseId"))||amount<=0||amount>available(receipt)||amount>Ledger.total(bill)-Ledger.paid(invoiceId,next.getJSONArray("receipts")))throw new IllegalArgumentException("請選同租約的待繳帳單，金額不得超過餘款或欠款");
        if(Settlements.forLease(next.getJSONArray("settlements"),receipt.getString("leaseId"))!=null)throw new IllegalArgumentException("已結算收款不能再分配");
        JSONArray parts=receipt.getJSONArray("allocations"),history=receipt.optJSONArray("allocationHistory");if(history==null)history=new JSONArray();history.put(new JSONObject().put("changedAt",java.time.Instant.now().toString()).put("previous",new JSONArray(parts.toString())));receipt.put("allocationHistory",history);
        boolean found=false;for(int i=0;i<parts.length();i++){JSONObject part=parts.getJSONObject(i);if(invoiceId.equals(part.getString("invoiceId"))){part.put("amount",part.getLong("amount")+amount);found=true;break;}}if(!found)parts.put(new JSONObject().put("invoiceId",invoiceId).put("amount",amount));validate(next);return next;
    }
    public static JSONObject voidRefund(JSONObject backup,String receiptId,String refundId,String reason)throws JSONException{
        JSONObject next=new JSONObject(backup.toString()),receipt=Owners.find(next.getJSONArray("receipts"),receiptId);if(receipt==null||reason.trim().isEmpty())throw new IllegalArgumentException("請填撤銷原因");
        if(Settlements.forLease(next.getJSONArray("settlements"),receipt.getString("leaseId"))!=null)throw new IllegalArgumentException("已結算退款不可撤銷");
        JSONObject row=Owners.find(refunds(receipt),refundId);if(row==null||row.optBoolean("void"))throw new IllegalArgumentException("退款已變更");row.put("void",true).put("voidReason",reason.trim()).put("voidedAt",java.time.Instant.now().toString());validate(next);return next;
    }
    public static void validate(JSONObject backup)throws JSONException{
        Set<String> ids=new HashSet<>();JSONArray receipts=backup.getJSONArray("receipts"),accounts=backup.getJSONArray("accounts");
        for(int i=0;i<receipts.length();i++){JSONObject receipt=receipts.getJSONObject(i);if(available(receipt)<0)throw new IllegalArgumentException("收款分配與退款合計超過原收款");JSONArray rows=refunds(receipt);
            if(rows.length()>0&&(!receipt.optBoolean("cash",true)||receipt.optBoolean("void")))throw new IllegalArgumentException("退款來源必須是有效實際收款");
            for(int j=0;j<rows.length();j++){JSONObject row=rows.getJSONObject(j);String id=row.getString("id");if(id.isEmpty()||!ids.add(id)||Owners.find(accounts,row.getString("accountId"))==null||Ledger.money(row,"amount")<=0||row.getString("note").trim().isEmpty()||LocalDate.parse(row.getString("date")).isBefore(LocalDate.parse(receipt.getString("date"))))throw new IllegalArgumentException("預收租金退款資料無效");if(row.getBoolean("void")&&(row.getString("voidReason").trim().isEmpty()||row.getString("voidedAt").isEmpty()))throw new IllegalArgumentException("退款撤銷痕跡不完整");if(row.getBoolean("void"))java.time.Instant.parse(row.getString("voidedAt"));}
        }
    }
}
