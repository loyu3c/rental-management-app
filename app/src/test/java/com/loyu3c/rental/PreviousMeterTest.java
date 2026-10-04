package com.loyu3c.rental;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreviousMeterTest {
    private JSONObject reading(String lease,String month,long current,boolean cancelled) throws Exception {return new JSONObject().put("leaseId",lease).put("month",month).put("current",current).put("void",cancelled);}
    @Test public void carriesOnlyPreviousMonthForSameLeaseAndIgnoresVoids() throws Exception {
        JSONArray readings=new JSONArray().put(reading("other","2026-09",999,false)).put(reading("L","2026-08",100,false)).put(reading("L","2026-09",200,false)).put(reading("L","2026-09",250,true)).put(reading("L","2026-10",300,false));String before=readings.toString();
        JSONObject result=MeterReadings.previousPeriod(readings,new JSONArray(),"L","2026-10");assertEquals(200,result.getLong("current"));assertEquals("2026-09",result.getString("month"));assertEquals(before,readings.toString());
        assertNull(MeterReadings.previousPeriod(readings,new JSONArray(),"newTenant","2026-10"));assertNull(MeterReadings.previousPeriod(readings,new JSONArray(),"L","2026-12"));
    }
    @Test public void establishedUtilityBillTakesPriorityAndYearBoundaryWorks() throws Exception {
        JSONArray readings=new JSONArray().put(reading("L","2026-12",200,false));JSONArray bills=new JSONArray().put(new JSONObject().put("leaseId","L").put("month","2026-12").put("kind","水電").put("current",180));
        JSONObject result=MeterReadings.previousPeriod(readings,bills,"L","2027-01");assertEquals(180,result.getLong("current"));assertEquals("水電帳單",result.getString("source"));
        assertNull(MeterReadings.previousPeriod(new JSONArray().put(reading("L","2026-12",200,true)),new JSONArray(),"L","2027-01"));
    }
}
