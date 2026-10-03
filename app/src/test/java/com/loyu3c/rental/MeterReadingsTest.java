package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;

public class MeterReadingsTest {
    private JSONObject lease(String id) throws Exception {return new JSONObject().put("id",id).put("unitId","U").put("start","2026-01-01").put("end","2027-01-01");}
    private JSONObject row(String id,long current) throws Exception {return MeterReadings.reading(lease(id),"2026-10","2026-10-03",100,current,"","");}
    @Test public void latestReadingStaysWithLeaseAndMonthAndSkipsVoidedCorrections() throws Exception {
        JSONArray rows=new JSONArray().put(row("OLD",200)).put(row("NEW",300)).put(row("OLD",400).put("void",true));assertEquals(200,MeterReadings.latest(rows,"OLD","2026-10").getLong("current"));assertNull(MeterReadings.latest(rows,"OLD","2026-11"));assertEquals(300,MeterReadings.latest(rows,"NEW","2026-10").getLong("current"));
    }
    @Test public void backwardsReadingAndOutsideActualLeaseRejected() throws Exception {
        try{row("L",99);fail();}catch(IllegalArgumentException expected){}
        JSONObject lease=lease("L").put("terminatedOn","2026-09-30");try{MeterReadings.reading(lease,"2026-10","2026-10-03",100,200,"","");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void missingLeaseOrDifferentUnitRejectedOnRestore() throws Exception {
        JSONObject b=new JSONObject().put("leases",new JSONArray().put(lease("L"))).put("meterReadings",new JSONArray().put(row("L",200)));MeterReadings.validate(b);b.getJSONArray("meterReadings").getJSONObject(0).put("unitId","OTHER");try{MeterReadings.validate(b);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void unsafeFileNameRejected() throws Exception {assertFalse(MeterReadings.photoName("../photo.jpg"));assertFalse(MeterReadings.photoName("photo.jpg"));assertTrue(MeterReadings.photoName("12345678-1234-1234-1234-123456789abc.jpg"));}
}
