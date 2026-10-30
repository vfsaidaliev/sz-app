package ru.rossem.sz;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    class Bridge {
        @JavascriptInterface
        public void share(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Отправить СЗ"));
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

                Uri uri = Uri.parse("content://" + DocProvider.AUTHORITY + "/" + Uri.encode(name));
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(DocProvider.MIME);
                send.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
                send.putExtra(Intent.EXTRA_SUBJECT, subject);
                send.putExtra(Intent.EXTRA_TEXT, body);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.setClipData(ClipData.newRawUri(name, uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent mailOnly = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
                send.setSelector(mailOnly);
                try {
                    startActivity(Intent.createChooser(send, "Отправить на почту"));
                } catch (Exception e) {
                    send.setSelector(null);
                    startActivity(Intent.createChooser(send, "Отправить СЗ"));
                }
            } catch (Exception e) {
                toast("Не удалось сформировать файл: " + e.getMessage());
            }
        }
    }
}
