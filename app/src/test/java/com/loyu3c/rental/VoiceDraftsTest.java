package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.time.*;

public class VoiceDraftsTest {
    private JSONObject catalog() throws Exception {
        JSONArray p=new JSONArray().put(new JSONObject().put("id","PA").put("name","A棟")).put(new JSONObject().put("id","PB").put("name","B棟"));
        JSONArray a=new JSONArray().put(new JSONObject().put("id","A").put("name","A帳戶")).put(new JSONObject().put("id","CASH").put("name","共用現金"));
        JSONArray units=new JSONArray().put(new JSONObject().put("id","U").put("code","R1401").put("propertyId","PA"));
        JSONArray ls=new JSONArray().put(new JSONObject().put("id","L").put("unitId","U").put("tenant","王小明").put("start","2026-01-01").put("end","2027-01-01"));
        return new JSONObject().put("properties",p).put("accounts",a).put("units",units).put("leases",ls);
    }
    private JSONObject parse(String text) throws Exception {return VoiceDrafts.parse(text,LocalDate.of(2026,10,4),catalog());}
    @Test public void chineseAndArabicMoneyWithNoShorthandGuess() throws Exception {
        assertEquals(3000,VoiceDrafts.integer("三千"));assertEquals(12500,VoiceDrafts.integer("一萬二千五百"));assertEquals(102,VoiceDrafts.integer("一百零二"));assertEquals(3005,VoiceDrafts.integer("三千零五"));assertEquals(10000,VoiceDrafts.integer("10000"));
        for(String ambiguous:new String[]{"三千五","一萬二","三百百"})try{VoiceDrafts.integer(ambiguous);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void repairDraftIncludesExplicitDateSourceAndPropertyWithoutSaving() throws Exception {
        JSONObject b=catalog();String before=b.toString();JSONObject d=VoiceDrafts.parse("今天A棟修繕總共三千元，從共用現金付款",LocalDate.of(2026,10,4),b);assertEquals("支出",d.getString("kind"));assertEquals("修繕",d.getString("category"));assertEquals(3000,d.getLong("amount"));assertEquals("CASH",d.getString("accountId"));assertEquals("2026-10-04",d.getString("date"));assertEquals("PA",d.getJSONArray("propertyIds").getString(0));assertEquals(before,b.toString());
    }
    @Test public void rentDraftMatchesRoomAndTenantNotPhoneDigits() throws Exception {
        JSONObject d=parse("今天收到R1401租金一萬元，匯入A帳戶");assertEquals("收款",d.getString("kind"));assertEquals("L",d.getString("leaseId"));assertEquals(10000,d.getLong("amount"));assertEquals("A",d.getString("accountId"));assertEquals("",parse("今天收到租金一萬元，電話0912341401").getString("leaseId"));
    }
    @Test public void transferRequiresExplicitDirectionAndDoesNotBecomeExpense() throws Exception {
        JSONObject d=parse("今天從A帳戶提款一萬元到共用現金");assertEquals("轉帳",d.getString("kind"));assertEquals("A",d.getString("fromId"));assertEquals("CASH",d.getString("toId"));assertEquals("",parse("今天A帳戶與共用現金轉帳一萬元").getString("fromId"));
    }
    @Test public void multiPropertySplitsNeedAmountsNotEqualDivision() throws Exception {
        JSONObject d=parse("今天A棟和B棟修繕總共三千元，從共用現金付款");assertEquals(0,d.getJSONArray("splits").length());assertEquals(2,d.getJSONArray("propertyIds").length());
        d=parse("今天A棟一千元，B棟兩千元，修繕總共三千元，從共用現金付款");assertEquals(2,d.getJSONArray("splits").length());assertEquals(3000,d.getLong("amount"));
    }
    @Test public void ambiguousDatesAmountsAndAccountsRemainUnselected() throws Exception {
        JSONObject d=parse("修繕一千元和兩千元，A帳戶與共用現金");assertTrue(d.isNull("amount"));assertEquals("",d.getString("date"));assertEquals("",d.getString("accountId"));assertEquals("",parse("今天昨天修繕三千元").getString("date"));
    }
    @Test public void unsupportedDepositRefundAndMixedFeeAreBlocked() throws Exception {
        assertTrue(parse("今天收到押金兩萬元").getBoolean("blocked"));assertTrue(parse("今天提款一萬元手續費十五元").getBoolean("blocked"));assertTrue(parse("今天退款一千元").getBoolean("blocked"));
    }
    @Test public void invalidAndFractionalAmountsAreNotRounded() throws Exception {
        assertTrue(parse("今天修繕負一千元").isNull("amount"));assertTrue(parse("今天修繕10.5元").isNull("amount"));assertTrue(parse("今天修繕三千五元").isNull("amount"));
    }
    @Test public void duplicateRoomsAndExpiredLeasesRequireManualSelection() throws Exception {
        JSONObject b=catalog();b.getJSONArray("leases").put(new JSONObject(b.getJSONArray("leases").getJSONObject(0).toString()).put("id","L2"));assertEquals("",VoiceDrafts.parse("今天收到R1401租金一萬元",LocalDate.of(2026,10,4),b).getString("leaseId"));b=catalog();b.getJSONArray("leases").getJSONObject(0).put("terminatedOn","2026-10-03");assertEquals("",VoiceDrafts.parse("今天收到R1401租金一萬元",LocalDate.of(2026,10,4),b).getString("leaseId"));
    }
    @Test public void explicitAndRelativeDatesAndThousandsSeparators() throws Exception {
        assertEquals("2026-10-03",parse("昨天A棟修繕3,000元").getString("date"));assertEquals(3000,parse("2026年10月2日A棟修繕3,000元").getLong("amount"));assertEquals("2026-10-02",parse("2026年10月2日A棟修繕3,000元").getString("date"));assertEquals("",parse("2026年2月30日A棟修繕3000元").getString("date"));
    }
}
