package com.loyu3c.rental;

import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LeaseAlertsTest {
    private JSONObject lease(String id,String end) throws Exception {return new JSONObject().put("id",id).put("end",end).put("unitCodeSnapshot",id);}
    @Test public void includesExpiredTodayAndHorizonButExcludesTerminatedAndLater() throws Exception {
        LocalDate today=LocalDate.of(2026,10,4);
        JSONArray rows=new JSONArray().put(lease("later",today.plusDays(61).toString())).put(lease("edge",today.plusDays(60).toString())).put(lease("today",today.toString())).put(lease("expired","2026-09-01")).put(lease("ended","2026-08-01").put("terminatedOn","2026-08-01"));
        String original=rows.toString();JSONArray result=LeaseAlerts.upcoming(rows,today,60);
        assertEquals(3,result.length());assertEquals("expired",result.getJSONObject(0).getString("id"));assertEquals("today",result.getJSONObject(1).getString("id"));assertEquals("edge",result.getJSONObject(2).getString("id"));assertEquals(original,rows.toString());
    }
    @Test public void emptyListAndZeroDayHorizonAreSupported() throws Exception {
        LocalDate today=LocalDate.of(2026,10,4);assertEquals(0,LeaseAlerts.upcoming(new JSONArray(),today,60).length());
        JSONArray rows=new JSONArray().put(lease("C1",today.toString())).put(lease("R1401",today.plusDays(1).toString()));
        assertEquals("C1",LeaseAlerts.upcoming(rows,today,0).getJSONObject(0).getString("unitCodeSnapshot"));
    }
}
