package ncnn.v5lite.demo;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 检测结果持久化：写到【公共 Downloads/YOLOv5Lite/】，按天一个 CSV 文件（保留历史）。
 * - Android 10+：走 MediaStore.Downloads（适配分区存储，无需存储权限）
 * - Android 9-：直接写公共 Downloads 目录（需 WRITE_EXTERNAL_STORAGE，已在 Manifest 声明 maxSdkVersion=28）
 */
public class DetectionStore {
    private static final String TAG = "DetectionStore";
    private static final String SUB_DIR = "YOLOv5Lite";
    private static final String MIME = "text/csv";
    private static final String HEADER = "时间,物体,置信度\n";

    public static class Item {
        public final String time;
        public final String label;
        public final float prob;

        public Item(String time, String label, float prob) {
            this.time = time;
            this.label = label;
            this.prob = prob;
        }
    }

    private final Context ctx;

    public DetectionStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    public static String fileName() {
        return "detect_" + today() + ".csv";
    }

    /** 组装一行：时间,物体,置信度 */
    public static String row(String time, String label, float prob) {
        return time + "," + label + "," + String.format(Locale.CHINA, "%.2f", prob) + "\n";
    }

    public boolean append(List<Item> items) {
        if (items == null || items.isEmpty()) return false;
        StringBuilder sb = new StringBuilder();
        for (Item it : items) sb.append(row(it.time, it.label, it.prob));
        return write(sb.toString());
    }

    private boolean write(String content) {
        try {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? writeMediaStore(content)
                    : writeLegacy(content);
        } catch (Exception e) {
            Log.e(TAG, "写入检测结果失败", e);
            return false;
        }
    }

    // ---------- Android 10+ : MediaStore.Downloads ----------
    private boolean writeMediaStore(String content) {
        String relPath = Environment.DIRECTORY_DOWNLOADS + "/" + SUB_DIR + "/";
        String name = fileName();

        Uri uri = findExisting(relPath, name);
        boolean isNew = (uri == null);

        if (isNew) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, MIME);
            cv.put(MediaStore.Downloads.RELATIVE_PATH, relPath);
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return false;
        }

        try {
            OutputStream os = ctx.getContentResolver().openOutputStream(uri, "wa");
            if (os == null) return false;
            OutputStreamWriter w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            if (isNew) w.write("\uFEFF" + HEADER); // BOM 便于 Excel 正确识别中文
            w.write(content);
            w.flush();
            w.close();
        } catch (Exception e) {
            Log.e(TAG, "MediaStore 写入异常", e);
            return false;
        }

        if (isNew) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, done, null, null);
        }
        return true;
    }

    private Uri findExisting(String relPath, String name) {
        ContentResolver cr = ctx.getContentResolver();
        String sel = MediaStore.Downloads.RELATIVE_PATH + "=? AND " +
                MediaStore.Downloads.DISPLAY_NAME + "=?";
        String[] args = new String[]{relPath, name};
        try (Cursor c = cr.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Downloads._ID}, sel, args, null)) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception e) {
            Log.w(TAG, "查询已存在文件失败，按新建处理", e);
        }
        return null;
    }

    // ---------- Android 9- : 直接文件 ----------
    private boolean writeLegacy(String content) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), SUB_DIR);
        if (!dir.exists() && !dir.mkdirs()) return false;
        File f = new File(dir, fileName());
        boolean isNew = !f.exists();
        try (FileOutputStream fos = new FileOutputStream(f, true)) {
            OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            if (isNew) w.write("\uFEFF" + HEADER);
            w.write(content);
            w.flush();
        } catch (Exception e) {
            Log.e(TAG, "旧版写入异常", e);
            return false;
        }
        return true;
    }
}
