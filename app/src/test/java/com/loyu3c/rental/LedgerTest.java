package com.loyu3c.rental;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.time.YearMonth;
import java.util.*;

public class LedgerTest {
    @Test public void monthlyPlanCombinesAmountsWithoutChangingExistingInvoices() throws Exception {
        JSONObject l=lease("L");JSONArray invoices=new JSONArray(),receipts=new JSONArray();
        JSONObject plan=Ledger.monthlyPlan(l,YearMonth.of(2026,2),10000,250,100,160,invoices,receipts);
        assertEquals(10550,plan.getLong("total"));assertEquals(2,plan.getJSONArray("added").length());assertEquals(0,invoices.length());
        invoices=new JSONArray(plan.getJSONArray("added").toString());
        String rentalId=plan.getJSONObject("rent").getString("id"),utilityId=plan.getJSONObject("utility").getString("id");
        receipts.put(Ledger.receipt("L","2026-02-05","A",10300,"",invoices,receipts));
        JSONObject again=Ledger.monthlyPlan(l,YearMonth.of(2026,2),99999,99999,999,1,invoices,receipts);
        assertEquals(0,again.getJSONArray("added").length());assertEquals(10550,again.getLong("total"));assertEquals(250,again.getLong("remaining"));
        assertEquals(rentalId,again.getJSONObject("rent").getString("id"));assertEquals(utilityId,again.getJSONObject("utility").getString("id"));
    }
    @Test public void annualRentPaidMonthlyPlanAddsOnlyUtilities() throws Exception {
        JSONObject l=lease("L");JSONArray invoices=new JSONArray(),receipts=new JSONArray();
        for(int m=1;m<=12;m++)invoices.put(Ledger.invoice(l,YearMonth.of(2026,m),false,10000,0,0));
        receipts.put(Ledger.receipt("L","2026-01-05","A",120000,"",invoices,receipts));
        JSONObject plan=Ledger.monthlyPlan(l,YearMonth.of(2026,2),10000,250,100,160,invoices,receipts);
        assertEquals(1,plan.getJSONArray("added").length());assertEquals("水電",plan.getJSONArray("added").getJSONObject(0).getString("kind"));
        assertEquals(10000,plan.getLong("paid"));assertEquals(550,plan.getLong("remaining"));assertEquals(1,receipts.length());
    }
    @Test public void selfPaidUtilitiesAndUnknownWaterMonthlyRules() throws Exception {
        JSONObject l=lease("L");l.put("utilities","租客自行繳水電");
        JSONObject plan=Ledger.monthlyPlan(l,YearMonth.of(2026,1),10000,-1,-1,-1,new JSONArray(),new JSONArray());
        assertEquals(1,plan.getJSONArray("added").length());assertTrue(plan.isNull("utility"));assertEquals(10000,plan.getLong("total"));
        l.put("utilities","管理者代收水電");
        try{Ledger.monthlyPlan(l,YearMonth.of(2026,1),10000,-1,100,160,new JSONArray(),new JSONArray());fail();}catch(IllegalArgumentException expected){}
    }
    private JSONObject lease(String id) throws Exception {
        return new JSONObject().put("id",id).put("start","2026-01-01").put("end","2026-12-31")
            .put("tenant","測試").put("unitCodeSnapshot","R1401").put("dueDay",31)
            .put("utilities","管理者代收水電").put("water",250).put("electricRate",5);
    }
    @Test public void combinedPaymentAndPartialDebt() throws Exception {
        JSONObject l=lease("L");JSONArray invoices=new JSONArray(),receipts=new JSONArray();
        JSONObject rent=Ledger.invoice(l,YearMonth.of(2026,2),false,10000,0,0);
        JSONObject utility=Ledger.invoice(l,YearMonth.of(2026,2),true,0,100,160);
        Ledger.addUnique(invoices,rent);Ledger.addUnique(invoices,utility);
        assertEquals("2026-02-28",rent.getString("due"));assertEquals(550,Ledger.total(utility));
        receipts.put(Ledger.receipt("L","2026-02-05","A帳戶",10300,"",invoices,receipts));
        assertEquals(10000,Ledger.paid(rent.getString("id"),receipts));assertEquals(300,Ledger.paid(utility.getString("id"),receipts));
        assertEquals(250,Ledger.total(utility)-Ledger.paid(utility.getString("id"),receipts));
        Ledger.validate(invoices,receipts,Set.of("L"));
    }
    @Test public void annualPaymentRemainsOneCashReceipt() throws Exception {
        JSONArray invoices=new JSONArray(),receipts=new JSONArray();JSONObject l=lease("L");
        for(int m=1;m<=12;m++)Ledger.addUnique(invoices,Ledger.invoice(l,YearMonth.of(2026,m),false,10000,0,0));
        JSONObject r=Ledger.receipt("L","2026-01-05","A帳戶",120000,"年繳",invoices,receipts);receipts.put(r);
        assertEquals(1,receipts.length());assertEquals(12,r.getJSONArray("allocations").length());assertEquals(120000,Ledger.allocated(r));
        Ledger.validate(invoices,receipts,Set.of("L"));
    }
    @Test public void turnoverKeepsOldAndNewTenantPaymentsSeparate() throws Exception {
        JSONArray invoices=new JSONArray(),receipts=new JSONArray();JSONObject old=Ledger.invoice(lease("OLD"),YearMonth.of(2026,1),false,10000,0,0),next=Ledger.invoice(lease("NEW"),YearMonth.of(2026,2),false,10000,0,0);
        invoices.put(old).put(next);receipts.put(Ledger.receipt("NEW","2026-02-05","A帳戶",15000,"",invoices,receipts));
        assertEquals(0,Ledger.paid(old.getString("id"),receipts));assertEquals(10000,Ledger.paid(next.getString("id"),receipts));
        assertEquals(5000,receipts.getJSONObject(0).getLong("amount")-Ledger.allocated(receipts.getJSONObject(0)));
    }
    @Test public void duplicateMonthlyInvoiceRejected() throws Exception {
        JSONArray rows=new JSONArray();JSONObject l=lease("L");Ledger.addUnique(rows,Ledger.invoice(l,YearMonth.of(2026,1),false,10000,0,0));
        try { Ledger.addUnique(rows,Ledger.invoice(l,YearMonth.of(2026,1),false,10000,0,0));fail(); }catch(IllegalArgumentException expected){}
        assertEquals(1,rows.length());
    }
    @Test public void unknownWaterAndBackwardMeterRejected() throws Exception {
        JSONObject l=lease("L");l.put("water",JSONObject.NULL);
        try {Ledger.invoice(l,YearMonth.of(2026,1),true,0,100,200);fail();}catch(IllegalArgumentException expected){}
        l.put("water",250);
        try {Ledger.invoice(l,YearMonth.of(2026,1),true,0,200,100);fail();}catch(IllegalArgumentException expected){}
        l.put("utilities","租客自行繳水電");
        try {Ledger.invoice(l,YearMonth.of(2026,1),true,0,100,200);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void importedOverAllocationAndCrossTenantAllocationRejected() throws Exception {
        JSONArray rows=new JSONArray().put(Ledger.invoice(lease("L"),YearMonth.of(2026,1),false,10000,0,0));
        JSONObject r=Ledger.receipt("L","2026-01-05","A",10000,"",rows,new JSONArray());
        r.getJSONArray("allocations").getJSONObject(0).put("amount",10001);
        try {Ledger.validate(rows,new JSONArray().put(r),Set.of("L"));fail();}catch(IllegalArgumentException expected){}
        r.getJSONArray("allocations").getJSONObject(0).put("amount",10000);r.put("leaseId","OTHER");
        try {Ledger.validate(rows,new JSONArray().put(r),Set.of("L","OTHER"));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void fractionalMoneyAndOutsideLeaseRejected() throws Exception {
        JSONObject row=new JSONObject().put("amount",1.5);
        try {Ledger.money(row,"amount");fail();}catch(IllegalArgumentException expected){}
        try {Ledger.invoice(lease("L"),YearMonth.of(2027,1),false,10000,0,0);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void excessCanBeAllocatedLaterWithoutNewCash() throws Exception {
        JSONArray invoices=new JSONArray(),receipts=new JSONArray();
        JSONObject original=Ledger.receipt("L","2026-01-05","A",20000,"",invoices,receipts);receipts.put(original);
        invoices.put(Ledger.invoice(lease("L"),YearMonth.of(2026,1),false,10000,0,0));
        JSONObject allocation=Ledger.receipt("L",original.getString("date"),original.getString("account"),20000,"",invoices,receipts);
        original.put("allocations",allocation.getJSONArray("allocations"));
        assertEquals(1,receipts.length());assertEquals(20000,original.getLong("amount"));assertEquals(10000,Ledger.allocated(original));
        Ledger.validate(invoices,receipts,Set.of("L"));
    }
}
