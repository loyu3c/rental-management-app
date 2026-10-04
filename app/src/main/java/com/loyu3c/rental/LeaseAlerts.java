package com.loyu3c.rental;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only expiry list: inclusive horizon, oldest expired lease first. */
public final class LeaseAlerts {
    private LeaseAlerts() {}
    public static JSONArray upcoming(JSONArray leases,LocalDate today,int days) throws Exception {
        if(days<0)throw new IllegalArgumentException("提醒天數不得為負數");
        LocalDate limit=today.plusDays(days);List<JSONObject> selected=new ArrayList<>();
        for(int i=0;i<leases.length();i++){
            JSONObject lease=leases.getJSONObject(i);
            if(lease.has("terminatedOn"))continue;
            if(!LocalDate.parse(lease.getString("end")).isAfter(limit))selected.add(lease);
        }
        selected.sort(Comparator.comparing((JSONObject l)->l.optString("end")).thenComparing(l->l.optString("unitCodeSnapshot")).thenComparing(l->l.optString("id")));
        JSONArray result=new JSONArray();for(JSONObject lease:selected)result.put(lease);return result;
    }
}
