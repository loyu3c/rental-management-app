package com.loyu3c.rental;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** System font PDF; wrapped text flows across pages without truncating descriptions. */
final class LedgerPdf implements AutoCloseable {
    private final PdfDocument document=new PdfDocument();private PdfDocument.Page page;private Canvas canvas;private float y;private int number;
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);private final JSONObject report;
    private static final int WIDTH=595,HEIGHT=842,LEFT=30,RIGHT=565,BOTTOM=800;
    private LedgerPdf(JSONObject report){this.report=report;paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(10);paint.setColor(Color.rgb(77,57,39));}
    private void finish(){if(page!=null){paint.setTextSize(9);canvas.drawText("第 "+number+" 頁 · "+report.optString("createdAt"),LEFT,824,paint);document.finishPage(page);page=null;}}
    private void next(){finish();page=document.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,++number).create());canvas=page.getCanvas();paint.setTextSize(17);canvas.drawText("收支明細",LEFT,34,paint);paint.setTextSize(9);canvas.drawText("日期／類型／金額　｜　項目、分類、帳戶及分攤",LEFT,55,paint);canvas.drawLine(LEFT,62,RIGHT,62,paint);y=78;}
    private void paragraph(String text,int size,boolean bold){
        paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,RIGHT-LEFT).setIncludePad(false).setLineSpacing(3,1).build();
        int line=0;while(line<layout.getLineCount()){
            if(y+size+8>BOTTOM)next();paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            int start=line;float available=BOTTOM-y;while(line<layout.getLineCount()&&layout.getLineBottom(line)-layout.getLineTop(start)<=available)line++;
            if(line==start){next();continue;}float top=layout.getLineTop(start),height=layout.getLineBottom(line-1)-top;canvas.save();canvas.clipRect(LEFT,y,RIGHT,y+height);canvas.translate(LEFT,y-top);layout.draw(canvas);canvas.restore();y+=height+5;
        }
    }
    static void write(OutputStream out,JSONObject report,File photos,boolean includePhotos)throws Exception{
        try(LedgerPdf pdf=new LedgerPdf(report)){pdf.next();pdf.paragraph(report.getString("conditions"),10,false);pdf.paragraph("一般收入 $"+money(report.getLong("income"))+"　支出 $"+money(report.getLong("expense")),12,true);pdf.paragraph("僅一般收入與支出；撤銷不計合計，不含租金收款、押金及轉帳。",9,false);
            JSONArray items=report.getJSONArray("items");if(items.length()==0)pdf.paragraph("沒有符合匯出條件的紀錄。",11,false);
            for(int i=0;i<items.length();i++){JSONObject r=items.getJSONObject(i);if(pdf.y+65>BOTTOM)pdf.next();pdf.paragraph((i+1)+". "+r.getString("date")+" · "+r.getString("kind")+" $"+money(r.getLong("amount"))+(r.getBoolean("void")?" 【已撤銷】":""),11,true);pdf.paragraph(r.getString("description"),11,false);pdf.paragraph(r.getString("category")+" · "+r.getString("account"),10,false);if(!r.getString("splits").isEmpty())pdf.paragraph("分攤："+r.getString("splits"),10,false);if(r.getBoolean("void"))pdf.paragraph("撤銷："+r.getString("voidReason")+" · "+r.getString("voidedAt"),9,false);pdf.y+=6;}
            if(includePhotos)for(int i=0;i<items.length();i++){JSONObject r=items.getJSONObject(i);JSONArray files=ExpensePhotos.photos(r.getJSONObject("record"));for(int j=0;j<files.length();j++){pdf.next();pdf.paragraph("照片附件 · 第 "+(i+1)+" 筆 · 第 "+(j+1)+" 張",12,true);pdf.paragraph(r.getString("date")+" · $"+money(r.getLong("amount"))+" · "+(r.getString("description").length()>120?r.getString("description").substring(0,120)+"…（全文見明細）":r.getString("description")),10,false);String name=files.getString(j);if(!ExpensePhotos.photoName(name))throw new IOException("照片檔名無效");File file=new File(photos,name);BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1400)options.inSampleSize*=2;options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);if(bitmap==null){pdf.paragraph("照片無法讀取："+name,10,false);continue;}try{float width=RIGHT-LEFT,height=BOTTOM-pdf.y,scale=Math.min(width/bitmap.getWidth(),height/bitmap.getHeight());pdf.canvas.drawBitmap(bitmap,null,new RectF(LEFT,pdf.y,LEFT+bitmap.getWidth()*scale,pdf.y+bitmap.getHeight()*scale),pdf.paint);}finally{bitmap.recycle();}}}
            pdf.finish();pdf.document.writeTo(out);
        }
    }
    private static String money(long n){return String.format(Locale.US,"%,d",n);}
    public void close(){if(page!=null)document.finishPage(page);document.close();}
}
