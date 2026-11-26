package com.loyu3c.rental;
import org.json.*;
import java.time.*;
import java.util.*;
/** General cash income, independent of leases and property allocations. */
public final class Income {
    public static JSONObject create(String date,String account,String category,String description,long amount)throws JSONException{
        LocalDate.parse(date);
        if(account.isEmpty()||category.trim().isEmpty()||description.trim().isEmpty()||amount<=0||amount>1000000000L)throw new IllegalArgumentException("請填收款帳戶、分類、項目與金額（1至1,000,000,000元）");
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("date",date).put("accountId",account).put("category",category.trim()).put("description",description.trim()).put("amount",amount).put("income",true).put("void",false);
    }
    public static long total(JSONArray rows,String start,String end,String account)throws JSONException{
        LocalDate.parse(start);LocalDate.parse(end);long sum=0;
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(!r.optBoolean("void")&&(account==null||account.equals(r.getString("accountId")))&&r.getString("date").compareTo(start)>=0&&r.getString("date").compareTo(end)<=0)sum=Math.addExact(sum,Ledger.money(r,"amount"));}return sum;
    }
    private static void row(JSONObject r,Set<String> accounts)throws JSONException{
        create(r.getString("date"),r.getString("accountId"),r.getString("category"),r.getString("description"),Ledger.money(r,"amount"));
        if(!r.getBoolean("income")||!accounts.contains(r.getString("accountId")))throw new IllegalArgumentException("收入帳戶不存在");r.getBoolean("void");
    }
    public static void validate(JSONObject backup)throws JSONException{
        JSONArray rows=backup.optJSONArray("incomes");if(rows==null)return;Set<String> accounts=new HashSet<>(),ids=new HashSet<>();JSONArray bank=backup.getJSONArray("accounts");for(int i=0;i<bank.length();i++)accounts.add(bank.getJSONObject(i).getString("id"));
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);row(r,accounts);if(r.getString("id").isEmpty()||!ids.add(r.getString("id")))throw new IllegalArgumentException("收入ID重複");
            if(r.getBoolean("void")){if(r.getString("voidReason").trim().isEmpty())throw new IllegalArgumentException("請填撤銷原因");Instant.parse(r.getString("voidedAt"));}
            JSONArray history=r.optJSONArray("history");if(history!=null){if(history.length()==0||history.length()>1000)throw new IllegalArgumentException("修改紀錄無效");String last="";for(int j=0;j<history.length();j++){JSONObject h=history.getJSONObject(j),old=h.getJSONObject("previous");row(old,accounts);if(!old.getString("id").equals(r.getString("id"))||old.optBoolean("void")||old.has("history"))throw new IllegalArgumentException("修改前版本無效");last=h.getString("changedAt");Instant.parse(last);}if(!last.equals(r.getString("editedAt")))throw new IllegalArgumentException("修改時間不一致");}
        }
    }
}
