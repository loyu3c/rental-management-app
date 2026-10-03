package com.loyu3c.rental;

import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Deposits are held funds, separate from rent receipts and expense reports. */
public final class Deposits {
    public static JSONObject movement(String leaseId,String accountId,String date,String kind,long amount,String note) throws JSONException {
        LocalDate.parse(date);
        if(leaseId.isEmpty()||accountId.isEmpty()||!Arrays.asList("收到押金","退還押金").contains(kind)||amount<=0||amount>1000000000L)throw new IllegalArgumentException("請檢查租約、帳戶與押金金額");
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("leaseId",leaseId).put("accountId",accountId).put("date",date)
            .put("kind",kind).put("amount",amount).put("note",note.trim()).put("void",false);
    }
    public static JSONObject summary(String leaseId,JSONArray rows) throws JSONException {
        long received=0,refunded=0,applied=0;
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(r.optBoolean("void")||(leaseId!=null&&!leaseId.equals(r.getString("leaseId"))))continue;
            if(r.getString("kind").equals("收到押金"))received=Math.addExact(received,Ledger.money(r,"amount"));else if(r.getString("kind").equals("退還押金"))refunded=Math.addExact(refunded,Ledger.money(r,"amount"));else applied=Math.addExact(applied,Ledger.money(r,"amount"));}
        return new JSONObject().put("received",received).put("refunded",refunded).put("applied",applied).put("held",Math.subtractExact(Math.subtractExact(received,refunded),applied));
    }
    public static long cashflow(String accountId,String start,String end,JSONArray rows) throws JSONException {
        long sum=0;LocalDate from=LocalDate.parse(start),to=LocalDate.parse(end);
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);LocalDate date=LocalDate.parse(r.getString("date"));
            if(r.getString("kind").equals("押金抵付費用")||r.optBoolean("void")||!accountId.equals(r.getString("accountId"))||date.isBefore(from)||date.isAfter(to))continue;
            long amount=Ledger.money(r,"amount");sum=r.getString("kind").equals("收到押金")?Math.addExact(sum,amount):Math.subtractExact(sum,amount);}
        return sum;
    }
    public static void validate(JSONArray rows,Set<String> leases,Set<String> accounts) throws JSONException {
        Set<String> ids=new HashSet<>();Map<String,List<JSONObject>> groups=new HashMap<>();
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);
            if(r.getString("id").isEmpty()||!ids.add(r.getString("id"))||!leases.contains(r.getString("leaseId"))||(!r.getString("kind").equals("押金抵付費用")&&!accounts.contains(r.getString("accountId"))))throw new IllegalArgumentException("押金租約、帳戶或ID無效");
            r.getBoolean("void");if(r.getString("kind").equals("押金抵付費用")){LocalDate.parse(r.getString("date"));if(!r.getString("accountId").isEmpty()||r.getString("settlementId").isEmpty()||Ledger.money(r,"amount")<=0)throw new IllegalArgumentException("押金抵付無效");}else movement(r.getString("leaseId"),r.getString("accountId"),r.getString("date"),r.getString("kind"),Ledger.money(r,"amount"),r.getString("note"));
            if(!r.getBoolean("void"))groups.computeIfAbsent(r.getString("leaseId"),key->new ArrayList<>()).add(r);
        }
        for(List<JSONObject> group:groups.values()) {
            group.sort(Comparator.comparing((JSONObject r)->r.optString("date")).thenComparing(r->r.optString("kind").equals("收到押金")?0:1));long held=0;
            for(JSONObject r:group){held=r.getString("kind").equals("收到押金")?Math.addExact(held,Ledger.money(r,"amount")):Math.subtractExact(held,Ledger.money(r,"amount"));
                if(held<0)throw new IllegalArgumentException("退款超過該租約當時已收押金；請先補齊歷史收取紀錄");}
        }
    }
}
