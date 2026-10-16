package com.loyu3c.rental;
import org.json.*;
import java.util.*;
/** Attachment edits remain separate from the saved lease until the final save. */
public final class LeaseAttachments {
    public static JSONObject draft(JSONObject lease)throws JSONException{return new JSONObject().put("photo",lease==null?"":lease.optString("initialMeterPhoto")).put("pdf",lease==null?"":lease.optString("contractFile")).put("pdfName",lease==null?"":lease.optString("contractName")).put("owned",new JSONArray());}
    public static void select(JSONObject draft,String file,String displayName,boolean pdf)throws JSONException{
        if(pdf?!LeaseFiles.pdfName(file):!MeterReadings.photoName(file))throw new IllegalArgumentException("附件檔名無效");
        JSONArray owned=draft.getJSONArray("owned");for(int i=0;i<owned.length();i++)if(file.equals(owned.getString(i)))throw new IllegalArgumentException("附件已選取");owned.put(file);draft.put(pdf?"pdf":"photo",file);if(pdf)draft.put("pdfName",displayName);
    }
    public static void remove(JSONObject draft,boolean pdf)throws JSONException{draft.put(pdf?"pdf":"photo","");if(pdf)draft.put("pdfName","");}
    public static void apply(JSONObject draft,JSONObject lease)throws JSONException{
        String photo=draft.getString("photo"),pdf=draft.getString("pdf");if(!photo.isEmpty()&&!MeterReadings.photoName(photo)||!pdf.isEmpty()&&!LeaseFiles.pdfName(pdf))throw new IllegalArgumentException("附件檔名無效");
        if(photo.isEmpty())lease.remove("initialMeterPhoto");else lease.put("initialMeterPhoto",photo);
        if(pdf.isEmpty()){lease.remove("contractFile");lease.remove("contractName");}else lease.put("contractFile",pdf).put("contractName",draft.optString("pdfName","租賃合約.pdf"));
    }
    public static Set<String> discard(JSONObject draft,boolean saved)throws JSONException{
        Set<String> files=new LinkedHashSet<>();JSONArray owned=draft.getJSONArray("owned");for(int i=0;i<owned.length();i++){String file=owned.getString(i);if(!MeterReadings.photoName(file)&&!LeaseFiles.pdfName(file))throw new IllegalArgumentException("暫存附件檔名無效");if(!saved||!file.equals(draft.optString("photo"))&&!file.equals(draft.optString("pdf")))files.add(file);}return files;
    }
}
