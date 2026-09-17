package com.wawa.toiletar;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        RadioGroup group = findViewById(R.id.qualityGroup);
        int quality = SettingsManager.getQuality(this);
        if (quality == SettingsManager.QUALITY_MEDIUM) {
            group.check(R.id.qualityMedium);
        } else if (quality == SettingsManager.QUALITY_LOW) {
            group.check(R.id.qualityLow);
        } else {
            group.check(R.id.qualityHigh);
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId == R.id.qualityMedium) {
                SettingsManager.setQuality(this, SettingsManager.QUALITY_MEDIUM);
            } else if (checkedId == R.id.qualityLow) {
                SettingsManager.setQuality(this, SettingsManager.QUALITY_LOW);
            } else {
                SettingsManager.setQuality(this, SettingsManager.QUALITY_HIGH);
            }
        });

        findViewById(R.id.aboutItem).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        // 延长评估时长(自愿,不强制赞助)
        findViewById(R.id.evalItem).setOnClickListener(v -> {
            EvaluationManager.extend(this);
            Toast.makeText(this, R.string.extend_done, Toast.LENGTH_SHORT).show();
        });

        // 赞助:打开支付宝收款码
        findViewById(R.id.donateItem).setOnClickListener(v ->
                startActivity(new Intent(this, PayActivity.class)));

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
