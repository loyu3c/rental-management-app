package com.loyu3c.rental;
import org.json.*;
import java.time.LocalDate;

public final class RentalUnits {
    public static boolean enabled(JSONObject unit){return unit.optBoolean("enabled",true);}
    private static boolean committed(JSONArray leases,String id,LocalDate today){for(int i=0;i<leases.length();i++){JSONObject l=leases.optJSONObject(i);if(id.equals(l.optString("unitId"))&&!LocalDate.parse(l.optString("terminatedOn",l.optString("end"))).isBefore(today))return true;}return false;}
    public static JSONArray setEnabled(JSONArray units,JSONArray leases,String id,boolean enabled,LocalDate today)throws JSONException{
        JSONArray next=new JSONArray(units.toString());JSONObject target=null;for(int i=0;i<next.length();i++)if(id.equals(next.getJSONObject(i).optString("id")))target=next.getJSONObject(i);
        if(target==null)throw new IllegalArgumentException("出租單位不存在");
        if(!enabled&&committed(leases,id,today))throw new IllegalArgumentException("尚有目前或未來租約，請先完成退租／處理租約，再停用");
        target.put("enabled",enabled);return next;
    }
}
