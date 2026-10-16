package com.loyu3c.rental;

import java.time.LocalDate;
import java.time.YearMonth;
import org.json.JSONArray;
import org.json.JSONObject;

/** Computed work list, never writes to the ledger. */
public final class TaskOverview {
    private TaskOverview() {}
    private static JSONArray rows(JSONObject b,String key){JSONArray a=b.optJSONArray(key);return a==null?new JSONArray():a;}
    private static void add(JSONArray tasks,JSONObject lease,String kind,String detail) throws Exception {
        tasks.put(new JSONObject().put("leaseId",lease.getString("id")).put("kind",kind).put("title",lease.optString("unitCodeSnapshot")+" · "+lease.optString("tenant")).put("detail",detail));
    }
    public static JSONArray build(JSONObject backup,LocalDate today) throws Exception {
        JSONArray tasks=new JSONArray(),leases=rows(backup,"leases"),invoices=rows(backup,"invoices"),receipts=rows(backup,"receipts");YearMonth month=YearMonth.from(today);
        for(int i=0;i<leases.length();i++){
            JSONObject lease=leases.getJSONObject(i);String id=lease.getString("id");
            JSONObject settlement=Settlements.forLease(rows(backup,"settlements"),id);
            if(settlement!=null){
                long due=Settlements.due(settlement),held=Deposits.summary(id,rows(backup,"deposits")).getLong("held");
                if(due>0)add(tasks,lease,"結算待收","尚待補繳 $"+due);
                if(held>0)add(tasks,lease,"押金待退","尚待退款 $"+held);
                continue;
            }
            if(lease.has("terminatedOn"))continue;
            long overdue=0;
            for(int j=0;j<invoices.length();j++){JSONObject bill=invoices.getJSONObject(j);if(id.equals(bill.getString("leaseId"))&&LocalDate.parse(bill.getString("due")).isBefore(today))overdue+=Ledger.total(bill)-Ledger.paid(bill.getString("id"),receipts);}
            if(overdue>0)add(tasks,lease,"逾期待收","已到期帳單未付 $"+overdue+"（租金與水電）");
            LocalDate start=LocalDate.parse(lease.getString("start")),end=LocalDate.parse(lease.getString("end"));
            if(start.isAfter(today)||end.isBefore(month.atDay(1)))continue;
            boolean rent=Ledger.forMonth(invoices,id,RentTerms.rentMonth(lease,month),"租金")!=null,utility=Ledger.forMonth(invoices,id,month,"水電")!=null;
            boolean collect="管理者代收水電".equals(lease.optString("utilities"));
            if(RentTerms.scheduled(lease)&&!rent){JSONObject period=RentTerms.period(lease,month);add(tasks,lease,"本期租金待開單",period.getString("from")+" ～ "+period.getString("to")+" · 應繳 "+period.getString("due")+" · 建議租金 $"+period.getLong("amount"));}
            if((!rent&&!RentTerms.scheduled(lease))||(collect&&!utility))add(tasks,lease,"未完成開單",month+" 尚缺"+(!rent&&!RentTerms.scheduled(lease)?"租金":"")+(!rent&&!RentTerms.scheduled(lease)&&collect&&!utility?"、":"")+(collect&&!utility?"水電":"")+"帳單");
            if(collect&&!utility&&MeterReadings.latest(rows(backup,"meterReadings"),id,month.toString())==null)add(tasks,lease,"待抄電表",month+" 尚無有效抄表紀錄，可拍照留存依據");
        }
        return tasks;
    }
}

