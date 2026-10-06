package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ExpensePhotosTest {
    private final String a="exp-12345678-1234-1234-1234-123456789abc.jpg",b="exp-22345678-1234-1234-1234-123456789abc.jpg";
    private JSONObject row(String photo) throws Exception {return Bookkeeping.expense("2026-10-06","A","修繕","材料",100,new JSONArray().put(new JSONObject().put("propertyId","P").put("amount",100))).put("photos",new JSONArray().put(photo));}
    private JSONObject backup() throws Exception {JSONObject old=row(a);JSONObject current=ExpenseEdits.replace(new JSONArray().put(old),old,row(b),"2026-10-06T00:00:00Z").getJSONObject(0);return new JSONObject().put("version",10).put("meterReadings",new JSONArray()).put("expenses",new JSONArray().put(current));}
    @Test public void editingRetainsHistoricalPhotosAndVoidingRetainsBothVersions() throws Exception {JSONObject backup=backup();assertEquals(new HashSet<>(Arrays.asList(a,b)),ExpensePhotos.files(backup));backup.getJSONArray("expenses").getJSONObject(0).put("void",true);assertEquals(2,ExpensePhotos.files(backup).size());}
    @Test public void completeBackupRestoresCurrentAndHistoricalBytes() throws Exception {File dir=Files.createTempDirectory("photos").toFile(),stage=Files.createTempDirectory("restore").toFile();Files.write(new File(dir,a).toPath(),new byte[]{1,2});Files.write(new File(dir,b).toPath(),new byte[]{3,4});ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.write(out,backup(),dir);JSONObject restored=BackupArchive.read(new ByteArrayInputStream(out.toByteArray()),stage);assertEquals(2,ExpensePhotos.files(restored).size());assertArrayEquals(new byte[]{1,2},Files.readAllBytes(new File(stage,a).toPath()));assertArrayEquals(new byte[]{3,4},Files.readAllBytes(new File(stage,b).toPath()));}
    @Test public void restorationRemapsAllSharedHistoryReferences() throws Exception {JSONObject backup=backup();Map<String,String> map=new HashMap<>();String x=a.replace("12345678","32345678"),y=b.replace("22345678","42345678");map.put(a,x);map.put(b,y);ExpensePhotos.remap(backup,map);assertEquals(new HashSet<>(Arrays.asList(x,y)),ExpensePhotos.files(backup));}
    @Test public void missingHistoricalFileAndJsonOnlyBackupAreRejected() throws Exception {File dir=Files.createTempDirectory("photos").toFile();Files.write(new File(dir,b).toPath(),new byte[]{3});try{BackupArchive.write(new ByteArrayOutputStream(),backup(),dir);fail();}catch(IOException expected){}try{BackupArchive.read(new ByteArrayInputStream(backup().toString().getBytes(StandardCharsets.UTF_8)),dir);fail();}catch(IOException expected){}}
    @Test public void legacyRowsHaveNoPhotosAndInvalidReferencesAreRejected() throws Exception {assertEquals(0,ExpensePhotos.photos(new JSONObject()).length());for(JSONArray names:new JSONArray[]{new JSONArray().put("../outside.jpg"),new JSONArray().put(a).put(a),new JSONArray().put(a).put(b).put(a.replace("12345678","32345678")).put(a.replace("12345678","42345678"))})try{ExpensePhotos.photos(new JSONObject().put("photos",names));fail();}catch(IllegalArgumentException expected){}}
}
