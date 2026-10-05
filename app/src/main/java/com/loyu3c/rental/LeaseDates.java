package com.loyu3c.rental;
import java.time.LocalDate;
import org.json.*;
public final class LeaseDates {
    public static String oneYearAfter(String start){return LocalDate.parse(start).plusYears(1).toString();}
    /** Existing posted transactions keep their original rent/deposit terms. */
    public static void validateBilledEdit(JSONObject original,JSONObject changed) throws JSONException {
        if(original.has("terminatedOn"))throw new IllegalArgumentException("已退租租約不能延長，請新增租約");
        if(LocalDate.parse(changed.getString("end")).isBefore(LocalDate.parse(original.getString("end"))))throw new IllegalArgumentException("已有帳務的租約只能延長到期日，不能縮短");
        for(String key:new String[]{"start","rent","dueDay","deposit","cycle"})if(!original.get(key).toString().equals(changed.get(key).toString()))throw new IllegalArgumentException("已有帳務的起租日、租金、應繳日、押金與繳款週期不能直接修改");
    }
}
