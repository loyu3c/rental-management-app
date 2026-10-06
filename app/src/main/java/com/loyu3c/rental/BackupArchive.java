package com.loyu3c.rental;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class BackupArchive {
    public static final long PHOTO_LIMIT=25000000L, TOTAL_LIMIT=300000000L;
    private static long copy(InputStream in,OutputStream out,long limit) throws IOException {
        byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("備份或照片超出大小限制");out.write(buffer,0,n);}return total;
    }
    public static void write(OutputStream output,JSONObject backup,File photos) throws Exception {
        byte[] json=backup.toString(2).getBytes(StandardCharsets.UTF_8);if(json.length>10000000)throw new IOException("帳務備份過大");Set<String> files=MeterReadings.files(backup.getJSONArray("meterReadings"));files.addAll(LeaseFiles.files(backup));files.addAll(ExpensePhotos.files(backup));if(files.size()>2999)throw new IOException("照片數量超出本版上限");long size=json.length;for(String name:files){File f=new File(photos,name);if(!f.isFile()||f.length()==0||f.length()>PHOTO_LIMIT)throw new IOException("照片缺少或過大："+name);if(LeaseFiles.pdfName(name))LeaseFiles.validatePdf(f);size+=f.length();}if(size>TOTAL_LIMIT)throw new IOException("照片總量超出本版備份上限");
        try(ZipOutputStream zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry("catalog.json"));zip.write(json);zip.closeEntry();for(String name:files){zip.putNextEntry(new ZipEntry((LeaseFiles.pdfName(name)?"contracts/":ExpensePhotos.photoName(name)?"expenses/":"photos/")+name));try(InputStream in=new FileInputStream(new File(photos,name))){copy(in,zip,PHOTO_LIMIT);}zip.closeEntry();}}
    }
    /** Extracts to an isolated cache directory only. Caller validates before replacing live data. */
    public static JSONObject read(InputStream input,File staging) throws Exception {
        PushbackInputStream in=new PushbackInputStream(input,4);byte[] head=new byte[4];int count=0,n;while(count<4&&(n=in.read(head,count,4-count))>0)count+=n;if(count>0)in.unread(head,0,count);
        if(count<2||head[0]!='P'||head[1]!='K'){ByteArrayOutputStream json=new ByteArrayOutputStream();copy(in,json,10000000);JSONObject b=new JSONObject(json.toString("UTF-8"));if(!ExpensePhotos.files(b).isEmpty()||!LeaseFiles.files(b).isEmpty()||(b.optInt("version")>=6&&!MeterReadings.files(b.getJSONArray("meterReadings")).isEmpty()))throw new IOException("含照片備份請使用完整ZIP");return b;}
        if(!staging.isDirectory()&&!staging.mkdirs())throw new IOException("無法準備還原");JSONObject backup=null;Set<String> names=new HashSet<>();long total=0;
        try(ZipInputStream zip=new ZipInputStream(in)){ZipEntry entry;while((entry=zip.getNextEntry())!=null){String name=entry.getName();if(entry.isDirectory()||!names.add(name)||names.size()>3000)throw new IOException("備份項目重複或無效");
            if(name.equals("catalog.json")){ByteArrayOutputStream json=new ByteArrayOutputStream();total+=copy(zip,json,10000000);backup=new JSONObject(json.toString("UTF-8"));}
            else if(name.startsWith("photos/")&&MeterReadings.photoName(name.substring(7))){try(OutputStream out=new FileOutputStream(new File(staging,name.substring(7)))){total+=copy(zip,out,PHOTO_LIMIT);}}
            else if(name.startsWith("contracts/")&&LeaseFiles.pdfName(name.substring(10))){try(OutputStream out=new FileOutputStream(new File(staging,name.substring(10)))){total+=copy(zip,out,PHOTO_LIMIT);}}
            else if(name.startsWith("expenses/")&&ExpensePhotos.photoName(name.substring(9))){try(OutputStream out=new FileOutputStream(new File(staging,name.substring(9)))){total+=copy(zip,out,PHOTO_LIMIT);}}
            else throw new IOException("備份含不支援項目");if(total>TOTAL_LIMIT)throw new IOException("備份過大");zip.closeEntry();}}
        if(backup==null)throw new IOException("缺少帳務資料");JSONArray rows=backup.optJSONArray("meterReadings");if(rows==null)rows=new JSONArray();Set<String> files=MeterReadings.files(rows);files.addAll(LeaseFiles.files(backup));files.addAll(ExpensePhotos.files(backup));
        for(String name:files)if(!new File(staging,name).isFile()||new File(staging,name).length()==0)throw new IOException("備份缺少照片");for(String name:LeaseFiles.files(backup))LeaseFiles.validatePdf(new File(staging,name));if(names.size()!=files.size()+1)throw new IOException("備份有未關聯照片");return backup;
    }
}


