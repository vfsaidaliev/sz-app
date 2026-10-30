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
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_BASE = 1;
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

    /* выбранный файл Excel читается в фоне; результат забирает index.html через baseTake() */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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

    private Intent mailIntent(String to, String subject, String body, String name, Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(DocProvider.MIME);
        send.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, body);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri(name, uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return send;
    }

    private void chooseMail(String to, String subject, String body, String name, Uri uri) {
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
            Intent send = mailIntent(to, subject, body, name, uri);
            send.setPackage(pkg);
            if (send.resolveActivity(pm) == null) continue;
            grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intents.add(send);
            labels.add(ri.loadLabel(pm));
        }
        if (intents.isEmpty()) {
            try {
                startActivity(Intent.createChooser(mailIntent(to, subject, body, name, uri), "Отправить СЗ"));
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
        public void emailDocx(String to, String subject, String body, String fileName, String base64) {
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

                final Uri uri = Uri.parse("content://" + DocProvider.AUTHORITY + "/" + Uri.encode(name));
                final String fTo = to, fSubject = subject, fBody = body, fName = name;
                runOnUiThread(new Runnable() {
                    public void run() {
                        chooseMail(fTo, fSubject, fBody, fName, uri);
                    }
                });
            } catch (Exception e) {
                toast("Не удалось сформировать файл: " + e.getMessage());
            }
        }
    }
}
