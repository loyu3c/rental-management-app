package com.loyu3c.rental;

import org.json.*;
import java.time.*;
import java.util.*;

/** Settlement costs, noncash deposit offsets and cash top-ups remain distinct. */
public final class Settlements {
    public static LocalDate effectiveEnd(JSONObject lease) throws JSONException {return LocalDate.parse(lease.optString("terminatedOn",lease.getString("end")));}
    public static boolean overlaps(JSONObject a,JSONObject b) throws JSONException {return !effectiveEnd(a).isBefore(LocalDate.parse(b.getString("start")))&&!LocalDate.parse(a.getString("start")).isAfter(effectiveEnd(b));}
    public static JSONObject forLease(JSONArray rows,String id) throws JSONException {
        for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("leaseId").equals(id))return rows.getJSONObject(i);return null;
    }
    public static long due(JSONObject s) throws JSONException {
        long paid=0;JSONArray payments=s.getJSONArray("payments");for(int i=0;i<payments.length();i++)paid=Math.addExact(paid,Ledger.money(payments.getJSONObject(i),"amount"));
        return Math.subtractExact(Math.subtractExact(Ledger.money(s,"total"),Ledger.money(s,"depositUsed")),paid);
    }
    private static JSONArray leaseRows(JSONArray rows,String id) throws JSONException {
        JSONArray result=new JSONArray();for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).getString("leaseId")))result.put(new JSONObject(rows.getJSONObject(i).toString()));return result;
    }
    public static JSONObject plan(JSONObject backup,String leaseId,String date,long finalRent,long water,long prior,long current,long repairs,long other,String note) throws JSONException {
        JSONObject b=new JSONObject(backup.toString()),lease=null;JSONArray ls=b.getJSONArray("leases");
        for(int i=0;i<ls.length();i++)if(ls.getJSONObject(i).getString("id").equals(leaseId))lease=ls.getJSONObject(i);
        if(lease==null||forLease(b.getJSONArray("settlements"),leaseId)!=null)throw new IllegalArgumentException("租約不存在或已結算");
        LocalDate day=LocalDate.parse(date);if(day.isBefore(LocalDate.parse(lease.getString("start")))||day.isAfter(LocalDate.parse(lease.getString("end"))))throw new IllegalArgumentException("退租日必須在租期內");
        if(repairs<0||repairs>1000000000L||other<0||other>1000000000L)throw new IllegalArgumentException("費用超出範圍");
        JSONArray invoices=b.getJSONArray("invoices"),receipts=b.getJSONArray("receipts"),deposits=b.getJSONArray("deposits");YearMonth month=YearMonth.from(day);
        for(int i=0;i<invoices.length();i++){JSONObject r=invoices.getJSONObject(i);if(leaseId.equals(r.getString("leaseId"))&&YearMonth.parse(r.getString("month")).isAfter(month))throw new IllegalArgumentException("退租日後仍有帳單，請先核對並撤銷錯誤帳單");}
        for(int i=0;i<receipts.length();i++){JSONObject r=receipts.getJSONObject(i);if(!leaseId.equals(r.getString("leaseId")))continue;
            if(Ledger.money(r,"amount")!=Ledger.allocated(r))throw new IllegalArgumentException("尚有未分配預收款，請先處理分配；本版尚不支援預收租金退款");
            if(LocalDate.parse(r.getString("date")).isAfter(day))throw new IllegalArgumentException("退租日後已有收款，請核對日期");}
        for(int i=0;i<deposits.length();i++){JSONObject r=deposits.getJSONObject(i);if(leaseId.equals(r.getString("leaseId"))&&!r.optBoolean("void")&&LocalDate.parse(r.getString("date")).isAfter(day))throw new IllegalArgumentException("退租日後已有押金紀錄，請核對日期");}
        for(int i=0;i<invoices.length();i++){JSONObject bill=invoices.getJSONObject(i);if(leaseId.equals(bill.optString("leaseId"))&&bill.has("periodEnd")&&LocalDate.parse(bill.getString("periodEnd")).isAfter(day))throw new IllegalArgumentException("本期租金涵蓋退租日之後，請先核對帳單；已收預付租金退款本版尚不支援");}
        JSONObject billingLease=new JSONObject(lease.toString());if(RentTerms.scheduled(lease))billingLease.put("end",date);
        JSONObject monthly=Ledger.monthlyPlan(billingLease,month,finalRent,water,prior,current,invoices,receipts);JSONArray added=monthly.getJSONArray("added");for(int i=0;i<added.length();i++){JSONObject bill=added.getJSONObject(i);if(b.has("meterReadings")&&bill.getString("kind").equals("水電")){JSONObject meter=MeterReadings.latest(b.getJSONArray("meterReadings"),leaseId,month.toString());if(meter!=null&&meter.getLong("previous")==bill.getLong("previous")&&meter.getLong("current")==bill.getLong("current"))bill.put("meterReadingId",meter.getString("id"));}Ledger.addUnique(invoices,bill);}
        JSONArray lines=new JSONArray();long debt=0;
        for(int i=0;i<invoices.length();i++){JSONObject r=invoices.getJSONObject(i);if(!leaseId.equals(r.getString("leaseId")))continue;long paid=Ledger.paid(r.getString("id"),receipts),left=Ledger.total(r)-paid;
            debt=Math.addExact(debt,left);lines.put(new JSONObject().put("invoiceId",r.getString("id")).put("month",r.getString("month")).put("kind",r.getString("kind")).put("total",Ledger.total(r)).put("rent",r.getLong("rent")).put("water",r.getLong("water")).put("electricity",r.getLong("electricity")).put("paidBefore",paid).put("remaining",left));}
        long total=Math.addExact(debt,Math.addExact(repairs,other)),held=Deposits.summary(leaseId,deposits).getLong("held"),used=Math.min(held,total);
        if(total>1000000000L||held>1000000000L)throw new IllegalArgumentException("結算總額超出本版支援範圍");
        JSONObject s=new JSONObject().put("id",UUID.randomUUID().toString()).put("leaseId",leaseId).put("date",date).put("note",note.trim())
            .put("invoiceDebt",debt).put("repairs",repairs).put("other",other).put("total",total).put("depositBefore",held).put("depositUsed",used)
            .put("lines",lines).put("payments",new JSONArray()).put("baseReceipts",leaseRows(receipts,leaseId)).put("baseDeposits",leaseRows(deposits,leaseId))
            .put("tenantSnapshot",lease.getString("tenant")).put("unitCodeSnapshot",lease.getString("unitCodeSnapshot"));
        if(used>0)deposits.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("leaseId",leaseId).put("accountId","").put("date",date).put("kind","押金抵付費用").put("amount",used).put("note",note.trim()).put("void",false).put("settlementId",s.getString("id")));
        long invoicePaid=Math.min(used,debt);if(invoicePaid>0)receipts.put(Ledger.receipt(leaseId,date,"押金抵付（非匯款）",invoicePaid,"退租結算",invoices,receipts).put("cash",false).put("settlementId",s.getString("id")));
        b.getJSONArray("settlements").put(s);lease.put("terminatedOn",date);return b;
    }
    public static JSONObject payment(JSONObject backup,String leaseId,String date,String accountId,long amount,String note) throws JSONException {
        JSONObject b=new JSONObject(backup.toString()),s=forLease(b.getJSONArray("settlements"),leaseId);if(s==null)throw new IllegalArgumentException("請先建立退租結算");
        if(LocalDate.parse(date).isBefore(LocalDate.parse(s.getString("date")))||amount<=0||amount>due(s))throw new IllegalArgumentException("日期不得早於結算日，補繳金額不得超過尚欠費用");
        JSONObject account=null;JSONArray accounts=b.getJSONArray("accounts");for(int i=0;i<accounts.length();i++)if(accounts.getJSONObject(i).getString("id").equals(accountId))account=accounts.getJSONObject(i);
        if(account==null)throw new IllegalArgumentException("請選實際收款帳戶");
        JSONArray priorPayments=s.getJSONArray("payments");for(int i=0;i<priorPayments.length();i++){JSONObject old=priorPayments.getJSONObject(i);if(old.getString("date").equals(date)&&old.getString("accountId").equals(accountId)&&old.getLong("amount")==amount&&old.getString("note").equals(note.trim()))throw new IllegalArgumentException("已有相同補繳，請核對；另一筆請填不同交易辨識");}
        JSONObject p=new JSONObject().put("id",UUID.randomUUID().toString()).put("date",date).put("accountId",accountId).put("amount",amount).put("note",note.trim());
        JSONArray receipts=b.getJSONArray("receipts"),invoices=b.getJSONArray("invoices");long debt=0;for(int i=0;i<invoices.length();i++){JSONObject r=invoices.getJSONObject(i);if(leaseId.equals(r.getString("leaseId")))debt+=Ledger.total(r)-Ledger.paid(r.getString("id"),receipts);}
        long applied=Math.min(debt,amount);p.put("invoiceApplied",applied);if(applied>0)receipts.put(Ledger.receipt(leaseId,date,"結算補繳（流水另計）",applied,note,invoices,receipts).put("cash",false).put("settlementId",s.getString("id")).put("paymentId",p.getString("id")));
        s.getJSONArray("payments").put(p);return b;
    }
    public static long cashflow(String accountId,String start,String end,JSONArray rows) throws JSONException {
        long n=0;for(int i=0;i<rows.length();i++){JSONArray payments=rows.getJSONObject(i).getJSONArray("payments");for(int j=0;j<payments.length();j++){JSONObject p=payments.getJSONObject(j);LocalDate d=LocalDate.parse(p.getString("date"));if(accountId.equals(p.getString("accountId"))&&!d.isBefore(LocalDate.parse(start))&&!d.isAfter(LocalDate.parse(end)))n=Math.addExact(n,Ledger.money(p,"amount"));}}return n;
    }
    public static void validate(JSONObject b) throws JSONException {
        JSONArray rows=b.getJSONArray("settlements"),invoices=b.getJSONArray("invoices"),receipts=b.getJSONArray("receipts"),deposits=b.getJSONArray("deposits"),leases=b.getJSONArray("leases");Set<String> ids=new HashSet<>(),lids=new HashSet<>(),accounts=new HashSet<>(),paymentIds=new HashSet<>();
        JSONArray ac=b.getJSONArray("accounts");for(int i=0;i<ac.length();i++)accounts.add(ac.getJSONObject(i).getString("id"));
        for(int i=0;i<rows.length();i++){JSONObject s=rows.getJSONObject(i);String id=s.getString("id"),lid=s.getString("leaseId");if(id.isEmpty()||!ids.add(id)||!lids.add(lid))throw new IllegalArgumentException("結算重複");
            JSONObject lease=null;for(int j=0;j<leases.length();j++)if(lid.equals(leases.getJSONObject(j).getString("id")))lease=leases.getJSONObject(j);
            if(lease==null||!s.getString("date").equals(lease.optString("terminatedOn")))throw new IllegalArgumentException("退租紀錄與租約不一致");
            LocalDate d=LocalDate.parse(s.getString("date"));if(d.isBefore(LocalDate.parse(lease.getString("start")))||d.isAfter(LocalDate.parse(lease.getString("end"))))throw new IllegalArgumentException("退租日期錯誤");
            long debt=0;Set<String> billIds=new HashSet<>();JSONArray lines=s.getJSONArray("lines"),base=s.getJSONArray("baseReceipts");
            for(int j=0;j<lines.length();j++){JSONObject line=lines.getJSONObject(j),bill=null;String bid=line.getString("invoiceId");if(!billIds.add(bid))throw new IllegalArgumentException();for(int k=0;k<invoices.length();k++)if(bid.equals(invoices.getJSONObject(k).getString("id")))bill=invoices.getJSONObject(k);
                if(bill==null||!lid.equals(bill.getString("leaseId"))||!line.getString("month").equals(bill.getString("month"))||!line.getString("kind").equals(bill.getString("kind"))||line.getLong("total")!=Ledger.total(bill)||line.getLong("rent")!=bill.getLong("rent")||line.getLong("water")!=bill.getLong("water")||line.getLong("electricity")!=bill.getLong("electricity")||line.getLong("paidBefore")!=Ledger.paid(bid,base)||line.getLong("remaining")!=Ledger.total(bill)-Ledger.paid(bid,base))throw new IllegalArgumentException("已結算帳單被修改");debt=Math.addExact(debt,line.getLong("remaining"));}
            for(int j=0;j<invoices.length();j++){JSONObject bill=invoices.getJSONObject(j);if(lid.equals(bill.getString("leaseId"))&&!billIds.contains(bill.getString("id")))throw new IllegalArgumentException("已退租不能新增帳單");}
            if(debt!=s.getLong("invoiceDebt")||s.getLong("total")!=Math.addExact(debt,Math.addExact(Ledger.money(s,"repairs"),Ledger.money(s,"other")))||s.getLong("depositBefore")!=Deposits.summary(lid,s.getJSONArray("baseDeposits")).getLong("held")||s.getLong("depositUsed")!=Math.min(s.getLong("depositBefore"),s.getLong("total"))||due(s)<0)throw new IllegalArgumentException("結算金額不一致");
            Set<String> originalReceiptIds=new HashSet<>();for(int j=0;j<base.length();j++){JSONObject old=base.getJSONObject(j);originalReceiptIds.add(old.getString("id"));JSONObject now=null;for(int k=0;k<receipts.length();k++)if(old.getString("id").equals(receipts.getJSONObject(k).getString("id")))now=receipts.getJSONObject(k);if(now==null||now.getLong("amount")!=old.getLong("amount")||!now.getJSONArray("allocations").toString().equals(old.getJSONArray("allocations").toString())||!now.getString("date").equals(old.getString("date")))throw new IllegalArgumentException("已結算收款不可撤銷或重新分配");}
            long generated=0;Map<String,Long> receiptPayments=new HashMap<>();long offsetReceipt=0;
            for(int j=0;j<receipts.length();j++){JSONObject r=receipts.getJSONObject(j);if(!lid.equals(r.getString("leaseId"))||originalReceiptIds.contains(r.getString("id")))continue;
                if(!id.equals(r.optString("settlementId"))||r.optBoolean("cash",true)||Ledger.allocated(r)!=r.getLong("amount"))throw new IllegalArgumentException("結算收款關聯錯誤");generated+=r.getLong("amount");if(r.has("paymentId")){String pid=r.getString("paymentId");if(receiptPayments.put(pid,r.getLong("amount"))!=null)throw new IllegalArgumentException();}else{if(!r.getString("date").equals(s.getString("date")))throw new IllegalArgumentException();offsetReceipt+=r.getLong("amount");}}
            long expected=Math.min(s.getLong("depositUsed"),debt),paid=0;if(offsetReceipt!=expected)throw new IllegalArgumentException("押金抵付帳單不一致");JSONArray payments=s.getJSONArray("payments");for(int j=0;j<payments.length();j++){JSONObject p=payments.getJSONObject(j);String pid=p.getString("id");long n=Ledger.money(p,"amount"),applied=Ledger.money(p,"invoiceApplied");if(pid.isEmpty()||!paymentIds.add(pid)||!accounts.contains(p.getString("accountId"))||LocalDate.parse(p.getString("date")).isBefore(d)||n<=0||applied!=Math.min(n,Math.max(0,debt-expected))||receiptPayments.getOrDefault(pid,0L)!=applied)throw new IllegalArgumentException("結算補繳不一致");expected+=applied;paid+=n;receiptPayments.remove(pid);}
            if(generated!=expected||!receiptPayments.isEmpty()||paid>s.getLong("total")-s.getLong("depositUsed"))throw new IllegalArgumentException("結算分配不一致");
            JSONArray baseDeposits=s.getJSONArray("baseDeposits");Set<String> originalDeposits=new HashSet<>();for(int j=0;j<baseDeposits.length();j++){JSONObject old=baseDeposits.getJSONObject(j),now=null;originalDeposits.add(old.getString("id"));for(int k=0;k<deposits.length();k++)if(old.getString("id").equals(deposits.getJSONObject(k).getString("id")))now=deposits.getJSONObject(k);if(now==null||now.getLong("amount")!=old.getLong("amount")||now.getBoolean("void")!=old.getBoolean("void")||!now.getString("date").equals(old.getString("date"))||!now.getString("kind").equals(old.getString("kind")))throw new IllegalArgumentException("已結算押金來源不可撤銷");}
            long offsets=0;for(int j=0;j<deposits.length();j++){JSONObject r=deposits.getJSONObject(j);if(!lid.equals(r.getString("leaseId"))||originalDeposits.contains(r.getString("id")))continue;if(r.getString("kind").equals("押金抵付費用")){if(!id.equals(r.optString("settlementId"))||r.getBoolean("void")||!r.getString("date").equals(s.getString("date")))throw new IllegalArgumentException();offsets+=r.getLong("amount");}else if(!r.getString("kind").equals("退還押金")||LocalDate.parse(r.getString("date")).isBefore(d))throw new IllegalArgumentException("退租後只能退款，不能改動押金來源");}if(offsets!=s.getLong("depositUsed"))throw new IllegalArgumentException("押金抵付紀錄不一致");
        }
        for(int i=0;i<leases.length();i++)if(leases.getJSONObject(i).has("terminatedOn")&&!lids.contains(leases.getJSONObject(i).getString("id")))throw new IllegalArgumentException("缺少退租結算");
        for(int i=0;i<receipts.length();i++){JSONObject r=receipts.getJSONObject(i);if((r.has("settlementId")||!r.optBoolean("cash",true))&&!ids.contains(r.optString("settlementId")))throw new IllegalArgumentException("缺少結算收款來源");}
        for(int i=0;i<deposits.length();i++){JSONObject r=deposits.getJSONObject(i);if(r.getString("kind").equals("押金抵付費用")&&!ids.contains(r.optString("settlementId")))throw new IllegalArgumentException("缺少押金抵付來源");}
    }
}

