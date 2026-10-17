package com.loyu3c.rental;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;import java.time.*;
public class RentalUnitsTest {
    private JSONObject u(String id,String type)throws Exception{return new JSONObject().put("id",id).put("propertyId","P").put("type",type);}
    private JSONArray units()throws Exception{return new JSONArray().put(u("W","整棟").put("enabled",false)).put(u("A","房間")).put(u("S","其他空間")).put(u("P","車格"));}
    private JSONArray leases(String id,String end)throws Exception{return new JSONArray().put(new JSONObject().put("unitId",id).put("start","2026-01-01").put("end",end));}
    private final LocalDate now=LocalDate.of(2026,10,17);
    @Test public void eachUnitCanBeEnabledWithoutChangingOtherSpaces()throws Exception {
        JSONArray source=units();String before=source.toString();JSONArray enabled=RentalUnits.setEnabled(source,new JSONArray(),"W",true,now);
        for(int i=0;i<enabled.length();i++)assertTrue(RentalUnits.enabled(enabled.getJSONObject(i)));
        assertEquals(before,source.toString());
        JSONArray off=RentalUnits.setEnabled(enabled,new JSONArray(),"S",false,now);
        assertTrue(RentalUnits.enabled(off.getJSONObject(0)));assertTrue(RentalUnits.enabled(off.getJSONObject(1)));assertFalse(RentalUnits.enabled(off.getJSONObject(2)));assertTrue(RentalUnits.enabled(off.getJSONObject(3)));
        assertArrayEquals(new int[]{0,3},Occupancy.counts(off,new JSONArray(),null,now));
    }
    @Test public void currentAndFutureLeasesStillBlockDisableAtomically()throws Exception {
        JSONArray source=units();String before=source.toString();
        for(String end:new String[]{"2026-10-17","2027-01-01"})try{RentalUnits.setEnabled(source,leases("A",end),"A",false,now);fail();}catch(IllegalArgumentException expected){}
        assertEquals(before,source.toString());
        assertFalse(RentalUnits.enabled(RentalUnits.setEnabled(source,leases("A","2026-10-16"),"A",false,now).getJSONObject(1)));
    }
}
