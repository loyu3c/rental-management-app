package com.loyu3c.rental;

import java.time.LocalDate;
import java.time.YearMonth;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskOverviewTest {
    private JSONObject lease(String id,String utilities) throws Exception {return new JSONObject().put("id",id).put("tenant","測試").put("unitCodeSnapshot",id).put("start","2026-01-01").put("end","2026-12-31").put("utilities",utilities).put("dueDay",5).put("water",200).put("electricRate",5);}
    private JSONObject backup(JSONObject lease) throws Exception {return new JSONObject().put("leases",new JSONArray().put(lease)).put("invoices",new JSONArray()).put("receipts",new JSONArray()).put("deposits",new JSONArray()).put("settlements",new JSONArray()).put("meterReadings",new JSONArray());}
    @Test public void selfPaidUtilitiesAndFutureOrTerminatedLeasesDoNotRequireMeters() throws Exception {
        JSONObject b=backup(lease("C1","租客自行繳水電"));String before=b.toString();JSONArray tasks=TaskOverview.build(b,LocalDate.of(2026,10,4));assertEquals(1,tasks.length());assertEquals("未完成開單",tasks.getJSONObject(0).getString("kind"));assertEquals(before,b.toString());
        b.getJSONArray("leases").getJSONObject(0).put("start","2026-11-01");assertEquals(0,TaskOverview.build(b,LocalDate.of(2026,10,4)).length());
        b.getJSONArray("leases").getJSONObject(0).put("start","2026-01-01").put("terminatedOn","2026-09-30");assertEquals(0,TaskOverview.build(b,LocalDate.of(2026,10,4)).length());
    }
    @Test public void existingUtilityBillOrEffectiveReadingSuppressMeterTask() throws Exception {
        JSONObject l=lease("R1401","管理者代收水電"),b=backup(l);LocalDate today=LocalDate.of(2026,10,4);
        assertEquals(2,TaskOverview.build(b,today).length());
        b.getJSONArray("meterReadings").put(new JSONObject().put("leaseId","R1401").put("month","2026-10").put("void",true));assertEquals(2,TaskOverview.build(b,today).length());
        b.getJSONArray("meterReadings").getJSONObject(0).put("void",false);assertEquals(1,TaskOverview.build(b,today).length());
        b.put("meterReadings",new JSONArray());b.getJSONArray("invoices").put(Ledger.invoice(l,YearMonth.of(2026,10),true,0,100,120));assertEquals(1,TaskOverview.build(b,today).length());
    }
    @Test public void dueDayIsNotOverdueAndPaidInvoicesDropOut() throws Exception {
        JSONObject l=lease("R1","租客自行繳水電"),b=backup(l);JSONArray invoices=b.getJSONArray("invoices"),receipts=b.getJSONArray("receipts");invoices.put(Ledger.invoice(l,YearMonth.of(2026,10),false,1000,0,0));
        assertEquals(0,TaskOverview.build(b,LocalDate.of(2026,10,5)).length());assertEquals("逾期待收",TaskOverview.build(b,LocalDate.of(2026,10,6)).getJSONObject(0).getString("kind"));
        receipts.put(Ledger.receipt("R1","2026-10-06","A",1000,"",invoices,receipts));assertEquals(0,TaskOverview.build(b,LocalDate.of(2026,10,6)).length());
    }
    @Test public void settlementCreatesOnlyOutstandingPaymentOrRefundTasks() throws Exception {
        JSONObject l=lease("L","租客自行繳水電").put("unitId","U").put("rent",1000),b=backup(l);
        b.getJSONArray("deposits").put(Deposits.movement("L","A","2026-01-01","收到押金",5000,""));
        JSONObject settled=Settlements.plan(b,"L","2026-10-03",1000,0,0,0,0,0,"");
        JSONArray tasks=TaskOverview.build(settled,LocalDate.of(2026,10,6));assertEquals(1,tasks.length());assertEquals("押金待退",tasks.getJSONObject(0).getString("kind"));
        b=backup(l);settled=Settlements.plan(b,"L","2026-10-03",1000,0,0,0,0,0,"");
        tasks=TaskOverview.build(settled,LocalDate.of(2026,10,6));assertEquals(1,tasks.length());assertEquals("結算待收",tasks.getJSONObject(0).getString("kind"));
    }

}
