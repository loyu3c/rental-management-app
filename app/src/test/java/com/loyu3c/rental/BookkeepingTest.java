package com.loyu3c.rental;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;

public class BookkeepingTest {
    private JSONObject account(String name,String type) throws Exception {return Bookkeeping.account(name,type,"2026-10-01",50000,"");}
    private JSONArray split(long a,long b) throws Exception {return new JSONArray().put(new JSONObject().put("propertyId","A").put("amount",a)).put(new JSONObject().put("propertyId","B").put("amount",b));}
    @Test public void threeBankWithdrawalsAreNotExpenses() throws Exception {
        JSONObject a=account("A","銀行"),b=account("B","銀行"),c=account("C","銀行"),cash=Bookkeeping.account("共用現金","現金","2026-10-01",0,"");
        JSONArray transfers=new JSONArray();for(JSONObject bank:new JSONObject[]{a,b,c})transfers.put(Bookkeeping.transfer("2026-10-02",bank.getString("id"),cash.getString("id"),10000,"提款"));
        for(JSONObject bank:new JSONObject[]{a,b,c})assertEquals(40000,Bookkeeping.balance(bank,"2026-10-02",new JSONArray(),new JSONArray(),transfers));
        assertEquals(30000,Bookkeeping.balance(cash,"2026-10-02",new JSONArray(),new JSONArray(),transfers));
        assertEquals(0,Bookkeeping.expenseTotal(new JSONArray(),"2026-10-01","2026-10-31",null));
    }
    @Test public void allocatedExpenseDebitsCashOnlyOnce() throws Exception {
        JSONObject cash=account("共用現金","現金");JSONArray expenses=new JSONArray().put(Bookkeeping.expense("2026-10-02",cash.getString("id"),"修繕","燈具更換",3000,split(1000,2000)));
        assertEquals(47000,Bookkeeping.balance(cash,"2026-10-02",new JSONArray(),expenses,new JSONArray()));
        assertEquals(3000,Bookkeeping.expenseTotal(expenses,"2026-10-01","2026-10-31",null));
        assertEquals(1000,Bookkeeping.expenseTotal(expenses,"2026-10-01","2026-10-31","A"));assertEquals(2000,Bookkeeping.expenseTotal(expenses,"2026-10-01","2026-10-31","B"));
    }
    @Test public void allocationMismatchAndSameAccountTransferRejected() throws Exception {
        try{Bookkeeping.expense("2026-10-02","cash","修繕","燈具",3000,split(1000,1000));fail();}catch(IllegalArgumentException expected){}
        try{Bookkeeping.transfer("2026-10-02","A","A",10000,"");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void historyBeforeOpeningIsReportedWithoutChangingBalance() throws Exception {
        JSONObject a=account("A","銀行");JSONArray expenses=new JSONArray().put(Bookkeeping.expense("2026-09-20",a.getString("id"),"房貸扣款","九月房貸",3000,split(1000,2000)));
        assertEquals(50000,Bookkeeping.balance(a,"2026-10-02",new JSONArray(),expenses,new JSONArray()));
        assertEquals(3000,Bookkeeping.expenseTotal(expenses,"2026-09-01","2026-09-30",null));
        try{Bookkeeping.balance(a,"2026-09-20",new JSONArray(),expenses,new JSONArray());fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void annualReceiptCashMovementDoesNotDependOnAllocations() throws Exception {
        JSONObject a=account("A","銀行");JSONArray receipts=new JSONArray().put(new JSONObject().put("date","2026-10-02").put("accountId",a.getString("id")).put("amount",120000).put("allocations",new JSONArray()));
        assertEquals(170000,Bookkeeping.balance(a,"2026-10-02",receipts,new JSONArray(),new JSONArray()));
        receipts.getJSONObject(0).put("allocations",new JSONArray().put(new JSONObject().put("amount",10000)));
        assertEquals(170000,Bookkeeping.balance(a,"2026-10-02",receipts,new JSONArray(),new JSONArray()));
    }
    @Test public void voidedMovementNoLongerAffectsExpenseOrBalances() throws Exception {
        JSONObject a=account("A","銀行"),cash=account("現金","現金");JSONObject expense=Bookkeeping.expense("2026-10-02",a.getString("id"),"修繕","燈具",3000,split(1000,2000)).put("void",true);
        JSONObject transfer=Bookkeeping.transfer("2026-10-02",a.getString("id"),cash.getString("id"),10000,"").put("void",true);
        assertEquals(50000,Bookkeeping.balance(a,"2026-10-02",new JSONArray(),new JSONArray().put(expense),new JSONArray().put(transfer)));
        assertEquals(0,Bookkeeping.expenseTotal(new JSONArray().put(expense),"2026-10-01","2026-10-31",null));
    }
    @Test public void unlinkedReceiptsAndFutureTransactionsDoNotSilentlyEnterBalance() throws Exception {
        JSONObject a=account("A","銀行");JSONArray receipts=new JSONArray().put(new JSONObject().put("date","2026-10-02").put("amount",5000));
        JSONArray expenses=new JSONArray().put(Bookkeeping.expense("2026-11-01",a.getString("id"),"房貸扣款","房貸",3000,split(1000,2000)));
        assertEquals(50000,Bookkeeping.balance(a,"2026-10-31",receipts,expenses,new JSONArray()));
        receipts.getJSONObject(0).put("accountId",a.getString("id"));assertEquals(55000,Bookkeeping.balance(a,"2026-10-31",receipts,expenses,new JSONArray()));
    }
    @Test public void backupRejectsInvalidAccountAndPropertyReferences() throws Exception {
        JSONObject a=account("A","銀行");JSONObject backup=new JSONObject().put("accounts",new JSONArray().put(a)).put("properties",new JSONArray().put(new JSONObject().put("id","A")).put(new JSONObject().put("id","B")))
            .put("receipts",new JSONArray()).put("expenses",new JSONArray()).put("transfers",new JSONArray()).put("reconciliations",new JSONArray());
        Bookkeeping.validate(backup);
        backup.getJSONArray("expenses").put(Bookkeeping.expense("2026-10-02","missing","修繕","燈具",3000,split(1000,2000)));
        try{Bookkeeping.validate(backup);fail();}catch(IllegalArgumentException expected){}
    }
}
