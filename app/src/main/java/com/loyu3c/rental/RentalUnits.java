package com.loyu3c.rental;
import org.json.*;
import java.time.LocalDate;

public final class RentalUnits {
    public static boolean enabled(JSONObject unit){return unit.optBoolean("enabled",true);}
    public static boolean room(JSONObject unit){return unit.optString("type").equals("房間")||unit.optString("type").equals("其他空間");}
    private static boolean committed(JSONArray leases,String id,LocalDate today){for(int i=0;i<leases.length();i++){JSONObject l=leases.optJSONObject(i);if(id.equals(l.optString("unitId"))&&!LocalDate.parse(l.optString("terminatedOn",l.optString("end"))).isBefore(today))return true;}return false;}
    public static JSONArray setEnabled(JSONArray units,JSONArray leases,String id,boolean enabled,LocalDate today)throws JSONException{
        JSONArray next=new JSONArray(units.toString());JSONObject target=null;for(int i=0;i<next.length();i++)if(id.equals(next.getJSONObject(i).optString("id")))target=next.getJSONObject(i);
        if(target==null)throw new IllegalArgumentException("出租單位不存在");
        if(!enabled&&committed(leases,id,today))throw new IllegalArgumentException("尚有目前或未來租約，請先完成退租／處理租約，再停用");
        if(enabled)for(int i=0;i<next.length();i++){JSONObject other=next.getJSONObject(i);if(other==target||!target.optString("propertyId").equals(other.optString("propertyId")))continue;boolean opposite=(target.optString("type").equals("整棟")&&room(other))||(room(target)&&other.optString("type").equals("整棟"));if(opposite&&RentalUnits.enabled(other))throw new IllegalArgumentException("整棟與分房不可同時啟用，請使用切換出租方式");}
        target.put("enabled",enabled);return next;
    }
    public static JSONArray switchMode(JSONArray units,JSONArray leases,String property,boolean whole,LocalDate today)throws JSONException{
        JSONArray next=new JSONArray(units.toString());int wholes=0,rooms=0;
        for(int i=0;i<next.length();i++){JSONObject u=next.getJSONObject(i);if(!property.equals(u.optString("propertyId")))continue;boolean isWhole=u.optString("type").equals("整棟"),isRoom=room(u);if(!isWhole&&!isRoom)continue;if(isWhole)wholes++;else rooms++;boolean on=whole?isWhole:isRoom;if(!on&&committed(leases,u.optString("id"),today))throw new IllegalArgumentException("要停用的單位尚有目前或未來租約，請先處理租約");u.put("enabled",on);}
        if(whole&&wholes!=1)throw new IllegalArgumentException("整棟出租需要且只能有一個整棟單位");if(!whole&&rooms==0)throw new IllegalArgumentException("請先建立房間／其他空間單位");return next;
    }
}
