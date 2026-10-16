package com.loyu3c.rental;
import org.json.*;
import java.util.*;
public final class InitialMeterPhotos {
    public static Set<String> files(JSONObject backup)throws JSONException{Set<String> files=new LinkedHashSet<>();JSONArray leases=backup.optJSONArray("leases");if(leases!=null)for(int i=0;i<leases.length();i++){String name=leases.getJSONObject(i).optString("initialMeterPhoto");if(!name.isEmpty()){if(!MeterReadings.photoName(name)||!files.add(name))throw new IllegalArgumentException("起始電表照片參照無效或重複");}}return files;}
}
