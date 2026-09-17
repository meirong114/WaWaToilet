package com.wawa.toiletar;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    private static final String GITHUB_URL = "https://github.com/meirong114/WaWaToilet";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        findViewById(R.id.licenseLink).setOnClickListener(v ->
                startActivity(new Intent(this, LegalActivity.class)
                        .putExtra(LegalActivity.EXTRA_VIEW_ONLY, true)));

        findViewById(R.id.githubLink).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))));

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
