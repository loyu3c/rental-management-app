package com.loyu3c.rental;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ExpenseEditsTest {
    private JSONObject expense(String account,String date,long amount) throws Exception {return Bookkeeping.expense(date,account,"修繕","燈具",amount,new JSONArray().put(new JSONObject().put("propertyId","P").put("amount",amount)));}
    @Test public void accountDateAmountChangeCountsOnlyLatestAndKeepsOldSnapshot() throws Exception {
        JSONObject a=Bookkeeping.account("A","銀行","2026-01-01",1000,"").put("id","A"),b=Bookkeeping.account("B","銀行","2026-01-01",1000,"").put("id","B");
        JSONObject original=expense("A","2026-09-30",100);JSONArray rows=new JSONArray().put(original);String before=rows.toString();
        JSONArray revised=ExpenseEdits.replace(rows,original,expense("B","2026-10-01",200),"2026-10-04T14:00:00Z");JSONObject current=revised.getJSONObject(0);ExpenseEdits.validate(current);
        assertEquals(before,rows.toString());assertEquals(original.getString("id"),current.getString("id"));assertEquals(original.toString(),current.getJSONArray("history").getJSONObject(0).getJSONObject("previous").toString());
        assertEquals(1000,Bookkeeping.balance(a,"2026-10-04",new JSONArray(),revised,new JSONArray()));assertEquals(800,Bookkeeping.balance(b,"2026-10-04",new JSONArray(),revised,new JSONArray()));
        assertEquals(0,Bookkeeping.expenseTotal(revised,"2026-09-01","2026-09-30",null));assertEquals(200,Bookkeeping.expenseTotal(revised,"2026-10-01","2026-10-31","P"));
        JSONArray twice=ExpenseEdits.replace(revised,current,expense("A","2026-10-02",300),"2026-10-04T14:01:00Z");ExpenseEdits.validate(twice.getJSONObject(0));assertEquals(2,twice.getJSONObject(0).getJSONArray("history").length());
        JSONObject restored=new JSONArray(twice.toString()).getJSONObject(0);ExpenseEdits.validate(restored);assertEquals(300,restored.getLong("amount"));
        restored.put("void",true);assertEquals(0,Bookkeeping.expenseTotal(new JSONArray().put(restored),"2026-10-01","2026-10-31",null));assertEquals(2,restored.getJSONArray("history").length());
    }
    @Test public void staleOrVoidedEditCannotOverwriteCurrentRecord() throws Exception {
        JSONObject old=expense("A","2026-10-01",100),replacement=expense("A","2026-10-01",200);JSONArray rows=new JSONArray().put(old);
        JSONArray changed=ExpenseEdits.replace(rows,old,replacement,"2026-10-04T14:00:00Z");
        try{ExpenseEdits.replace(changed,old,replacement,"2026-10-04T14:01:00Z");fail();}catch(IllegalArgumentException expected){}
        old.put("void",true);try{ExpenseEdits.replace(rows,old,replacement,"2026-10-04T14:01:00Z");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void invalidHistoryCannotBeRestored() throws Exception {
        JSONObject old=expense("A","2026-10-01",100);JSONObject changed=ExpenseEdits.replace(new JSONArray().put(old),old,expense("A","2026-10-01",200),"2026-10-04T14:00:00Z").getJSONObject(0);
        changed.getJSONArray("history").getJSONObject(0).getJSONObject("previous").put("amount",101);
        try{ExpenseEdits.validate(changed);fail();}catch(IllegalArgumentException expected){}
    }
}
