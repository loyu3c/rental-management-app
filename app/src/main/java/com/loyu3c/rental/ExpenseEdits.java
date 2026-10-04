package com.loyu3c.rental;
import org.json.*;
import java.time.Instant;
/** Replace one effective record; prior snapshots never participate in cash totals. */
public final class ExpenseEdits {
    public static JSONArray replace(JSONArray rows,JSONObject expected,JSONObject replacement,String timestamp) throws JSONException {
        Instant.parse(timestamp);JSONArray result=new JSONArray(rows.toString());int index=-1;
        for(int i=0;i<result.length();i++)if(expected.getString("id").equals(result.getJSONObject(i).getString("id")))index=i;
        if(index<0||!result.getJSONObject(index).toString().equals(expected.toString()))throw new IllegalArgumentException("這筆紀錄已變動，請重新開啟修改");
        if(expected.getBoolean("void"))throw new IllegalArgumentException("已撤銷紀錄不能修改");
        JSONObject next=new JSONObject(replacement.toString());next.put("id",expected.getString("id")).put("void",false);
        JSONArray history=expected.has("history")?new JSONArray(expected.getJSONArray("history").toString()):new JSONArray();
        if(history.length()>=1000)throw new IllegalArgumentException("修改紀錄已達上限");
        JSONObject prior=new JSONObject(expected.toString());prior.remove("history");
        history.put(new JSONObject().put("changedAt",timestamp).put("previous",prior));next.put("history",history).put("editedAt",timestamp);result.put(index,next);return result;
    }
    public static void validate(JSONObject row) throws JSONException {
        if(!row.has("history")){if(row.has("editedAt"))throw new IllegalArgumentException("修改紀錄不完整");return;}
        JSONArray history=row.getJSONArray("history");if(history.length()==0||history.length()>1000)throw new IllegalArgumentException("修改紀錄無效");
        String last="";for(int i=0;i<history.length();i++){
            JSONObject entry=history.getJSONObject(i),old=entry.getJSONObject("previous");Instant.parse(entry.getString("changedAt"));
            if(!row.getString("id").equals(old.getString("id"))||old.has("history")||old.getBoolean("void"))throw new IllegalArgumentException("修改前版本無效");
            Bookkeeping.expense(old.getString("date"),old.getString("accountId"),old.getString("category"),old.getString("description"),Ledger.money(old,"amount"),old.getJSONArray("splits"));
            last=entry.getString("changedAt");
        }
        if(!last.equals(row.getString("editedAt")))throw new IllegalArgumentException("修改時間不一致");
    }
}
