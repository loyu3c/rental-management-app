package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.time.*;
import java.util.*;

public class SettlementsTest {
    private JSONObject backup(long deposit) throws Exception {
        JSONObject lease=new JSONObject().put("id","L").put("unitId","U").put("start","2026-01-01").put("end","2027-01-01").put("tenant","租客").put("unitCodeSnapshot","R1").put("rent",5000).put("water",200).put("electricRate",5).put("dueDay",5).put("utilities","管理者代收水電");
        JSONObject account=Bookkeeping.account("A","銀行","2026-01-01",0,"");account.put("id","A");
        JSONObject b=new JSONObject().put("leases",new JSONArray().put(lease)).put("accounts",new JSONArray().put(account)).put("properties",new JSONArray()).put("invoices",new JSONArray()).put("receipts",new JSONArray()).put("expenses",new JSONArray()).put("transfers",new JSONArray()).put("reconciliations",new JSONArray()).put("deposits",new JSONArray()).put("settlements",new JSONArray());
        if(deposit>0)b.getJSONArray("deposits").put(Deposits.movement("L","A","2026-01-01","收到押金",deposit,""));return b;
    }
    private void validate(JSONObject b) throws Exception {Ledger.validate(b.getJSONArray("invoices"),b.getJSONArray("receipts"),Collections.singleton("L"));Bookkeeping.validate(b);}
    private JSONObject plan(JSONObject b,long repair) throws Exception {JSONObject next=Settlements.plan(b,"L","2026-10-03",5000,200,100,300,repair,0,"");validate(next);return next;}
    private long bank(JSONObject b) throws Exception {return Bookkeeping.balance(b.getJSONArray("accounts").getJSONObject(0),"2026-10-31",b.getJSONArray("receipts"),b.getJSONArray("expenses"),b.getJSONArray("transfers"),b.getJSONArray("deposits"),b.getJSONArray("settlements"));}
    @Test public void depositOffsetsFeesWithoutBankDebitUntilActualRefund() throws Exception {
        JSONObject b=plan(backup(20000),3000),s=Settlements.forLease(b.getJSONArray("settlements"),"L");assertEquals(9200,s.getLong("total"));assertEquals(0,Settlements.due(s));assertEquals(10800,Deposits.summary("L",b.getJSONArray("deposits")).getLong("held"));assertEquals(20000,bank(b));
        b.getJSONArray("deposits").put(Deposits.movement("L","A","2026-10-04","退還押金",10800,""));validate(b);assertEquals(9200,bank(b));assertEquals(0,Deposits.summary("L",b.getJSONArray("deposits")).getLong("held"));
    }
    @Test public void costsAboveDepositAllowPartialTopUpsWithoutDuplicateCash() throws Exception {
        JSONObject b=plan(backup(2000),10000);assertEquals(14200,Settlements.due(Settlements.forLease(b.getJSONArray("settlements"),"L")));
        b=Settlements.payment(b,"L","2026-10-04","A",3000,"");validate(b);assertEquals(5000,bank(b));
        b=Settlements.payment(b,"L","2026-10-05","A",11200,"");validate(b);assertEquals(16200,bank(b));assertEquals(0,Settlements.due(Settlements.forLease(b.getJSONArray("settlements"),"L")));
        assertEquals(0,Bookkeeping.expenseTotal(b.getJSONArray("expenses"),"2026-10-01","2026-10-31",null));
    }
    @Test public void existingFinalInvoicesArePreservedAndNotBilledTwice() throws Exception {
        JSONObject b=backup(20000),lease=b.getJSONArray("leases").getJSONObject(0);JSONObject m=Ledger.monthlyPlan(lease,YearMonth.of(2026,10),4000,250,10,20,b.getJSONArray("invoices"),b.getJSONArray("receipts"));b.put("invoices",m.getJSONArray("added"));b=plan(b,0);assertEquals(2,b.getJSONArray("invoices").length());assertEquals(4300,Settlements.forLease(b.getJSONArray("settlements"),"L").getLong("total"));
    }
    @Test public void unpaidBillsAndHistoricalDepositUseOpeningWithoutDoubleCash() throws Exception {
        JSONObject b=backup(20000);b.getJSONArray("accounts").getJSONObject(0).put("start","2026-10-01").put("opening",20000);b=plan(b,0);assertEquals(20000,bank(b));
        assertEquals(6200,Ledger.paid(b.getJSONArray("invoices").getJSONObject(0).getString("id"),b.getJSONArray("receipts"))+Ledger.paid(b.getJSONArray("invoices").getJSONObject(1).getString("id"),b.getJSONArray("receipts")));
    }
    @Test public void finalManualRentAndSelfPaidUtilities() throws Exception {
        JSONObject b=backup(0);b.getJSONArray("leases").getJSONObject(0).put("utilities","租客自行繳水電");b=Settlements.plan(b,"L","2026-10-03",1200,0,0,0,0,0,"");validate(b);assertEquals(1,b.getJSONArray("invoices").length());assertEquals(1200,Settlements.due(Settlements.forLease(b.getJSONArray("settlements"),"L")));
    }
    @Test public void prepaidUnallocatedAndFutureBillsPreventUnsafeSettlement() throws Exception {
        JSONObject b=backup(0);b.getJSONArray("receipts").put(Ledger.receipt("L","2026-10-01","A",1000,"",b.getJSONArray("invoices"),b.getJSONArray("receipts")));try{plan(b,0);fail();}catch(IllegalArgumentException expected){}
        b=backup(0);b.getJSONArray("invoices").put(Ledger.invoice(b.getJSONArray("leases").getJSONObject(0),YearMonth.of(2026,11),false,5000,0,0));try{plan(b,0);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void duplicateSettlementExcessPaymentAndEarlierPaymentRejected() throws Exception {
        JSONObject b=plan(backup(0),0);try{plan(b,0);fail();}catch(IllegalArgumentException expected){}
        try{Settlements.payment(b,"L","2026-10-04","A",6201,"");fail();}catch(IllegalArgumentException expected){}
        try{Settlements.payment(b,"L","2026-10-02","A",1,"");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void removingOffsetOrChangingSettledInvoiceFailsValidation() throws Exception {
        JSONObject b=plan(backup(20000),0);b.getJSONArray("deposits").getJSONObject(1).put("void",true);try{validate(b);fail();}catch(IllegalArgumentException expected){}
        b=plan(backup(20000),0);b.getJSONArray("invoices").getJSONObject(0).put("rent",5001);try{validate(b);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void turnoverAllowsNextDayButNotOverlappingDayAndKeepsOriginalEnd() throws Exception {
        JSONObject b=plan(backup(0),0),old=b.getJSONArray("leases").getJSONObject(0),next=new JSONObject(old.toString());next.remove("terminatedOn");next.put("start","2026-10-04");assertFalse(Settlements.overlaps(old,next));assertEquals("2027-01-01",old.getString("end"));next.put("start","2026-10-03");assertTrue(Settlements.overlaps(old,next));
    }
    @Test public void backupRoundTripAndCashCutoffPreserveAmounts() throws Exception {
        JSONObject b=plan(backup(2000),10000);b=Settlements.payment(b,"L","2026-11-01","A",3000,"");b=new JSONObject(b.toString());validate(b);assertEquals(2000,bank(b));assertEquals(11200,Settlements.due(Settlements.forLease(b.getJSONArray("settlements"),"L")));
    }
}
