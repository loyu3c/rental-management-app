package com.loyu3c.rental;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class LeaseDatesTest {
    private JSONObject original() throws Exception {return new JSONObject().put("start","2026-09-02").put("end","2027-09-02").put("rent",8000).put("dueDay",5).put("deposit",16000).put("cycle","月繳");}
    @Test public void anniversaryDefaultHandlesLeapDate(){assertEquals("2027-09-02",LeaseDates.oneYearAfter("2026-09-02"));assertEquals("2025-02-28",LeaseDates.oneYearAfter("2024-02-29"));}
    @Test public void sameTenantFixedTermsCanExtendButCannotShorten() throws Exception {
        JSONObject old=original(),next=new JSONObject(old.toString()).put("end","2028-09-02");String before=old.toString();LeaseDates.validateBilledEdit(old,next);assertEquals(before,old.toString());LeaseDates.validateBilledEdit(old,new JSONObject(old.toString()));
        try{LeaseDates.validateBilledEdit(old,next.put("end","2027-08-01"));fail();}catch(IllegalArgumentException expected){}
        try{LeaseDates.validateBilledEdit(old,new JSONObject(old.toString()).put("rent",9000));fail();}catch(IllegalArgumentException expected){}
        try{LeaseDates.validateBilledEdit(old.put("terminatedOn","2027-09-02"),new JSONObject(old.toString()).put("end","2028-09-02"));fail();}catch(IllegalArgumentException expected){}
    }
}
