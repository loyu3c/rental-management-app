package com.loyu3c.rental;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
public class IncomeTest {
    private JSONArray empty(){return new JSONArray();}
    private long balance(JSONObject account,JSONArray incomes)throws Exception{return Bookkeeping.balance(account,"2026-11-30",empty(),empty(),empty(),empty(),empty(),incomes);}
    @Test public void incomeCreditsOnlySelectedAccountAndExcludesHistoricalFutureVoid()throws Exception{
        JSONObject a=Bookkeeping.account("A","銀行","2026-11-01",1000,""),b=Bookkeeping.account("B","銀行","2026-11-01",2000,"");String id=a.getString("id");
        JSONArray rows=new JSONArray().put(Income.create("2026-11-01",id,"利息收入","銀行利息",25)).put(Income.create("2026-10-31",id,"利息收入","舊利息",99)).put(Income.create("2026-12-01",id,"利息收入","未來",88)).put(Income.create("2026-11-10",id,"利息收入","誤登",77).put("void",true));
        assertEquals(1025,balance(a,rows));assertEquals(2000,balance(b,rows));assertEquals(25,Income.total(rows,"2026-11-01","2026-11-30",null));
    }
    @Test public void editsKeepHistoryWithoutDoubleCreditAndVoidReverses()throws Exception{
        JSONObject a=Bookkeeping.account("A","銀行","2026-11-01",1000,"");String id=a.getString("id");JSONObject first=Income.create("2026-11-05",id,"利息收入","銀行利息",25);
        JSONArray rows=ExpenseEdits.replace(new JSONArray().put(first),first,Income.create("2026-11-06",id,"利息收入","更正利息",35),"2026-11-26T08:00:00Z");
        JSONObject backup=new JSONObject().put("accounts",new JSONArray().put(a)).put("incomes",rows);Income.validate(backup);assertEquals(1035,balance(a,rows));assertEquals(25,rows.getJSONObject(0).getJSONArray("history").getJSONObject(0).getJSONObject("previous").getLong("amount"));
        rows.getJSONObject(0).put("void",true).put("voidReason","登錯").put("voidedAt","2026-11-26T08:01:00Z");Income.validate(backup);assertEquals(1000,balance(a,rows));
    }
    @Test public void backupArchiveRetainsIncomeAndItsEditHistory()throws Exception{
        JSONObject first=Income.create("2026-11-05","bank","利息收入","銀行利息",25);JSONArray rows=ExpenseEdits.replace(new JSONArray().put(first),first,Income.create("2026-11-06","bank","利息收入","更正",35),"2026-11-26T08:00:00Z");
        JSONObject backup=new JSONObject().put("version",14).put("incomes",rows).put("meterReadings",empty()).put("leases",empty()).put("expenses",empty());
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();BackupArchive.write(out,backup,java.nio.file.Files.createTempDirectory("income-photos").toFile());
        JSONObject restored=BackupArchive.read(new java.io.ByteArrayInputStream(out.toByteArray()),java.nio.file.Files.createTempDirectory("income-restore").toFile());
        assertEquals(35,restored.getJSONArray("incomes").getJSONObject(0).getLong("amount"));assertEquals(25,restored.getJSONArray("incomes").getJSONObject(0).getJSONArray("history").getJSONObject(0).getJSONObject("previous").getLong("amount"));
    }
    @Test public void validationRejectsMissingAccountAndInvalidAmount()throws Exception{
        try{Income.create("2026-11-26","a","利息收入","利息",0);fail();}catch(IllegalArgumentException expected){}
        try{Income.validate(new JSONObject().put("accounts",empty()).put("incomes",new JSONArray().put(Income.create("2026-11-26","missing","利息收入","利息",20))));fail();}catch(IllegalArgumentException expected){}
    }
}
