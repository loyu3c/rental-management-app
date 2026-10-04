package com.loyu3c.rental;
import org.json.*;
import java.util.*;
public final class ExpenseCategories {
    public static JSONArray defaults() throws JSONException {
        JSONArray a=new JSONArray();for(String name:new String[]{"修繕","電梯保養","房貸扣款","其他費用","清潔打掃"})a.put(new JSONObject().put("id",name).put("name",name.equals("修繕")?"修繕費用":name).put("active",true));return a;
    }
    public static JSONArray catalog(JSONObject backup) throws JSONException {JSONArray a=backup.optJSONArray("expenseCategories");return a==null?defaults():a;}
    public static String name(JSONArray rows,String id) {for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&id.equals(r.optString("id")))return r.optString("name");}return id;}
    public static void validate(JSONArray rows,JSONArray expenses) throws JSONException {
        Set<String> ids=new HashSet<>(),names=new HashSet<>();if(rows.length()==0||rows.length()>100)throw new IllegalArgumentException("支出類別需有1至100筆");
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);String n=r.getString("name").trim();if(r.getString("id").isEmpty()||!ids.add(r.getString("id"))||n.isEmpty()||n.length()>40||!names.add(n.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("類別名稱不可空白、重複或超過40字");r.getBoolean("active");}
        for(int i=0;i<expenses.length();i++)if(!ids.contains(expenses.getJSONObject(i).getString("category")))throw new IllegalArgumentException("支出類別不存在");
    }
}
