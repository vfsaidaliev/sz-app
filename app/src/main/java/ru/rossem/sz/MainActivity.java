package ru.rossem.sz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.content.pm.ResolveInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_BASE = 1;
    private static final int PHOTO_CAM = 2;
    private static final int PHOTO_GAL = 3;
    private WebView web;
    private String pendingBase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = night();
        getWindow().setStatusBarColor(Color.parseColor(dark ? "#060C18" : "#EEF3FB"));
        getWindow().setNavigationBarColor(Color.parseColor(dark ? "#09111F" : "#F7F2E7"));
        if (!dark) {
            int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(dark ? "#060C18" : "#EEF3FB"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("back()", new ValueCallback<String>() {
            public void onReceiveValue(String handled) {
                if (!"true".equals(handled)) finish();
            }
        });
    }

    private File baseFile() {
        return new File(getFilesDir(), "base.json");
    }

    private void js(final String code) {
        runOnUiThread(new Runnable() {
            public void run() {
                web.evaluateJavascript(code, null);
            }
        });
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception e) {
        } finally {
            if (c != null) c.close();
        }
        return "";
    }

    private File camFile() {
        File d = new File(getCacheDir(), "cam");
        if (!d.exists()) d.mkdirs();
        return new File(d, "cam.jpg");
    }

    /* снятые или выбранные фото обрабатываются в фоне; id передаются в index.html через photoAdded([...]) */
    private void addPhotos(final List<Uri> uris, final boolean fromCam) {
        new Thread(new Runnable() {
            public void run() {
                JSONArray ids = new JSONArray();
                String err = null;
                for (Uri u : uris) {
                    try {
                        InputStream in = fromCam ? new FileInputStream(camFile()) : getContentResolver().openInputStream(u);
                        ids.put(Photos.add(MainActivity.this, in));
                    } catch (Throwable e) {
                        err = "Не удалось добавить фото: " + e.getMessage();
                    }
                }
                if (fromCam) camFile().delete();
                js("photoAdded(" + ids.toString() + (err != null ? "," + JSONObject.quote(err) : "") + ")");
            }
        }).start();
    }

    /* выбранный файл Excel читается в фоне; результат забирает index.html через baseTake() */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PHOTO_CAM || requestCode == PHOTO_GAL) {
            if (resultCode != RESULT_OK) return;
            List<Uri> uris = new ArrayList<Uri>();
            if (requestCode == PHOTO_CAM) uris.add(null);
            else if (data != null && data.getClipData() != null)
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            else if (data != null && data.getData() != null) uris.add(data.getData());
            if (!uris.isEmpty()) addPhotos(uris, requestCode == PHOTO_CAM);
            return;
        }
        if (requestCode != PICK_BASE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            js("baseError('')");
            return;
        }
        final Uri uri = data.getData();
        new Thread(new Runnable() {
            public void run() {
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    String json = XlsxReader.toJson(in);
                    in.close();
                    pendingBase = json;
                    js("baseParsed(" + JSONObject.quote(displayName(uri)) + ")");
                } catch (Throwable e) {
                    js("baseError(" + JSONObject.quote("Не удалось прочитать файл: " + e.getMessage()) + ")");
                }
            }
        }).start();
    }

    private boolean night() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /* одно вложение — ACTION_SEND, несколько (СЗ и фото) — ACTION_SEND_MULTIPLE */
    private Intent mailIntent(String to, String subject, String body, ArrayList<Uri> uris) {
        Intent send = new Intent(uris.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        send.setType(uris.size() > 1 ? "*/*" : DocProvider.MIME);
        send.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, body);
        if (uris.size() > 1) send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        else send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        ClipData clip = ClipData.newRawUri("", uris.get(0));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
        send.setClipData(clip);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return send;
    }

    private void chooseMail(String to, String subject, String body, ArrayList<Uri> uris) {
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        final List<Intent> intents = new ArrayList<Intent>();
        final List<CharSequence> labels = new ArrayList<CharSequence>();
        List<String> seen = new ArrayList<String>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (seen.contains(pkg)) continue;
            seen.add(pkg);
            Intent send = mailIntent(to, subject, body, uris);
            send.setPackage(pkg);
            if (send.resolveActivity(pm) == null) continue;
            for (Uri u : uris) grantUriPermission(pkg, u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intents.add(send);
            labels.add(ri.loadLabel(pm));
        }
        if (intents.isEmpty()) {
            try {
                startActivity(Intent.createChooser(mailIntent(to, subject, body, uris), "Отправить СЗ"));
            } catch (Exception e) {
                toast("Не найдено почтовое приложение");
            }
            return;
        }
        if (intents.size() == 1) {
            startActivity(intents.get(0));
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Через какую почту отправить")
            .setItems(labels.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    try {
                        startActivity(intents.get(which));
                    } catch (Exception e) {
                        toast("Не удалось открыть почту");
                    }
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    class Bridge {
        @JavascriptInterface
        public boolean night() {
            return MainActivity.this.night();
        }

        @JavascriptInterface
        public void share(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Отправить СЗ"));
        }

        @JavascriptInterface
        public void pickBase() {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, PICK_BASE);
        }

        @JavascriptInterface
        public String baseTake() {
            String s = pendingBase;
            pendingBase = null;
            return s == null ? "" : s;
        }

        @JavascriptInterface
        public boolean baseSave(String json) {
            try {
                FileOutputStream out = new FileOutputStream(baseFile());
                out.write(json.getBytes("UTF-8"));
                out.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String baseLoad() {
            File f = baseFile();
            if (!f.exists()) return "";
            try {
                byte[] b = new byte[(int) f.length()];
                FileInputStream in = new FileInputStream(f);
                int n = 0;
                while (n < b.length) {
                    int k = in.read(b, n, b.length - n);
                    if (k < 0) break;
                    n += k;
                }
                in.close();
                return new String(b, 0, n, "UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void baseDelete() {
            baseFile().delete();
        }

        @JavascriptInterface
        public void copy(final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("СЗ", text));
                    Toast.makeText(MainActivity.this, "Текст скопирован", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void email(String to, String subject, String body) {
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("mailto:"));
            i.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
            i.putExtra(Intent.EXTRA_SUBJECT, subject);
            i.putExtra(Intent.EXTRA_TEXT, body);
            try {
                startActivity(Intent.createChooser(i, "Отправить на почту"));
            } catch (Exception e) {
                toast("Не найдено почтовое приложение");
            }
        }

        @JavascriptInterface
        public void photoCamera() {
            Uri uri = Uri.parse("content://" + DocProvider.AUTHORITY + "/cam/cam.jpg");
            camFile().delete();
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.setClipData(ClipData.newRawUri("", uri));
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(i, PHOTO_CAM);
            } catch (Exception e) {
                toast("Не найдено приложение камеры");
            }
        }

        @JavascriptInterface
        public void photoGallery() {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                startActivityForResult(i, PHOTO_GAL);
            } catch (Exception e) {
                toast("Не найдена галерея");
            }
        }

        @JavascriptInterface
        public String photoThumb(String id) {
            return Photos.thumb(MainActivity.this, id);
        }

        @JavascriptInterface
        public void photoDelete(String id) {
            File f = Photos.file(MainActivity.this, id);
            if (f != null) f.delete();
        }

        /* удалить фото, которых нет в списке записки (после очистки или сбоя) */
        @JavascriptInterface
        public void photoKeep(String json) {
            try {
                JSONArray a = new JSONArray(json);
                List<String> keep = new ArrayList<String>();
                for (int i = 0; i < a.length(); i++) keep.add(a.getString(i));
                File[] all = Photos.dir(MainActivity.this).listFiles();
                if (all != null) for (File f : all) if (!keep.contains(f.getName())) f.delete();
            } catch (Exception e) {
            }
        }

        @JavascriptInterface
        public void emailDocx(String to, String subject, String body, String fileName, String base64, String photosJson) {
            try {
                String name = new File(fileName).getName();
                File dir = new File(getCacheDir(), "docs");
                if (!dir.exists()) dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                File file = new File(dir, name);
                FileOutputStream out = new FileOutputStream(file);
                out.write(Base64.decode(base64, Base64.DEFAULT));
                out.close();

                final ArrayList<Uri> uris = new ArrayList<Uri>();
                uris.add(Uri.parse("content://" + DocProvider.AUTHORITY + "/" + Uri.encode(name)));
                /* фото — отдельными вложениями: «<имя СЗ>_фото_1.jpg» … */
                JSONArray ph = new JSONArray(photosJson == null || photosJson.isEmpty() ? "[]" : photosJson);
                String base = name.replaceAll("\\.docx$", "");
                for (int i = 0; i < ph.length(); i++) {
                    File src = Photos.file(MainActivity.this, ph.getString(i));
                    if (src == null || !src.exists()) continue;
                    String pn = base + "_фото_" + (i + 1) + ".jpg";
                    FileInputStream in = new FileInputStream(src);
                    FileOutputStream po = new FileOutputStream(new File(dir, pn));
                    byte[] buf = new byte[65536];
                    int k;
                    while ((k = in.read(buf)) > 0) po.write(buf, 0, k);
                    in.close();
                    po.close();
                    uris.add(Uri.parse("content://" + DocProvider.AUTHORITY + "/" + Uri.encode(pn)));
                }
                final String fTo = to, fSubject = subject, fBody = body;
                runOnUiThread(new Runnable() {
                    public void run() {
                        chooseMail(fTo, fSubject, fBody, uris);
                    }
                });
            } catch (Exception e) {
                toast("Не удалось сформировать файл: " + e.getMessage());
            }
        }
    }
}
