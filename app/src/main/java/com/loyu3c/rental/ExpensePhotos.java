package com.loyu3c.rental;
import org.json.*;
import java.util.*;

public final class ExpensePhotos {
    public static boolean photoName(String name){return name.matches("exp-[0-9a-fA-F-]{36}\\.jpg");}
    public static JSONArray photos(JSONObject row) throws JSONException {
        if(!row.has("photos"))return new JSONArray();JSONArray photos=row.getJSONArray("photos");if(photos.length()>3)throw new IllegalArgumentException("每筆支出最多3張照片");Set<String> names=new HashSet<>();for(int i=0;i<photos.length();i++){String name=photos.getString(i);if(!photoName(name)||!names.add(name))throw new IllegalArgumentException("支出照片參照無效");}return photos;
    }
    public static Set<String> files(JSONObject backup) throws JSONException {
        Set<String> names=new LinkedHashSet<>();JSONArray rows=backup.optJSONArray("expenses");if(rows==null)return names;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);collect(row,names);JSONArray history=row.optJSONArray("history");if(history!=null)for(int j=0;j<history.length();j++)collect(history.getJSONObject(j).getJSONObject("previous"),names);}return names;
    }
    private static void collect(JSONObject row,Set<String> names) throws JSONException {JSONArray photos=photos(row);for(int i=0;i<photos.length();i++)names.add(photos.getString(i));}
    public static void remap(JSONObject backup,Map<String,String> mapping) throws JSONException {
        JSONArray rows=backup.optJSONArray("expenses");if(rows==null)return;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);remapRow(row,mapping);JSONArray history=row.optJSONArray("history");if(history!=null)for(int j=0;j<history.length();j++)remapRow(history.getJSONObject(j).getJSONObject("previous"),mapping);}
    }
    private static void remapRow(JSONObject row,Map<String,String> mapping) throws JSONException {if(!row.has("photos"))return;JSONArray before=photos(row),after=new JSONArray();for(int i=0;i<before.length();i++){String name=mapping.get(before.getString(i));if(name==null||!photoName(name))throw new IllegalArgumentException("缺少支出照片對照");after.put(name);}row.put("photos",after);}
}
