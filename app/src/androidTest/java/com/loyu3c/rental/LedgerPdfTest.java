package com.loyu3c.rental;
import android.graphics.*;import android.graphics.pdf.PdfRenderer;import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;import org.junit.Test;import org.junit.runner.RunWith;import java.io.*;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class LedgerPdfTest {
    @Test public void chineseLongTextAndPhotoAppendixProduceReadablePages()throws Exception{
        File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"ledger-pdf-test");dir.mkdirs();String photo="exp-12345678-1234-1234-1234-123456789012.jpg";File image=new File(dir,photo);Bitmap source=Bitmap.createBitmap(100,160,Bitmap.Config.ARGB_8888);source.eraseColor(Color.BLUE);try(OutputStream out=new FileOutputStream(image)){assertTrue(source.compress(Bitmap.CompressFormat.JPEG,85,out));}source.recycle();
        StringBuilder text=new StringBuilder();for(int i=0;i<1500;i++)text.append("中文長明細測試，修繕水電項目。\n");
        JSONObject row=new JSONObject().put("date","2026-11-28").put("kind","支出").put("description",text.toString()).put("amount",9015).put("category","修繕").put("account","農會").put("splits","不動產1621 $9,015").put("void",false).put("record",new JSONObject().put("photos",new JSONArray().put(photo)));
        JSONObject report=new JSONObject().put("conditions","全部月份 · 不含撤銷").put("createdAt","2026-11-28 13:00").put("income",0).put("expense",9015).put("items",new JSONArray().put(row));File pdf=new File(dir,"report.pdf");
        try(OutputStream out=new FileOutputStream(pdf)){LedgerPdf.write(out,report,dir,true);}assertTrue(pdf.length()>1000);
        try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(descriptor)){assertTrue("Long text must paginate",renderer.getPageCount()>3);try(PdfRenderer.Page page=renderer.openPage(renderer.getPageCount()-1)){Bitmap bitmap=Bitmap.createBitmap(page.getWidth(),page.getHeight(),Bitmap.Config.ARGB_8888);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);boolean blue=false;for(int y=100;y<bitmap.getHeight();y+=10)for(int x=35;x<bitmap.getWidth();x+=10){int pixel=bitmap.getPixel(x,y);if(Color.blue(pixel)>180&&Color.red(pixel)<80)blue=true;}assertTrue("Last page must contain photo",blue);bitmap.recycle();}}
        image.delete();pdf.delete();dir.delete();
    }
}
