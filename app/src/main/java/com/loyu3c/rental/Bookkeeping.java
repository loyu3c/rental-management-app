package com.loyu3c.rental;

import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** One cash movement per payment. Property splits are reporting dimensions only. */
public final class Bookkeeping {
    public static final String[] CATEGORIES={"修繕","電梯保養","房貸扣款","其他費用"};
    public static boolean sameReceiptAccount(JSONObject a,JSONObject b) {
        String left=a.optString("accountId"),right=b.optString("accountId");
        return !left.isEmpty()&&!right.isEmpty()?left.equals(right):a.optString("account").equals(b.optString("account"));
    }
    public static JSONObject account(String name,String type,String start,long opening,String propertyId) throws JSONException {
        LocalDate.parse(start);if(name.trim().isEmpty()||!Arrays.asList("銀行","現金").contains(type)||opening<0||opening>1000000000L)throw new IllegalArgumentException("請檢查帳戶名稱、類型、起算日與期初餘額");
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("name",name.trim()).put("type",type).put("start",start).put("opening",opening).put("propertyId",propertyId);
    }
    public static JSONObject expense(String date,String accountId,String category,String description,long amount,JSONArray splits) throws JSONException {
        LocalDate.parse(date);if(accountId.isEmpty()||description.trim().isEmpty()||!Arrays.asList(CATEGORIES).contains(category)||amount<=0||amount>1000000000L)throw new IllegalArgumentException("請填付款帳戶、項目與正確金額");
        long sum=0;Set<String> unique=new HashSet<>();
        for(int i=0;i<splits.length();i++) {JSONObject r=splits.getJSONObject(i);long n=Ledger.money(r,"amount");
            if(n<=0||r.getString("propertyId").isEmpty()||!unique.add(r.getString("propertyId")))throw new IllegalArgumentException("分攤必須是正金額且不動產不能重複");sum=Math.addExact(sum,n);}
        if(sum!=amount)throw new IllegalArgumentException("分攤合計 $"+sum+"，必須等於支出 $"+amount);
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("date",date).put("accountId",accountId).put("category",category)
            .put("description",description.trim()).put("amount",amount).put("splits",new JSONArray(splits.toString())).put("void",false);
    }
    public static JSONObject transfer(String date,String from,String to,long amount,String note) throws JSONException {
        LocalDate.parse(date);if(from.isEmpty()||to.isEmpty()||from.equals(to)||amount<=0||amount>1000000000L)throw new IllegalArgumentException("轉出與轉入帳戶需不同，金額需大於0");
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("date",date).put("fromId",from).put("toId",to).put("amount",amount).put("description",note.trim()).put("void",false);
    }
    private static boolean inRange(JSONObject row,String start,String end) throws JSONException {
        LocalDate date=LocalDate.parse(row.getString("date"));return !date.isBefore(LocalDate.parse(start))&&!date.isAfter(LocalDate.parse(end));
    }
    public static long balance(JSONObject account,String date,JSONArray receipts,JSONArray expenses,JSONArray transfers) throws JSONException {
        LocalDate.parse(date);String start=account.getString("start"),id=account.getString("id");
        if(LocalDate.parse(date).isBefore(LocalDate.parse(start)))throw new IllegalArgumentException("對帳日早於帳戶起算日，無法計算餘額");
        long n=Ledger.money(account,"opening");
        for(int i=0;i<receipts.length();i++){JSONObject r=receipts.getJSONObject(i);if(id.equals(r.optString("accountId"))&&inRange(r,start,date))n=Math.addExact(n,Ledger.money(r,"amount"));}
        for(int i=0;i<expenses.length();i++){JSONObject r=expenses.getJSONObject(i);if(!r.optBoolean("void")&&id.equals(r.getString("accountId"))&&inRange(r,start,date))n=Math.subtractExact(n,Ledger.money(r,"amount"));}
        for(int i=0;i<transfers.length();i++){JSONObject r=transfers.getJSONObject(i);if(r.optBoolean("void")||!inRange(r,start,date))continue;long v=Ledger.money(r,"amount");
            if(id.equals(r.getString("fromId")))n=Math.subtractExact(n,v);if(id.equals(r.getString("toId")))n=Math.addExact(n,v);}
        return n;
    }
    public static long expenseTotal(JSONArray expenses,String start,String end,String propertyId) throws JSONException {
        long n=0;for(int i=0;i<expenses.length();i++){JSONObject r=expenses.getJSONObject(i);if(r.optBoolean("void")||!inRange(r,start,end))continue;
            if(propertyId==null)n=Math.addExact(n,Ledger.money(r,"amount"));else{JSONArray splits=r.getJSONArray("splits");for(int j=0;j<splits.length();j++)if(propertyId.equals(splits.getJSONObject(j).getString("propertyId")))n=Math.addExact(n,Ledger.money(splits.getJSONObject(j),"amount"));}}
        return n;
    }
    public static void validate(JSONObject backup) throws JSONException {
        JSONArray accounts=backup.getJSONArray("accounts"),expenses=backup.getJSONArray("expenses"),transfers=backup.getJSONArray("transfers"),receipts=backup.getJSONArray("receipts"),reconciliations=backup.getJSONArray("reconciliations");
        Set<String> accountIds=new HashSet<>(),names=new HashSet<>(),propertyIds=new HashSet<>(),ids=new HashSet<>();int cash=0;
        JSONArray properties=backup.getJSONArray("properties");for(int i=0;i<properties.length();i++)propertyIds.add(properties.getJSONObject(i).getString("id"));
        for(int i=0;i<accounts.length();i++){JSONObject r=accounts.getJSONObject(i);String type=r.getString("type");
            if(r.getString("id").isEmpty()||!accountIds.add(r.getString("id"))||!names.add(r.getString("name").trim().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("帳戶重複");
            account(r.getString("name"),type,r.getString("start"),Ledger.money(r,"opening"),r.optString("propertyId"));
            if(type.equals("現金"))cash++;if(!r.optString("propertyId").isEmpty()&&!propertyIds.contains(r.getString("propertyId")))throw new IllegalArgumentException("帳戶的不動產不存在");}
        if(cash>1)throw new IllegalArgumentException("共用現金帳戶只能有一個");
        for(int i=0;i<receipts.length();i++){JSONObject r=receipts.getJSONObject(i);if(!r.optString("accountId").isEmpty()&&!accountIds.contains(r.getString("accountId")))throw new IllegalArgumentException("收款帳戶不存在");}
        for(int i=0;i<expenses.length();i++){JSONObject r=expenses.getJSONObject(i);if(r.getString("id").isEmpty()||!ids.add(r.getString("id"))||!accountIds.contains(r.getString("accountId")))throw new IllegalArgumentException("支出帳戶或ID無效");
            r.getBoolean("void");expense(r.getString("date"),r.getString("accountId"),r.getString("category"),r.getString("description"),Ledger.money(r,"amount"),r.getJSONArray("splits"));
            JSONArray s=r.getJSONArray("splits");for(int j=0;j<s.length();j++)if(!propertyIds.contains(s.getJSONObject(j).getString("propertyId")))throw new IllegalArgumentException("分攤不動產不存在");}
        ids.clear();for(int i=0;i<transfers.length();i++){JSONObject r=transfers.getJSONObject(i);if(r.getString("id").isEmpty()||!ids.add(r.getString("id"))||!accountIds.contains(r.getString("fromId"))||!accountIds.contains(r.getString("toId")))throw new IllegalArgumentException("轉帳關聯無效");
            r.getBoolean("void");transfer(r.getString("date"),r.getString("fromId"),r.getString("toId"),Ledger.money(r,"amount"),r.getString("description"));}
        ids.clear();for(int i=0;i<reconciliations.length();i++){JSONObject r=reconciliations.getJSONObject(i);if(r.getString("id").isEmpty()||!ids.add(r.getString("id"))||!accountIds.contains(r.getString("accountId")))throw new IllegalArgumentException("對帳紀錄無效");
            LocalDate.parse(r.getString("date"));Ledger.money(r,"actual");Object raw=r.get("book");if(!(raw instanceof Integer||raw instanceof Long))throw new IllegalArgumentException("對帳快照格式錯誤");
            long book=((Number)raw).longValue();if(book < -1000000000000000L || book > 1000000000000000L)throw new IllegalArgumentException("對帳快照超出範圍");
            for(int j=0;j<accounts.length();j++)if(accounts.getJSONObject(j).getString("id").equals(r.getString("accountId"))&&LocalDate.parse(r.getString("date")).isBefore(LocalDate.parse(accounts.getJSONObject(j).getString("start"))))throw new IllegalArgumentException("對帳日早於帳戶起算日");}
    }
}
