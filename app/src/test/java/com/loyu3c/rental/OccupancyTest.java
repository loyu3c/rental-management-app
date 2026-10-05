package com.loyu3c.rental;
import org.json.*;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;
public class OccupancyTest {
    private JSONObject u(String id,String property,String type) throws Exception{return new JSONObject().put("id",id).put("propertyId",property).put("type",type);}
    private JSONObject l(String unit,String start,String end) throws Exception{return new JSONObject().put("unitId",unit).put("start",start).put("end",end).put("tenant","租客");}
    @Test public void countsByPropertyAreReadOnlyAndUseTodayNotAnyHistoricalLease() throws Exception{
        JSONArray units=new JSONArray().put(u("A","P","房間")).put(u("B","P","房間")).put(u("C","Q","車格")).put(u("D","P","房間"));
        JSONArray leases=new JSONArray().put(l("A","2026-01-01","2027-01-01")).put(l("B","2026-11-01","2027-11-01")).put(l("C","2025-01-01","2026-01-01"));LocalDate today=LocalDate.of(2026,10,5);String before=leases.toString();
        assertArrayEquals(new int[]{1,3},Occupancy.counts(units,leases,null,today));assertArrayEquals(new int[]{1,2},Occupancy.counts(units,leases,"P",today));assertArrayEquals(new int[]{0,0},Occupancy.counts(units,leases,"empty",today));
        assertEquals("待起租",Occupancy.status(units,leases,units.getJSONObject(1),today));assertEquals("租約已到期",Occupancy.status(units,leases,units.getJSONObject(2),today));assertEquals("未建租約",Occupancy.status(units,leases,units.getJSONObject(3),today));assertEquals(before,leases.toString());
    }
    @Test public void wholeBuildingCoversRoomsButNotIndependentParkingOrLand() throws Exception{
        JSONArray units=new JSONArray().put(u("whole","P","整棟")).put(u("room","P","房間")).put(u("car","P","車格")).put(u("land","P","土地出租範圍")).put(u("other","Q","房間"));
        JSONArray leases=new JSONArray().put(l("whole","2026-01-01","2027-01-01"));LocalDate today=LocalDate.of(2026,10,5);
        assertEquals("出租中（整棟租約）",Occupancy.status(units,leases,units.getJSONObject(1),today));assertArrayEquals(new int[]{2,3},Occupancy.counts(units,leases,null,today));
        leases.put(l("car","2026-01-01","2027-01-01"));assertArrayEquals(new int[]{3,2},Occupancy.counts(units,leases,null,today));
    }
    @Test public void effectiveEndAndTenantChangeStayConsistent() throws Exception{
        JSONArray units=new JSONArray().put(u("room","P","房間"));JSONArray leases=new JSONArray().put(l("room","2026-01-01","2027-01-01").put("terminatedOn","2026-10-04")).put(l("room","2026-10-06","2027-10-06").put("tenant","新租客"));
        assertArrayEquals(new int[]{0,1},Occupancy.counts(units,leases,null,LocalDate.of(2026,10,5)));
        assertEquals("新租客",Occupancy.current(units,leases,units.getJSONObject(0),LocalDate.of(2026,10,6)).getString("tenant"));
        assertArrayEquals(new int[]{1,0},Occupancy.counts(units,leases,null,LocalDate.of(2027,10,6)));assertArrayEquals(new int[]{0,1},Occupancy.counts(units,leases,null,LocalDate.of(2027,10,7)));
    }
}
