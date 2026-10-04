package com.runbeat.app;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 기록을 앱 내부 저장소의 runs/ 폴더에 JSON 파일로 저장한다. (서버 없음) */
public final class RunStorage {
    private static final String TAG = "RunStorage";

    private RunStorage() {}

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "runs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static void save(Context c, RunRecord r) {
        try {
            byte[] data = r.toJson().toString().getBytes(StandardCharsets.UTF_8);
            // 임시 파일에 먼저 쓰고 이름을 바꿔서, 저장 도중 앱이 죽어도 기존 파일이 깨지지 않게 한다.
            File tmp = new File(dir(c), r.id + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
            }
            File dst = new File(dir(c), r.id + ".json");
            if (!tmp.renameTo(dst)) {
                try (OutputStream out = new FileOutputStream(dst)) {
                    out.write(data);
                }
                tmp.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "save failed", e);
        }
    }

    public static RunRecord load(Context c, String id) {
        return read(new File(dir(c), id + ".json"));
    }

    public static List<RunRecord> loadAll(Context c) {
        List<RunRecord> list = new ArrayList<>();
        File[] files = dir(c).listFiles();
        if (files == null) return list;
        for (File f : files) {
            if (!f.getName().endsWith(".json")) continue;
            RunRecord r = read(f);
            if (r != null) list.add(r);
        }
        // 최신 기록이 위로
        Collections.sort(list, (a, b) -> Long.compare(b.startTime, a.startTime));
        return list;
    }

    public static void delete(Context c, String id) {
        new File(dir(c), id + ".json").delete();
    }

    private static RunRecord read(File f) {
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            String json = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            return RunRecord.fromJson(new JSONObject(json));
        } catch (Exception e) {
            Log.e(TAG, "read failed: " + f, e);
            return null;
        }
    }
}
