package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.util.*;

public class DepositsTest {
    private void validate(JSONArray rows) throws Exception {Deposits.validate(rows,new HashSet<>(Arrays.asList("L1","L2")),Collections.singleton("A"));}
    private JSONObject row(String lease,String date,String kind,long amount) throws Exception {return Deposits.movement(lease,"A",date,kind,amount,"");}
    @Test public void partialRefundChangesHeldAndBankButIsNotExpense() throws Exception {
        JSONObject bank=Bookkeeping.account("A","銀行","2026-10-01",50000,"");String id=bank.getString("id");
        JSONArray rows=new JSONArray().put(Deposits.movement("L1",id,"2026-10-02","收到押金",100000,""))
            .put(Deposits.movement("L1",id,"2026-10-03","退還押金",20000,""));
        assertEquals(80000,Deposits.summary("L1",rows).getLong("held"));
        assertEquals(130000,Bookkeeping.balance(bank,"2026-10-03",new JSONArray(),new JSONArray(),new JSONArray(),rows));
        assertEquals(0,Bookkeeping.expenseTotal(new JSONArray(),"2026-10-01","2026-10-31",null));
    }
    @Test public void historicalDepositIsHeldWithoutAddingOpeningBalanceAgain() throws Exception {
        JSONArray rows=new JSONArray().put(row("L1","2026-09-01","收到押金",20000));validate(rows);
        assertEquals(20000,Deposits.summary("L1",rows).getLong("held"));
        assertEquals(0,Deposits.cashflow("A","2026-10-01","2026-10-31",rows));
    }
    @Test public void otherLeaseCannotFundRefund() throws Exception {
        JSONArray rows=new JSONArray().put(row("L1","2026-10-01","收到押金",20000)).put(row("L2","2026-10-02","退還押金",1000));
        try{validate(rows);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void futureReceiptCannotFundEarlierRefund() throws Exception {
        JSONArray rows=new JSONArray().put(row("L1","2026-10-03","收到押金",20000)).put(row("L1","2026-10-02","退還押金",1000));
        try{validate(rows);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void voidingReceiptWithRefundIsRejectedButVoidingRefundRestoresFunds() throws Exception {
        JSONObject received=row("L1","2026-10-01","收到押金",20000),refund=row("L1","2026-10-02","退還押金",5000);
        JSONArray rows=new JSONArray().put(received).put(refund);validate(rows);
        received.put("void",true);try{validate(rows);fail();}catch(IllegalArgumentException expected){}
        received.put("void",false);refund.put("void",true);validate(rows);
        assertEquals(20000,Deposits.summary("L1",rows).getLong("held"));assertEquals(20000,Deposits.cashflow("A","2026-10-01","2026-10-31",rows));
    }
    @Test public void sameDayReceiveAndRefundDoesNotDependOnInputOrder() throws Exception {
        JSONArray rows=new JSONArray().put(row("L1","2026-10-01","退還押金",20000)).put(row("L1","2026-10-01","收到押金",20000));
        validate(rows);assertEquals(0,Deposits.summary("L1",rows).getLong("held"));
    }
    @Test public void missingAccountAndZeroAmountRejected() throws Exception {
        JSONArray rows=new JSONArray().put(row("L1","2026-10-01","收到押金",20000).put("accountId","missing"));
        try{validate(rows);fail();}catch(IllegalArgumentException expected){}
        try{row("L1","2026-10-01","收到押金",0);fail();}catch(IllegalArgumentException expected){}
    }
}
