package com.loyu3c.rental;
import org.json.*;
import java.time.*;
import java.util.*;

public final class MeterReadings {
    public static boolean photoName(String name) {return name.matches("[0-9a-fA-F-]{36}\\.jpg");}
    public static JSONObject reading(JSONObject lease,String month,String date,long previous,long current,String file,String note) throws JSONException {
        YearMonth ym=YearMonth.parse(month);LocalDate.parse(date);
        if(ym.atEndOfMonth().isBefore(LocalDate.parse(lease.getString("start")))||ym.atDay(1).isAfter(Settlements.effectiveEnd(lease)))throw new IllegalArgumentException("抄表月份不在租期內");
        if(previous<0||current<previous||current>1000000000L||(!file.isEmpty()&&!photoName(file)))throw new IllegalArgumentException("電表讀數或照片格式不正確");
        return new JSONObject().put("id",UUID.randomUUID().toString()).put("leaseId",lease.getString("id")).put("unitId",lease.getString("unitId")).put("month",month).put("date",date).put("previous",previous).put("current",current).put("file",file).put("note",note.trim()).put("void",false);
    }
    public static JSONObject latest(JSONArray rows,String lease,String month) throws JSONException {
        JSONObject result=null;for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(!r.getBoolean("void")&&r.getString("leaseId").equals(lease)&&r.getString("month").equals(month))result=r;}return result;
    }
    public static Set<String> files(JSONArray rows) throws JSONException {
        Set<String> files=new LinkedHashSet<>();for(int i=0;i<rows.length();i++){String file=rows.getJSONObject(i).getString("file");if(!file.isEmpty()){if(!photoName(file))throw new IllegalArgumentException("照片檔名無效");files.add(file);}}return files;
    }
    public static void validate(JSONObject backup) throws JSONException {
        JSONArray rows=backup.getJSONArray("meterReadings"),leases=backup.getJSONArray("leases");Set<String> ids=new HashSet<>(),files=new HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i),lease=null;for(int j=0;j<leases.length();j++)if(leases.getJSONObject(j).getString("id").equals(r.getString("leaseId")))lease=leases.getJSONObject(j);
            if(lease==null||r.getString("id").isEmpty()||!ids.add(r.getString("id"))||!r.getString("unitId").equals(lease.getString("unitId")))throw new IllegalArgumentException("抄表租約或單位錯誤");
            r.getBoolean("void");reading(lease,r.getString("month"),r.getString("date"),Ledger.money(r,"previous"),Ledger.money(r,"current"),r.getString("file"),r.getString("note"));if(!r.getString("file").isEmpty()&&!files.add(r.getString("file")))throw new IllegalArgumentException("照片參照重複");
        }
        JSONArray invoices=backup.optJSONArray("invoices");if(invoices!=null)for(int i=0;i<invoices.length();i++){JSONObject bill=invoices.getJSONObject(i);if(!bill.has("meterReadingId"))continue;JSONObject reading=null;for(int j=0;j<rows.length();j++)if(bill.getString("meterReadingId").equals(rows.getJSONObject(j).getString("id")))reading=rows.getJSONObject(j);if(reading==null||!bill.getString("kind").equals("水電")||!bill.getString("leaseId").equals(reading.getString("leaseId"))||!bill.getString("month").equals(reading.getString("month"))||bill.getLong("previous")!=reading.getLong("previous")||bill.getLong("current")!=reading.getLong("current"))throw new IllegalArgumentException("帳單與抄表依據不一致");}
    }
}
