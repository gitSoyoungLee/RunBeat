package com.runbeat.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 이미지를 갤러리(사진 앱)의 "Pictures/RunBeat" 앨범에 저장한다.
 *
 * Android 10(API 29) 이상: MediaStore에 넣으면 권한 없이 저장 가능
 * Android 9 이하        : 저장소 쓰기 권한(WRITE_EXTERNAL_STORAGE)이 필요해서 화면 쪽에서 먼저 권한을 받는다
 */
public final class ImageSaver {
    private static final String TAG = "ImageSaver";
    public static final String ALBUM = "RunBeat";

    private ImageSaver() {}

    /** Android 9 이하에서만 권한이 필요 */
    public static boolean needsPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    /** "RunBeat_20261019_0730.png" 처럼 러닝 시작 시각으로 파일 이름을 만든다 */
    public static String fileName(long runStartMillis) {
        return "RunBeat_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date(runStartMillis)) + ".png";
    }

    /** 저장 성공 시 true. 시간이 걸리므로 백그라운드 스레드에서 호출할 것 */
    public static boolean save(Context c, Bitmap bmp, String name) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return saveMediaStore(c, bmp, name);
            }
            return saveLegacy(c, bmp, name);
        } catch (Exception e) {
            Log.e(TAG, "save failed", e);
            return false;
        }
    }

    private static boolean saveMediaStore(Context c, Bitmap bmp, String name) throws Exception {
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM);
        v.put(MediaStore.Images.Media.IS_PENDING, 1); // 다 쓰기 전에는 갤러리에 안 보이게

        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return false;
        boolean ok;
        try (OutputStream out = cr.openOutputStream(uri)) {
            ok = out != null && bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        if (!ok) {
            cr.delete(uri, null, null); // 실패하면 빈 파일이 남지 않게
            return false;
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0); // 이제 갤러리에 보이게
        cr.update(uri, v, null, null);
        return true;
    }

    @SuppressWarnings("deprecation")
    private static boolean saveLegacy(Context c, Bitmap bmp, String name) throws Exception {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM);
        if (!dir.exists() && !dir.mkdirs()) return false;
        File f = new File(dir, name);
        boolean ok;
        try (OutputStream out = new FileOutputStream(f)) {
            ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        // 갤러리 앱이 새 파일을 바로 알아채도록 알려줌
        if (ok) MediaScannerConnection.scanFile(c, new String[]{f.getAbsolutePath()}, new String[]{"image/png"}, null);
        return ok;
    }
}
