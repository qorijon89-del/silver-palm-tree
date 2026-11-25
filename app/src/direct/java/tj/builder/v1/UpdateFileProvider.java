package tj.builder.v1;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
public final class UpdateFileProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode)||!"/update.apk".equals(uri.getPath()))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(),"verified-update.apk"),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri u){return "application/vnd.android.package-archive";}
    @Override public Cursor query(Uri u,String[] p,String s,String[] sa,String o){
        if(!"/update.apk".equals(u.getPath()))return null;
        android.database.MatrixCursor c=new android.database.MatrixCursor(new String[]{"_display_name","_size"});
        c.addRow(new Object[]{"Android-Builder-Update.apk",new File(getContext().getCacheDir(),"verified-update.apk").length()});
        return c;
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
