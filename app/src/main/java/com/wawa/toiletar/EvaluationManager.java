package com.wawa.toiletar;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 软件评估期管理:评估期 7 天,到期日存入 JSON 文件。
 * 到期后提示是否赞助(自愿、不强制),不赞助可继续延长评估。
 */
public class EvaluationManager {

    private static final String FILE = "evaluation.json";
    private static final String KEY_UNTIL = "eval_until";
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    /** 是否已过评估期;首次使用时自动写入 7 天后的到期日。 */
    public static boolean isExpired(Context context) {
        long until = readUntil(context);
        if (until <= 0) {
            writeUntil(context, System.currentTimeMillis() + WEEK_MS);
            return false;
        }
        return System.currentTimeMillis() > until;
    }

    /** 延长 7 天(从"当前时间与现有到期日的较大者"起算)。 */
    public static void extend(Context context) {
        long base = Math.max(System.currentTimeMillis(), readUntil(context));
        writeUntil(context, base + WEEK_MS);
    }

    private static long readUntil(Context context) {
        try {
            File f = new File(context.getFilesDir(), FILE);
            if (!f.exists()) return 0;
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[(int) f.length()];
                int n = in.read(buf);
                JSONObject o = new JSONObject(
                        new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8));
                return o.optLong(KEY_UNTIL, 0);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static void writeUntil(Context context, long until) {
        try {
            JSONObject o = new JSONObject();
            o.put(KEY_UNTIL, until);
            try (FileOutputStream out =
                         new FileOutputStream(new File(context.getFilesDir(), FILE))) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }
}
