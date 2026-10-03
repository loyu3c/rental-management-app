package com.loyu3c.rental;

import org.json.*;
import java.time.*;
import java.util.*;

/** Integer TWD calculations. Receipts, rather than allocations, are cash inflows. */
public final class Ledger {
    public static JSONObject forMonth(JSONArray invoices, String leaseId, YearMonth month, String kind) throws JSONException {
        for(int i=0;i<invoices.length();i++) {
            JSONObject r=invoices.getJSONObject(i);
            if(leaseId.equals(r.getString("leaseId"))&&month.toString().equals(r.getString("month"))&&kind.equals(r.getString("kind")))return r;
        }
        return null;
    }
    /** A single operation completes missing parts while preserving existing invoice IDs and receipts. */
    public static JSONObject monthlyPlan(JSONObject lease, YearMonth month, long rent, long water, long previous, long current,
                                         JSONArray invoices, JSONArray receipts) throws JSONException {
        LocalDate start=LocalDate.parse(lease.getString("start")),end=LocalDate.parse(lease.getString("end"));
        if(month.atEndOfMonth().isBefore(start)||month.atDay(1).isAfter(end))throw new IllegalArgumentException("月份不在租期內");
        JSONArray added=new JSONArray();
        JSONObject rental=forMonth(invoices,lease.getString("id"),month,"租金"),utility=forMonth(invoices,lease.getString("id"),month,"水電");
        if(rental==null) {rental=invoice(lease,month,false,rent,0,0);added.put(rental);}
        if(utility==null&&lease.getString("utilities").equals("管理者代收水電")) {
            if(water<0)throw new IllegalArgumentException("請填固定月水費，不能將未知水費當作0");
            JSONObject settings=new JSONObject(lease.toString()).put("water",water);
            utility=invoice(settings,month,true,0,previous,current);added.put(utility);
        }
        long total=total(rental),paid=paid(rental.getString("id"),receipts);
        if(utility!=null){total=Math.addExact(total,total(utility));paid=Math.addExact(paid,paid(utility.getString("id"),receipts));}
        return new JSONObject().put("added",added).put("rent",rental).put("utility",utility==null?JSONObject.NULL:utility)
            .put("total",total).put("paid",paid).put("remaining",Math.subtractExact(total,paid));
    }
    public static long money(JSONObject row, String key) throws JSONException {
        Object raw = row.get(key);
        if (!(raw instanceof Integer || raw instanceof Long)) throw new IllegalArgumentException("金額必須是整數");
        long n = ((Number) raw).longValue();
        if (n < 0 || n > 1000000000L) throw new IllegalArgumentException("金額超出範圍");
        return n;
    }
    public static long total(JSONObject invoice) throws JSONException {
        return Math.addExact(Math.addExact(money(invoice,"rent"), money(invoice,"water")), money(invoice,"electricity"));
    }
    public static long paid(String id, JSONArray receipts) throws JSONException {
        long sum = 0;
        for (int i=0;i<receipts.length();i++) {
            JSONArray a=receipts.getJSONObject(i).getJSONArray("allocations");
            for(int j=0;j<a.length();j++) if(id.equals(a.getJSONObject(j).getString("invoiceId")))
                sum=Math.addExact(sum,money(a.getJSONObject(j),"amount"));
        }
        return sum;
    }
    public static long allocated(JSONObject receipt) throws JSONException {
        long n=0; JSONArray a=receipt.getJSONArray("allocations");
        for(int i=0;i<a.length();i++) n=Math.addExact(n,money(a.getJSONObject(i),"amount"));
        return n;
    }
    public static JSONObject invoice(JSONObject lease, YearMonth month, boolean utility,
                                     long rent, long previous, long current) throws JSONException {
        LocalDate start=LocalDate.parse(lease.getString("start")),end=LocalDate.parse(lease.getString("end"));
        if(month.atEndOfMonth().isBefore(start)||month.atDay(1).isAfter(end)) throw new IllegalArgumentException("月份不在租期內");
        long water=0,electric=0;
        if(utility) {
            if(!"管理者代收水電".equals(lease.getString("utilities"))) throw new IllegalArgumentException("此租約自行繳水電");
            if(lease.isNull("water")) throw new IllegalArgumentException("請先在租約設定月水費");
            water=money(lease,"water");
            if(previous<0 || current<previous || current>1000000000L) throw new IllegalArgumentException("電表讀數不合理；換表請另外處理");
            electric=Math.multiplyExact(current-previous,money(lease,"electricRate"));
            if(electric>1000000000L) throw new IllegalArgumentException("電費超出範圍");
        }
        if(rent<0||rent>1000000000L) throw new IllegalArgumentException("租金超出範圍");
        JSONObject row=new JSONObject();
        row.put("id",UUID.randomUUID().toString()).put("leaseId",lease.getString("id"));
        row.put("month",month.toString()).put("kind",utility?"水電":"租金");
        LocalDate due=month.atDay(Math.min(lease.getInt("dueDay"),month.lengthOfMonth()));
        if(due.isBefore(start)) due=start;
        row.put("due",due.toString()).put("rent",utility?0:rent).put("water",water).put("electricity",electric);
        row.put("tenantSnapshot",lease.getString("tenant")).put("unitCodeSnapshot",lease.getString("unitCodeSnapshot"));
        if(utility) row.put("previous",previous).put("current",current).put("rate",money(lease,"electricRate"));
        total(row); return row;
    }
    public static void addUnique(JSONArray invoices, JSONObject row) throws JSONException {
        for(int i=0;i<invoices.length();i++) {
            JSONObject old=invoices.getJSONObject(i);
            if(old.getString("leaseId").equals(row.getString("leaseId"))&&old.getString("month").equals(row.getString("month"))
                &&old.getString("kind").equals(row.getString("kind"))) throw new IllegalArgumentException(row.getString("month")+"的"+row.getString("kind")+"已建立");
        }
        invoices.put(row);
    }
    public static JSONObject receipt(String leaseId, String date, String account, long amount, String note,
                                     JSONArray invoices, JSONArray receipts) throws JSONException {
        LocalDate.parse(date);
        if(amount<=0||amount>1000000000L||account.trim().isEmpty()) throw new IllegalArgumentException("請檢查收款金額與帳戶");
        List<JSONObject> candidates=new ArrayList<>();
        for(int i=0;i<invoices.length();i++) if(leaseId.equals(invoices.getJSONObject(i).getString("leaseId"))) candidates.add(invoices.getJSONObject(i));
        candidates.sort(Comparator.comparing((JSONObject r)->r.optString("month")).thenComparing(r->r.optString("kind").equals("租金")?0:1));
        JSONArray allocations=new JSONArray(); long left=amount;
        for(JSONObject r:candidates) {
            long debt=total(r)-paid(r.getString("id"),receipts);
            if(debt<0) throw new IllegalArgumentException("帳單分配超額，請檢查資料");
            long n=Math.min(left,debt);
            if(n>0) allocations.put(new JSONObject().put("invoiceId",r.getString("id")).put("amount",n));
            left-=n;
        }
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("leaseId",leaseId).put("date",date)
            .put("account",account.trim()).put("amount",amount).put("note",note.trim()).put("allocations",allocations);
    }
    public static void validate(JSONArray invoices, JSONArray receipts, Set<String> leases) throws JSONException {
        Map<String,JSONObject> byId=new HashMap<>(); Set<String> unique=new HashSet<>(), receiptIds=new HashSet<>();
        for(int i=0;i<invoices.length();i++) {
            JSONObject r=invoices.getJSONObject(i);
            String id=r.getString("id"),lid=r.getString("leaseId"),kind=r.getString("kind");
            if(id.isEmpty()||byId.put(id,r)!=null||!leases.contains(lid)||!Arrays.asList("租金","水電").contains(kind)
                ||!unique.add(lid+"|"+r.getString("month")+"|"+kind)) throw new IllegalArgumentException("帳單資料重複或關聯錯誤");
            YearMonth.parse(r.getString("month")); LocalDate.parse(r.getString("due")); total(r);
            if(kind.equals("租金")&&(money(r,"water")!=0||money(r,"electricity")!=0)) throw new IllegalArgumentException();
            if(kind.equals("水電")) {
                if(money(r,"rent")!=0||money(r,"current")<money(r,"previous")
                    ||Math.multiplyExact(money(r,"current")-money(r,"previous"),money(r,"rate"))!=money(r,"electricity")) throw new IllegalArgumentException();
            }
        }
        for(int i=0;i<receipts.length();i++) {
            JSONObject r=receipts.getJSONObject(i); LocalDate.parse(r.getString("date"));
            if(r.getString("id").isEmpty()||!receiptIds.add(r.getString("id"))||!leases.contains(r.getString("leaseId"))
                ||r.getString("account").trim().isEmpty()||money(r,"amount")<=0||allocated(r)>money(r,"amount")) throw new IllegalArgumentException("收款資料無效");
            JSONArray a=r.getJSONArray("allocations");Set<String> seen=new HashSet<>();
            for(int j=0;j<a.length();j++) {
                JSONObject part=a.getJSONObject(j),invoice=byId.get(part.getString("invoiceId"));
                if(invoice==null||!seen.add(part.getString("invoiceId"))||!invoice.getString("leaseId").equals(r.getString("leaseId"))||money(part,"amount")<=0) throw new IllegalArgumentException("收款分配無效");
            }
        }
        for(JSONObject r:byId.values()) if(paid(r.getString("id"),receipts)>total(r)) throw new IllegalArgumentException("帳單超收");
    }
}
