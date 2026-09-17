package com.wawa.toiletar;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class LegalActivity extends AppCompatActivity {

    private static final String PREF_FILE = "agreement.json";
    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;

    /** 仅查看模式(从主界面进入),不拦截返回。 */
    public static final String EXTRA_VIEW_ONLY = "view_only";

    /** 读取 JSON 记录,判断许可证页面是否需要展示。 */
    public static boolean shouldShow(Context context) {
        try {
            File f = new File(context.getFilesDir(), PREF_FILE);
            if (!f.exists()) {
                return true;
            }
            JSONObject o = new JSONObject(
                    new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            String mode = o.optString("mode");
            if ("forever".equals(mode)) {
                return false;
            }
            if ("7d".equals(mode)) {
                return System.currentTimeMillis() > o.optLong("hide_until");
            }
        } catch (Exception ignored) {
            // 记录损坏时重新展示
        }
        return true;
    }

    /** 将同意状态写入 JSON 文件:{"mode":"7d","hide_until":...} 或 {"mode":"forever"}。 */
    private static void recordAgreement(Context context, String mode, long hideUntil) {
        try {
            JSONObject o = new JSONObject();
            o.put("mode", mode);
            if (hideUntil > 0) {
                o.put("hide_until", hideUntil);
            }
            Files.write(new File(context.getFilesDir(), PREF_FILE).toPath(),
                    o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean viewOnly = getIntent().getBooleanExtra(EXTRA_VIEW_ONLY, false);

        // 强制同意:非查看模式下,若已同意且在静默期内,直接进入主界面
        if (!viewOnly && !shouldShow(this)) {
            openMain();
            return;
        }

        setContentView(R.layout.activity_legal);

        // 查看模式下才显示"返回";强制同意模式下只能二选一
        findViewById(R.id.backBtn).setVisibility(viewOnly ? View.VISIBLE : View.GONE);
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        findViewById(R.id.hide7dBtn).setOnClickListener(v -> {
            recordAgreement(this, "7d", System.currentTimeMillis() + SEVEN_DAYS_MS);
            if (viewOnly) {
                finish();
            } else {
                openMain();
            }
        });

        findViewById(R.id.hideForeverBtn).setOnClickListener(v -> {
            recordAgreement(this, "forever", 0);
            if (viewOnly) {
                finish();
            } else {
                openMain();
            }
        });
    }

    /** 强制同意模式下禁用返回键退出。 */
    @Override
    public void onBackPressed() {
        if (getIntent().getBooleanExtra(EXTRA_VIEW_ONLY, false)) {
            super.onBackPressed();
        }
        // 否则停留在许可证页面
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
