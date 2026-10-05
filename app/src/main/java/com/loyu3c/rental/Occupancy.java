package com.loyu3c.rental;
import org.json.*;
import java.time.LocalDate;
public final class Occupancy {
    private static boolean covers(JSONObject target,JSONObject leased){
        if(leased==null)return false;if(target.optString("id").equals(leased.optString("id")))return true;
        return target.optString("propertyId").equals(leased.optString("propertyId"))&&leased.optString("type").equals("整棟")&&(target.optString("type").equals("房間")||target.optString("type").equals("其他空間"));
    }
    private static JSONObject unit(JSONArray units,String id){for(int i=0;i<units.length();i++)if(id.equals(units.optJSONObject(i).optString("id")))return units.optJSONObject(i);return null;}
    public static JSONObject current(JSONArray units,JSONArray leases,JSONObject target,LocalDate today){
        for(int i=0;i<leases.length();i++){JSONObject l=leases.optJSONObject(i);if(!covers(target,unit(units,l.optString("unitId"))))continue;LocalDate start=LocalDate.parse(l.optString("start")),end=LocalDate.parse(l.optString("terminatedOn",l.optString("end")));if(!today.isBefore(start)&&!today.isAfter(end))return l;}return null;
    }
    public static String status(JSONArray units,JSONArray leases,JSONObject target,LocalDate today){
        JSONObject active=current(units,leases,target,today);if(active!=null)return active.optString("unitId").equals(target.optString("id"))?"出租中":"出租中（整棟租約）";
        boolean future=false,history=false;for(int i=0;i<leases.length();i++){JSONObject l=leases.optJSONObject(i);if(!covers(target,unit(units,l.optString("unitId"))))continue;if(LocalDate.parse(l.optString("start")).isAfter(today))future=true;else history=true;}return future?"待起租":history?"租約已到期":"未建租約";
    }
    public static int[] counts(JSONArray units,JSONArray leases,String propertyId,LocalDate today){
        int rented=0,vacant=0;for(int i=0;i<units.length();i++){JSONObject u=units.optJSONObject(i);if(propertyId!=null&&!propertyId.equals(u.optString("propertyId")))continue;if(current(units,leases,u,today)!=null)rented++;else vacant++;}return new int[]{rented,vacant};
    }
}
