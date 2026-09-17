package com.wawa.toiletar;

import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.OutputStream;

public class PayActivity extends AppCompatActivity {

    /** 固定文件名:收款码只保存一份,已存在则不再重复保存。 */
    private static final String QR_NAME = "WaWaToilet_alipay.jpg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        // 进入页面时自动把收款码保存到相册(仅首次)
        saveQrToGallery();
    }

    private void saveQrToGallery() {
        Drawable d = ContextCompat.getDrawable(this, R.drawable.alipay);
        if (!(d instanceof BitmapDrawable)) {
            Toast.makeText(this, R.string.pay_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = ((BitmapDrawable) d).getBitmap();

        new Thread(() -> {
            // 相册里已有同名收款码就直接跳过,避免重复保存
            try (Cursor c = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media._ID},
                    MediaStore.Images.Media.DISPLAY_NAME + " = ?",
                    new String[]{QR_NAME}, null)) {
                if (c != null && c.moveToFirst()) return;
            } catch (Exception ignored) {
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, QR_NAME);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/WaWaToilet");

            Uri uri = getContentResolver()
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.pay_save_failed, Toast.LENGTH_SHORT).show());
                return;
            }
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.pay_saved, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.pay_save_failed, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
