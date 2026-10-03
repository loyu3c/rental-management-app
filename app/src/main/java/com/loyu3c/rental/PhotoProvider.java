package com.loyu3c.rental;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Narrow per-file grants for camera capture and the user's selected image viewer. */
public class PhotoProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri) throws FileNotFoundException {
        String name=uri.getLastPathSegment();if(uri.getPathSegments().size()!=1||name==null||!MeterReadings.photoName(name))throw new FileNotFoundException("Invalid photo");
        return new File(getContext().getFilesDir(),"meter-photos/"+name);
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        File f=file(uri);int flags;if(mode.equals("r"))flags=ParcelFileDescriptor.MODE_READ_ONLY;else if(mode.equals("w")||mode.equals("wt")||mode.equals("rw"))flags=ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE;else throw new FileNotFoundException("Invalid mode");return ParcelFileDescriptor.open(f,flags);
    }
    public String getType(Uri uri){try{android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file(uri).getPath(),options);return options.outMimeType==null?"image/jpeg":options.outMimeType;}catch(Exception e){return "image/jpeg";}}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        try{File f=file(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=cols[i].equals(OpenableColumns.DISPLAY_NAME)?f.getName():cols[i].equals(OpenableColumns.SIZE)?f.length():null;cursor.addRow(row);return cursor;}catch(Exception e){return null;}
    }
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
