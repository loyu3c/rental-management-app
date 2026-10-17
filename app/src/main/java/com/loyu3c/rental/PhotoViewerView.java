package com.loyu3c.rental;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.io.File;
import java.io.IOException;

/** Read-only, bounded-memory image viewer. Never launches an external app or alters files. */
final class PhotoViewerView extends View {
    private Bitmap bitmap;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector pinch;
    private final GestureDetector gestures;
    private float fit=1,zoom=1,left,top;

    PhotoViewerView(Context context,File file)throws IOException {
        super(context);
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),options);
        if(options.outWidth<=0||options.outHeight<=0||file.length()>BackupArchive.PHOTO_LIMIT)throw new IOException("無法讀取照片，請確認照片檔案或從備份還原");
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;
        bitmap=BitmapFactory.decodeFile(file.getPath(),options);
        if(bitmap==null)throw new IOException("無法讀取照片");
        setBackgroundColor(Color.rgb(35,32,29));setContentDescription("照片大圖，可雙指縮放、拖曳及雙點還原");
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){
                float next=Math.max(1,Math.min(5,zoom*detector.getScaleFactor()));
                scaleAt(next,detector.getFocusX(),detector.getFocusY());return true;
            }
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapUp(MotionEvent e){performClick();return true;}
            @Override public boolean onDoubleTap(MotionEvent e){scaleAt(zoom>1.05f?1:2.5f,e.getX(),e.getY());return true;}
            @Override public boolean onScroll(MotionEvent first,MotionEvent current,float dx,float dy){
                if(!pinch.isInProgress()&&zoom>1){left-=dx;top-=dy;bound();invalidate();}return true;
            }
        });
    }
    private void scaleAt(float next,float x,float y){float ratio=next/zoom;left=x-(x-left)*ratio;top=y-(y-top)*ratio;zoom=next;bound();invalidate();}
    private void bound(){if(bitmap==null)return;float width=bitmap.getWidth()*fit*zoom,height=bitmap.getHeight()*fit*zoom;
        left=width<=getWidth()?(getWidth()-width)/2:Math.max(getWidth()-width,Math.min(0,left));
        top=height<=getHeight()?(getHeight()-height)/2:Math.max(getHeight()-height,Math.min(0,top));}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(bitmap!=null){fit=Math.min((float)w/bitmap.getWidth(),(float)h/bitmap.getHeight());zoom=1;bound();}}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(bitmap!=null){canvas.save();canvas.translate(left,top);canvas.scale(fit*zoom,fit*zoom);canvas.drawBitmap(bitmap,0,0,paint);canvas.restore();}}
    @Override public boolean onTouchEvent(MotionEvent event){pinch.onTouchEvent(event);gestures.onTouchEvent(event);return true;}
    @Override public boolean performClick(){super.performClick();return true;}
    void release(){Bitmap old=bitmap;bitmap=null;if(old!=null)old.recycle();}
}
