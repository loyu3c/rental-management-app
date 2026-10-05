package com.loyu3c.rental;
import org.json.*;
import java.io.*;
import java.time.YearMonth;
import java.util.*;

public final class LeaseFiles {
    public static boolean pdfName(String name){return name.matches("[0-9a-fA-F-]{36}\\.pdf");}
    public static Set<String> files(JSONObject backup) throws JSONException {
        Set<String> names=new LinkedHashSet<>();JSONArray leases=backup.optJSONArray("leases");if(leases==null)return names;
        for(int i=0;i<leases.length();i++){String name=leases.getJSONObject(i).optString("contractFile");if(!name.isEmpty()){if(!pdfName(name))throw new IllegalArgumentException("合約檔名無效");names.add(name);}}return names;
    }
    public static void validatePdf(File file) throws IOException {
        if(!file.isFile()||file.length()<5||file.length()>BackupArchive.PHOTO_LIMIT)throw new IOException("PDF合約缺少或超過25MB");
        try(InputStream in=new FileInputStream(file)){byte[] header=new byte[5];if(in.read(header)!=5||!Arrays.equals(header,new byte[]{37,80,68,70,45}))throw new IOException("合約必須是PDF檔案");}
    }
    public static String initialReading(JSONObject lease,String month){
        if(!lease.has("initialMeter")||lease.isNull("initialMeter"))return "";
        try{return YearMonth.parse(month).equals(YearMonth.from(java.time.LocalDate.parse(lease.getString("start"))))?Long.toString(lease.getLong("initialMeter")):"";}catch(Exception e){return "";}
    }
    public static void validate(JSONObject backup) throws JSONException {
        files(backup);JSONArray leases=backup.getJSONArray("leases");for(int i=0;i<leases.length();i++){JSONObject lease=leases.getJSONObject(i);if(lease.has("initialMeter")&&!lease.isNull("initialMeter"))Ledger.money(lease,"initialMeter");}
    }
}
