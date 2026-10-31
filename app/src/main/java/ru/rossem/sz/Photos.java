package ru.rossem.sz;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/* Фото-доказательства к СЗ: уменьшаются до MAX точек по длинной стороне с учётом поворота (EXIF),
   хранятся во внутренней памяти приложения (files/photos) до отправки или очистки записки. */
public class Photos {
    private static final int MAX = 1600;
    private static int seq;

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /* имя файла фото без путей; null — если имя недопустимо */
    public static File file(Context c, String id) {
        if (id == null || !id.matches("[A-Za-z0-9_]+\\.jpg")) return null;
        return new File(dir(c), id);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int k;
        while ((k = in.read(buf)) > 0) out.write(buf, 0, k);
        in.close();
        return out.toByteArray();
    }

    private static Bitmap decode(byte[] data, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private static Bitmap fit(Bitmap b, int max, int orientation) {
        float k = Math.min(1f, (float) max / Math.max(b.getWidth(), b.getHeight()));
        Matrix m = new Matrix();
        m.postScale(k, k);
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
        if (k == 1f && m.isIdentity()) return b;
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    /* сохранить фото из потока; возвращает его id (имя файла) */
    public static String add(Context c, InputStream in) throws IOException {
        byte[] data = readAll(in);
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            orientation = new ExifInterface(new ByteArrayInputStream(data)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
        }
        Bitmap b = decode(data, MAX);
        if (b == null) throw new IOException("файл не является изображением");
        b = fit(b, MAX, orientation);
        String id;
        synchronized (Photos.class) {
            id = "p" + System.currentTimeMillis() + "_" + (seq++) + ".jpg";
        }
        FileOutputStream out = new FileOutputStream(new File(dir(c), id));
        b.compress(Bitmap.CompressFormat.JPEG, 85, out);
        out.close();
        return id;
    }

    /* миниатюра для показа в приложении: data-URL или "" */
    public static String thumb(Context c, String id) {
        File f = file(c, id);
        if (f == null || !f.exists()) return "";
        try {
            Bitmap b = fit(decode(readAll(new java.io.FileInputStream(f)), 240), 240, ExifInterface.ORIENTATION_NORMAL);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 70, out);
            return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
