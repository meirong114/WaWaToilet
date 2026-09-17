package com.wawa.toiletar;

import android.content.Context;
import android.content.SharedPreferences;

/** 应用设置存取:照片质量档位(影响分辨率与文件大小)。 */
public class SettingsManager {

    public static final int QUALITY_HIGH = 0;   // 清晰,约 1MB
    public static final int QUALITY_MEDIUM = 1; // 中等,约 500KB
    public static final int QUALITY_LOW = 2;    // 节省,约 200KB

    private static final String PREFS = "settings";
    private static final String KEY_QUALITY = "quality";

    public static int getQuality(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_QUALITY, QUALITY_HIGH);
    }

    public static void setQuality(Context context, int quality) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_QUALITY, quality).apply();
    }

    /** 按档位返回缩放比例,用于降低分辨率。 */
    public static float getScale(int quality) {
        switch (quality) {
            case QUALITY_MEDIUM:
                return 0.65f;
            case QUALITY_LOW:
                return 0.45f;
            default:
                return 1.0f;
        }
    }

    /** 按档位返回 JPEG 压缩质量。 */
    public static int getJpegQuality(int quality) {
        switch (quality) {
            case QUALITY_MEDIUM:
                return 80;
            case QUALITY_LOW:
                return 70;
            default:
                return 92;
        }
    }
}
