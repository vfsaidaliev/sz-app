package ru.rossem.sz;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class DocProvider extends ContentProvider {
    public static final String AUTHORITY = "ru.rossem.sz.docs";
    public static final String MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /* content://…/имя — вложения письма (cache/docs, только чтение);
       content://…/cam/имя — снимок камеры (cache/cam, камера записывает в него) */
    private boolean cam(Uri uri) {
        return uri.getPathSegments().size() > 1 && "cam".equals(uri.getPathSegments().get(0));
    }

    private File file(Uri uri) {
        String name = new File(uri.getLastPathSegment()).getName();
        File dir = new File(getContext().getCacheDir(), cam(uri) ? "cam" : "docs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (cam(uri)) return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
        if (!f.exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return uri.getLastPathSegment().toLowerCase().endsWith(".jpg") ? "image/jpeg" : MIME;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f = file(uri);
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
