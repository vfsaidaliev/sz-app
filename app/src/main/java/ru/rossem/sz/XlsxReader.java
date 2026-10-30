package ru.rossem.sz;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.SAXParserFactory;

/* Чтение первого листа XLSX в JSON-массив строк: [["A1","B1",...],["A2",...],...].
   Значения передаются как есть (числа и даты — в виде чисел Excel), разбор колонок — в index.html. */
public class XlsxReader {

    public static String toJson(InputStream in) throws Exception {
        Map<String, byte[]> files = unzip(in);
        if (!files.containsKey("xl/workbook.xml")) throw new IOException("это не файл Excel в формате .xlsx. Если файл в формате .xls, откройте его в Excel и сохраните как .xlsx");
        List<String> shared = files.containsKey("xl/sharedStrings.xml") ? sharedStrings(files.get("xl/sharedStrings.xml")) : new ArrayList<String>();
        byte[] sheet = files.get(firstSheet(files));
        if (sheet == null) throw new IOException("В книге не найден лист");
        return rows(sheet, shared);
    }

    private static Map<String, byte[]> unzip(InputStream in) throws IOException {
        Map<String, byte[]> m = new HashMap<String, byte[]>();
        ZipInputStream z = new ZipInputStream(in);
        ZipEntry e;
        byte[] buf = new byte[65536];
        while ((e = z.getNextEntry()) != null) {
            String n = e.getName();
            if (!n.startsWith("xl/") || !n.endsWith(".xml") && !n.endsWith(".rels")) continue;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int k;
            while ((k = z.read(buf)) > 0) out.write(buf, 0, k);
            m.put(n, out.toByteArray());
        }
        z.close();
        return m;
    }

    private static void parse(byte[] xml, DefaultHandler h) throws Exception {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(false);
        f.newSAXParser().parse(new ByteArrayInputStream(xml), h);
    }

    private static String local(String q) {
        int i = q.indexOf(':');
        return i < 0 ? q : q.substring(i + 1);
    }

    /* путь к первому листу по workbook.xml и его связям */
    private static String firstSheet(Map<String, byte[]> files) throws Exception {
        final String[] rid = {null};
        parse(files.get("xl/workbook.xml"), new DefaultHandler() {
            public void startElement(String u, String l, String q, Attributes a) {
                if (rid[0] == null && local(q).equals("sheet")) rid[0] = a.getValue("r:id");
            }
        });
        final String[] target = {null};
        byte[] rels = files.get("xl/_rels/workbook.xml.rels");
        if (rid[0] != null && rels != null) {
            parse(rels, new DefaultHandler() {
                public void startElement(String u, String l, String q, Attributes a) {
                    if (local(q).equals("Relationship") && rid[0].equals(a.getValue("Id"))) target[0] = a.getValue("Target");
                }
            });
        }
        if (target[0] == null) return "xl/worksheets/sheet1.xml";
        String t = target[0];
        return t.startsWith("/") ? t.substring(1) : "xl/" + t;
    }

    private static List<String> sharedStrings(byte[] xml) throws Exception {
        final List<String> list = new ArrayList<String>();
        parse(xml, new DefaultHandler() {
            StringBuilder si, t;
            boolean rph;
            public void startElement(String u, String l, String q, Attributes a) {
                String n = local(q);
                if (n.equals("si")) si = new StringBuilder();
                else if (n.equals("rPh")) rph = true;
                else if (n.equals("t") && si != null && !rph) t = new StringBuilder();
            }
            public void characters(char[] c, int s, int len) {
                if (t != null) t.append(c, s, len);
            }
            public void endElement(String u, String l, String q) {
                String n = local(q);
                if (n.equals("t") && t != null) { si.append(t); t = null; }
                else if (n.equals("rPh")) rph = false;
                else if (n.equals("si")) { list.add(si.toString()); si = null; }
            }
        });
        return list;
    }

    private static int col(String ref) {
        int c = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch < 'A' || ch > 'Z') break;
            c = c * 26 + (ch - 'A' + 1);
        }
        return c - 1;
    }

    private static String rows(byte[] xml, final List<String> shared) throws Exception {
        final StringBuilder out = new StringBuilder("[");
        parse(xml, new DefaultHandler() {
            List<String> row;
            String type;
            int c, next;
            StringBuilder v;
            public void startElement(String u, String l, String q, Attributes a) {
                String n = local(q);
                if (n.equals("row")) { row = new ArrayList<String>(); next = 0; }
                else if (n.equals("c") && row != null) {
                    String r = a.getValue("r");
                    c = r != null ? col(r) : next;
                    type = a.getValue("t");
                    v = null;
                } else if ((n.equals("v") || n.equals("t")) && row != null) v = v == null ? new StringBuilder() : v;
            }
            public void characters(char[] ch, int s, int len) {
                if (v != null) v.append(ch, s, len);
            }
            public void endElement(String u, String l, String q) {
                String n = local(q);
                if (n.equals("c") && row != null) {
                    String s = v == null ? "" : v.toString();
                    if ("s".equals(type) && s.length() > 0) {
                        int i = Integer.parseInt(s.trim());
                        s = i < shared.size() ? shared.get(i) : "";
                    }
                    while (row.size() < c) row.add("");
                    row.add(s);
                    next = c + 1;
                    v = null;
                } else if (n.equals("row") && row != null) {
                    if (out.length() > 1) out.append(',');
                    out.append('[');
                    for (int i = 0; i < row.size(); i++) {
                        if (i > 0) out.append(',');
                        json(out, row.get(i));
                    }
                    out.append(']');
                    row = null;
                }
            }
        });
        return out.append(']').toString();
    }

    private static void json(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
            else b.append(ch);
        }
        b.append('"');
    }
}
