package com.wawa.toiletar;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 100;

    private PreviewView previewView;
    private ImageView signOverlay;
    private Button directionBtn;
    private View captureBtn;

    /** 最新的相机帧,已按显示方向旋转,仅在主线程读写。 */
    private volatile Bitmap latestFrame;

    private boolean signFlipped = false; // false=朝左, true=朝右
    private ProcessCameraProvider cameraProvider;

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(getApplicationContext(), "一千万以内最好的厕所！", Toast.LENGTH_SHORT).show();
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        signOverlay = findViewById(R.id.signOverlay);
        directionBtn = findViewById(R.id.directionBtn);
        captureBtn = findViewById(R.id.captureBtn);

        setupSignDrag();

        directionBtn.setOnClickListener(v -> {
            signFlipped = !signFlipped;
            signOverlay.setImageResource(signFlipped
                    ? R.drawable.right : R.drawable.left);
            directionBtn.setText(signFlipped
                    ? getString(R.string.direction_right)
                    : getString(R.string.direction_left));
        });

        captureBtn.setOnClickListener(v -> capture());

        // 打开设置页面
        findViewById(R.id.settingsBtn).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // 右上角分享:打开历史照片页,可多选分享
        findViewById(R.id.shareBtn).setOnClickListener(v ->
                startActivity(new Intent(this, GalleryActivity.class)));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }

        maybeShowEvalExpiryDialog();
    }

    /** 评估期到期提示:自愿赞助,不强制;继续评估则延长 7 天。 */
    private void maybeShowEvalExpiryDialog() {
        if (!EvaluationManager.isExpired(this)) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.eval_expired_title)
                .setMessage(R.string.eval_expired_message)
                .setCancelable(false)
                .setPositiveButton(R.string.eval_expired_sponsor, (d, w) ->
                        startActivity(new Intent(this, PayActivity.class)))
                .setNegativeButton(R.string.eval_expired_continue, (d, w) ->
                        EvaluationManager.extend(this))
                .show();
    }

    // region 权限

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    // endregion

    // region CameraX

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // 默认仅 640x480,需显式请求高分辨率,否则"清晰"档也只有约 37KB
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setResolutionSelector(
                                new ResolutionSelector.Builder()
                                        .setResolutionStrategy(new ResolutionStrategy(
                                                new Size(2592, 1944),
                                                ResolutionStrategy
                                                        .FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                                        .build())
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::onFrame);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis);
            } catch (Exception e) {
                Toast.makeText(this, "相机初始化失败: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** 后台线程解码最新帧,转到显示方向后缓存到主线程。 */
    private void onFrame(ImageProxy proxy) {
        try {
            Bitmap bmp = proxy.toBitmap();
            int rotation = proxy.getImageInfo().getRotationDegrees();
            if (rotation != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotation);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }
            final Bitmap frame = bmp;
            runOnUiThread(() -> latestFrame = frame);
        } catch (Exception ignored) {
            // 单帧解码失败直接跳过
        } finally {
            proxy.close();
        }
    }

    // endregion

    // region 牌子拖动

    private void setupSignDrag() {
        signOverlay.setOnTouchListener(new View.OnTouchListener() {
            private float dragDx, dragDy;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragDx = v.getX() - event.getRawX();
                        dragDy = v.getY() - event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float nx = event.getRawX() + dragDx;
                        float ny = event.getRawY() + dragDy;
                        // 限制在父容器内
                        View parent = (View) v.getParent();
                        nx = Math.max(0, Math.min(nx, parent.getWidth() - v.getWidth()));
                        ny = Math.max(0, Math.min(ny, parent.getHeight() - v.getHeight()));
                        v.setX(nx);
                        v.setY(ny);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            }
        });
    }

    // endregion

    // region 拍照合成

    private void capture() {
        Bitmap frame = latestFrame;
        if (frame == null || frame.isRecycled()) {
            Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap out = frame.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);

        // 屏幕上的 PreviewView 使用默认的 FILL_CENTER 缩放,
        // 计算屏幕坐标 -> 合成图坐标的映射(中心裁剪的逆变换)。
        float vw = previewView.getWidth();
        float vh = previewView.getHeight();
        float bw = out.getWidth();
        float bh = out.getHeight();
        float scale = Math.max(vw / bw, vh / bh);
        float offX = (vw - bw * scale) / 2f;
        float offY = (vh - bh * scale) / 2f;

        int[] pLoc = new int[2];
        int[] sLoc = new int[2];
        previewView.getLocationInWindow(pLoc);
        signOverlay.getLocationInWindow(sLoc);
        float sx = sLoc[0] - pLoc[0];
        float sy = sLoc[1] - pLoc[1];

        Rect dst = new Rect(
                Math.round((sx - offX) / scale),
                Math.round((sy - offY) / scale),
                Math.round((sx + signOverlay.getWidth() - offX) / scale),
                Math.round((sy + signOverlay.getHeight() - offY) / scale));

        // 必须复制一份独立的 Drawable:setBounds() 会修改共享实例的边界,
        // 直接使用 signOverlay.getDrawable() 会导致拍照后屏幕上的牌子消失/缩小
        Drawable sign = signOverlay.getDrawable().getConstantState().newDrawable().mutate();
        sign.setBounds(dst);
        sign.draw(canvas);

        // 按设置档位调整分辨率,降低文件大小
        int quality = SettingsManager.getQuality(this);
        float resScale = SettingsManager.getScale(quality);
        if (resScale < 1.0f) {
            out = Bitmap.createScaledBitmap(out,
                    Math.round(out.getWidth() * resScale),
                    Math.round(out.getHeight() * resScale), true);
        }

        // 拍照后直接保存到相册,分享通过右上角"分享"按钮在历史照片中选择
        saveJpeg(out, SettingsManager.getJpegQuality(quality));
    }

    private void saveJpeg(Bitmap bitmap, int jpegQuality) {
        analysisExecutor.execute(() -> {
            Uri uri = writeToGallery(this, bitmap, jpegQuality);
            runOnUiThread(() -> Toast.makeText(this,
                    uri == null ? R.string.save_failed : R.string.saved_to,
                    Toast.LENGTH_SHORT).show());
        });
    }

    /** 把照片写入相册并返回 content Uri(在后台线程调用)。 */
    static Uri writeToGallery(Context context, Bitmap bitmap, int jpegQuality) {
        String name = String.format(Locale.US, "WaWaToilet_%d.jpg",
                System.currentTimeMillis());
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/WaWaToilet");

        Uri uri = context.getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return null;
        try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, os);
            return uri;
        } catch (Exception e) {
            context.getContentResolver().delete(uri, null, null);
            return null;
        }
    }

    // endregion

    @Override
    protected void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdown();
    }
}
