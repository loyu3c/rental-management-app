package com.loyu3c.rental;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;import java.time.*;import java.util.*;

public class RentCreditsTest {
    private JSONObject backup()throws Exception{
        JSONObject lease=new JSONObject().put("id","L").put("unitId","U").put("start","2026-01-01").put("end","2027-01-01").put("tenant","Tenant").put("unitCodeSnapshot","1632").put("rent",1000).put("deposit",0).put("water",0).put("electricRate",5).put("dueDay",5).put("utilities","租客自行繳水電").put("cycle","月繳");
        JSONObject b=new JSONObject();for(String key:new String[]{"properties","units","leases","accounts","invoices","receipts","deposits","settlements","expenses","transfers","reconciliations","meterReadings","reportShares","voidRecords"})b.put(key,new JSONArray());
        b.getJSONArray("properties").put(new JSONObject().put("id","P").put("name","P").put("type","建築物"));b.getJSONArray("units").put(new JSONObject().put("id","U").put("code","1632").put("propertyId","P").put("type","房間"));b.getJSONArray("leases").put(lease);
        b.getJSONArray("accounts").put(Bookkeeping.account("Bank","銀行","2026-01-01",0,"").put("id","A"));
        b.getJSONArray("invoices").put(Ledger.invoice(lease,YearMonth.of(2026,9),false,1000,0,0).put("id","I"));b.getJSONArray("receipts").put(Ledger.receipt("L","2026-09-01","Bank",1200,"paid",b.getJSONArray("invoices"),new JSONArray()).put("id","R").put("accountId","A"));return b;
    }
    private JSONObject receipt(JSONObject b)throws Exception{return b.getJSONArray("receipts").getJSONObject(0);}
    private long balance(JSONObject b,String date)throws Exception{return Bookkeeping.balance(b.getJSONArray("accounts").getJSONObject(0),date,b.getJSONArray("receipts"),b.getJSONArray("expenses"),b.getJSONArray("transfers"),b.getJSONArray("deposits"),b.getJSONArray("settlements"));}
    private void valid(JSONObject b)throws Exception{Ledger.validate(b.getJSONArray("invoices"),b.getJSONArray("receipts"),Collections.singleton("L"));Bookkeeping.validate(b);}
    private interface Action{void run()throws Exception;}
    private void rejects(Action action)throws Exception{try{action.run();fail("Expected rejected operation");}catch(IllegalArgumentException expected){}}
    @Test public void refundChangesOnlyAvailableCreditAndActualPaymentDate()throws Exception{
        JSONObject original=backup();String before=original.toString();JSONObject b=RentCredits.refund(original,"R","2026-10-02","A",150,"unused rent");valid(b);assertEquals(before,original.toString());assertEquals(50,RentCredits.available(receipt(b)));assertEquals(1000,Ledger.paid("I",b.getJSONArray("receipts")));assertEquals(1200,balance(b,"2026-09-30"));assertEquals(1050,balance(b,"2026-10-31"));assertEquals(0,Bookkeeping.expenseTotal(b.getJSONArray("expenses"),"2026-01-01","2026-12-31",null));
    }
    @Test public void refundRejectsExcessOldDateMissingAccountAndDuplicate()throws Exception{
        JSONObject b=backup();rejects(()->RentCredits.refund(b,"R","2026-10-02","A",201,"x"));rejects(()->RentCredits.refund(b,"R","2026-08-31","A",100,"x"));rejects(()->RentCredits.refund(b,"R","2026-10-02","missing",100,"x"));rejects(()->RentCredits.refund(b,"R","2026-10-02","A",100," "));JSONObject next=RentCredits.refund(b,"R","2026-10-02","A",100,"x");rejects(()->RentCredits.refund(next,"R","2026-10-02","A",100,"x"));
    }
    @Test public void partialRefundAndAllocationConserveCash()throws Exception{
        JSONObject b=RentCredits.refund(backup(),"R","2026-10-02","A",100,"x");JSONObject bill=Ledger.invoice(b.getJSONArray("leases").getJSONObject(0),YearMonth.of(2026,10),false,1000,0,0).put("id","J");b.getJSONArray("invoices").put(bill);JSONObject next=RentCredits.allocate(b,"R","J",100);valid(next);assertEquals(0,RentCredits.available(receipt(next)));assertEquals(1100,balance(next,"2026-10-31"));assertEquals(100,Ledger.paid("J",next.getJSONArray("receipts")));assertEquals(1,receipt(next).getJSONArray("allocationHistory").length());assertEquals(100,RentCredits.available(receipt(b)));
    }
    @Test public void allocationRejectsCrossLeaseAndRefundedMoney()throws Exception{
        JSONObject b=RentCredits.refund(backup(),"R","2026-10-02","A",200,"x");b.getJSONArray("invoices").put(new JSONObject(b.getJSONArray("invoices").getJSONObject(0).toString()).put("id","J").put("leaseId","OTHER"));rejects(()->RentCredits.allocate(b,"R","J",1));rejects(()->RentCredits.allocate(b,"R","I",1));
    }
    @Test public void voidRefundRestoresCreditAndRetainsOriginal()throws Exception{
        JSONObject b=RentCredits.refund(backup(),"R","2026-10-02","A",150,"x");String id=receipt(b).getJSONArray("refunds").getJSONObject(0).getString("id");JSONObject next=RentCredits.voidRefund(b,"R",id,"wrong entry");valid(next);assertEquals(200,RentCredits.available(receipt(next)));assertEquals(1200,balance(next,"2026-10-31"));assertTrue(receipt(next).getJSONArray("refunds").getJSONObject(0).getBoolean("void"));assertFalse(receipt(b).getJSONArray("refunds").getJSONObject(0).getBoolean("void"));
    }
    @Test public void settlementAcceptsRefundedCreditAndLocksRefundHistory()throws Exception{
        JSONObject b=RentCredits.refund(backup(),"R","2026-10-02","A",200,"x");JSONObject next=Settlements.plan(b,"L","2026-10-12",1000,0,0,0,0,0,"");valid(next);String id=receipt(next).getJSONArray("refunds").getJSONObject(0).getString("id");rejects(()->RentCredits.voidRefund(next,"R",id,"x"));JSONObject corrupt=new JSONObject(next.toString());receipt(corrupt).getJSONArray("refunds").getJSONObject(0).put("note","altered");rejects(()->Settlements.validate(corrupt));
    }
    @Test public void reportsReflectRefundMonthAndTenantIsolation()throws Exception{
        JSONObject b=RentCredits.refund(backup(),"R","2026-10-02","A",150,"x");JSONObject history=PaymentHistory.query(b,"2026-10","全部","");assertEquals(150,history.getLong("rentRefunded"));assertEquals(-150,history.getLong("net"));JSONObject report=OwnerReport.build(b,YearMonth.of(2026,10));assertEquals(-150,report.getJSONObject("total").getLong("cash"));assertEquals(-150,report.getJSONObject("total").getLong("net"));assertEquals(2,SharedReports.tenant(b,"L").getJSONArray("payments").length());
    }
    @Test public void refundFromAnotherAccountDoesNotChangeReceiptAccount()throws Exception{
        JSONObject b=backup();b.getJSONArray("accounts").put(Bookkeeping.account("Other","銀行","2026-01-01",500,"").put("id","B"));JSONObject next=RentCredits.refund(b,"R","2026-10-02","B",150,"x");assertEquals(1200,balance(next,"2026-10-31"));assertEquals(350,Bookkeeping.balance(next.getJSONArray("accounts").getJSONObject(1),"2026-10-31",next.getJSONArray("receipts"),next.getJSONArray("expenses"),next.getJSONArray("transfers")));valid(next);
    }
    @Test public void archiveRejectsPaidInvoiceButRetainsErroneousReceipt()throws Exception{
        JSONObject b=backup();rejects(()->VoidRecords.archive(b,"invoices","I","wrong"));JSONObject next=VoidRecords.archive(b,"receipts","R","wrong entry");assertEquals(0,next.getJSONArray("receipts").length());assertEquals(0,balance(next,"2026-10-31"));assertEquals(1200,next.getJSONArray("voidRecords").getJSONObject(0).getJSONObject("previous").getLong("amount"));valid(next);assertEquals(1,b.getJSONArray("receipts").length());
    }
    @Test public void erroneousLeaseArchiveNeverPretendsToBeMoveOut()throws Exception{
        JSONObject b=backup();rejects(()->VoidRecords.archive(b,"leases","L","wrong room"));b.put("invoices",new JSONArray()).put("receipts",new JSONArray());b.getJSONArray("leases").getJSONObject(0).put("initialMeterPhoto","12345678-1234-1234-1234-123456789abc.jpg").put("contractFile","12345678-1234-1234-1234-123456789abc.pdf");JSONObject next=VoidRecords.archive(b,"leases","L","correct room already created");assertEquals(0,next.getJSONArray("leases").length());assertEquals(1,VoidRecords.allLeases(next).length());assertFalse(next.getJSONArray("voidRecords").getJSONObject(0).getJSONObject("previous").has("terminatedOn"));assertEquals(1,InitialMeterPhotos.files(next).size());assertEquals(1,LeaseFiles.files(next).size());VoidRecords.validate(next);
    }
    @Test public void archivedLeaseAttachmentsSurviveZipRoundTrip()throws Exception{
        JSONObject b=backup();b.put("invoices",new JSONArray()).put("receipts",new JSONArray()).put("version",13);String jpg="12345678-1234-1234-1234-123456789abc.jpg",pdf="12345678-1234-1234-1234-123456789abc.pdf";
        b.getJSONArray("leases").getJSONObject(0).put("initialMeterPhoto",jpg).put("contractFile",pdf);b=VoidRecords.archive(b,"leases","L","wrong room");
        java.io.File photos=java.nio.file.Files.createTempDirectory("archived-photos").toFile(),stage=java.nio.file.Files.createTempDirectory("archived-restore").toFile();
        try{java.nio.file.Files.write(new java.io.File(photos,jpg).toPath(),new byte[]{1,2,3});java.nio.file.Files.write(new java.io.File(photos,pdf).toPath(),"%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.UTF_8));java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();BackupArchive.write(output,b,photos);JSONObject restored=BackupArchive.read(new java.io.ByteArrayInputStream(output.toByteArray()),stage);VoidRecords.validate(restored);assertEquals(0,restored.getJSONArray("leases").length());assertTrue(new java.io.File(stage,jpg).isFile());assertTrue(new java.io.File(stage,pdf).isFile());assertEquals(jpg,VoidRecords.allLeases(restored).getJSONObject(0).getString("initialMeterPhoto"));}
        finally{for(java.io.File file:photos.listFiles())file.delete();for(java.io.File file:stage.listFiles())file.delete();photos.delete();stage.delete();}
    }

}
