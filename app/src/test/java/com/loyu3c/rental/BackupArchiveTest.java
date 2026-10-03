package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;

public class BackupArchiveTest {
    private final String name="12345678-1234-1234-1234-123456789abc.jpg";
    private JSONObject catalog() throws Exception {return new JSONObject().put("version",6).put("meterReadings",new JSONArray().put(new JSONObject().put("file",name)));}
    private byte[] archive(String path,boolean catalog) throws Exception {ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){if(catalog){zip.putNextEntry(new ZipEntry("catalog.json"));zip.write(catalog().toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}if(path!=null){zip.putNextEntry(new ZipEntry(path));zip.write(new byte[]{1,2,3});zip.closeEntry();}}return bytes.toByteArray();}
    @Test public void archiveRoundTripKeepsPhotoBytesAndMetadata() throws Exception {
        File photos=Files.createTempDirectory("photos").toFile(),stage=Files.createTempDirectory("stage").toFile();Files.write(new File(photos,name).toPath(),new byte[]{1,2,3});ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.write(out,catalog(),photos);JSONObject restored=BackupArchive.read(new ByteArrayInputStream(out.toByteArray()),stage);assertEquals(name,restored.getJSONArray("meterReadings").getJSONObject(0).getString("file"));assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(new File(stage,name).toPath()));
    }
    @Test public void missingPhotoStopsRestoreBeforeReplacingCatalog() throws Exception {
        try{BackupArchive.read(new ByteArrayInputStream(archive(null,true)),Files.createTempDirectory("stage").toFile());fail();}catch(IOException expected){}
    }
    @Test public void traversalAndUnknownEntriesRejected() throws Exception {
        for(String path:new String[]{"photos/../outside.jpg","../outside.jpg","unrelated.txt"})try{BackupArchive.read(new ByteArrayInputStream(archive(path,true)),Files.createTempDirectory("stage").toFile());fail();}catch(IOException expected){}
    }
    @Test public void referencedPhotoMustExistToExport() throws Exception {
        try{BackupArchive.write(new ByteArrayOutputStream(),catalog(),Files.createTempDirectory("photos").toFile());fail();}catch(IOException expected){}
    }
    @Test public void legacyJsonSupportedButPhotoReferencesRequireZip() throws Exception {
        byte[] old=new JSONObject().put("version",5).toString().getBytes(StandardCharsets.UTF_8);assertEquals(5,BackupArchive.read(new ByteArrayInputStream(old),Files.createTempDirectory("stage").toFile()).getInt("version"));
        try{BackupArchive.read(new ByteArrayInputStream(catalog().toString().getBytes(StandardCharsets.UTF_8)),Files.createTempDirectory("stage").toFile());fail();}catch(IOException expected){}
    }
    @Test public void readingOnlyBackupNeedsNoPhotoFiles() throws Exception {
        JSONObject b=new JSONObject().put("version",6).put("meterReadings",new JSONArray().put(new JSONObject().put("file","")));ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.write(out,b,Files.createTempDirectory("photos").toFile());assertEquals("",BackupArchive.read(new ByteArrayInputStream(out.toByteArray()),Files.createTempDirectory("stage").toFile()).getJSONArray("meterReadings").getJSONObject(0).getString("file"));
    }
}
