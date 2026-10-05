package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class LeaseFilesTest {
    private final String name="12345678-1234-1234-1234-123456789abc.pdf";
    private JSONObject catalog() throws Exception {return new JSONObject().put("version",9).put("meterReadings",new JSONArray()).put("leases",new JSONArray().put(new JSONObject().put("initialMeter",1234).put("start","2026-10-15").put("contractFile",name).put("contractName","合約.pdf")));}
    @Test public void pdfAndInitialReadingRoundTripInCompleteBackup() throws Exception {
        File files=Files.createTempDirectory("contracts").toFile(),stage=Files.createTempDirectory("restore").toFile();byte[] pdf="%PDF-1.7\ncontract".getBytes(StandardCharsets.UTF_8);Files.write(new File(files,name).toPath(),pdf);ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.write(out,catalog(),files);JSONObject restored=BackupArchive.read(new ByteArrayInputStream(out.toByteArray()),stage);assertArrayEquals(pdf,Files.readAllBytes(new File(stage,name).toPath()));assertEquals(1234,restored.getJSONArray("leases").getJSONObject(0).getLong("initialMeter"));assertEquals("合約.pdf",restored.getJSONArray("leases").getJSONObject(0).getString("contractName"));
    }
    @Test public void missingAndInvalidPdfPreventExport() throws Exception {
        File dir=Files.createTempDirectory("contracts").toFile();try{BackupArchive.write(new ByteArrayOutputStream(),catalog(),dir);fail();}catch(IOException expected){}Files.write(new File(dir,name).toPath(),"not a PDF".getBytes(StandardCharsets.UTF_8));try{BackupArchive.write(new ByteArrayOutputStream(),catalog(),dir);fail();}catch(IOException expected){}
    }
    @Test public void attachmentRequiresZipAndTraversalIsRejected() throws Exception {
        try{BackupArchive.read(new ByteArrayInputStream(catalog().toString().getBytes(StandardCharsets.UTF_8)),Files.createTempDirectory("restore").toFile());fail();}catch(IOException expected){}JSONObject b=catalog();b.getJSONArray("leases").getJSONObject(0).put("contractFile","../outside.pdf");try{LeaseFiles.files(b);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void initialReadingOnlyAppliesToStartingMonthAndLease() throws Exception {
        JSONObject lease=catalog().getJSONArray("leases").getJSONObject(0);assertEquals("1234",LeaseFiles.initialReading(lease,"2026-10"));assertEquals("",LeaseFiles.initialReading(lease,"2026-11"));assertEquals("",LeaseFiles.initialReading(new JSONObject().put("start","2026-10-15"),"2026-10"));lease.put("initialMeter",0);assertEquals("0",LeaseFiles.initialReading(lease,"2026-10"));
    }
}
