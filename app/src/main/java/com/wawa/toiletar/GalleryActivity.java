package com.wawa.toiletar;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GalleryActivity extends AppCompatActivity {

    private final List<Uri> photos = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();
    private PhotoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        adapter = new PhotoAdapter();
        GridView grid = findViewById(R.id.photoGrid);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (selected.contains(position)) {
                selected.remove(position);
            } else {
                selected.add(position);
            }
            adapter.notifyDataSetChanged();
        });

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.shareSelBtn).setOnClickListener(v -> shareSelected());

        loadPhotos();
    }

    /** 查询本应用保存到 Pictures/WaWaToilet 的历史照片,最新在前。 */
    private void loadPhotos() {
        new Thread(() -> {
            List<Uri> result = new ArrayList<>();
            String[] proj = {MediaStore.Images.Media._ID};
            // 排除自动保存到同一目录的支付宝收款码
            String selection = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?"
                    + " AND " + MediaStore.Images.Media.DISPLAY_NAME + " NOT LIKE ?";
            try (Cursor c = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    proj,
                    selection,
                    new String[]{"%WaWaToilet%", "%alipay%"},
                    MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (c != null) {
                    int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    while (c.moveToNext()) {
                        result.add(Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                String.valueOf(c.getLong(idCol))));
                    }
                }
            } catch (Exception ignored) {
            }
            photos.clear();
            photos.addAll(result);
            selected.clear();
            runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                findViewById(R.id.emptyText)
                        .setVisibility(photos.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    /** 多选分享:ACTION_SEND_MULTIPLE 到微信,未安装则系统分享。 */
    private void shareSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.gallery_none_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        for (int i : selected) uris.add(photos.get(i));

        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("image/jpeg")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(send.setPackage("com.tencent.mm"));
        } catch (Exception e) {
            Toast.makeText(this, R.string.wechat_not_found, Toast.LENGTH_SHORT).show();
            try {
                startActivity(Intent.createChooser(send, null));
            } catch (Exception ignored) {
            }
        }
    }

    private class PhotoAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return photos.size();
        }

        @Override
        public Uri getItem(int position) {
            return photos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_gallery, parent, false);
            ImageView thumb = v.findViewById(R.id.thumb);
            TextView mark = v.findViewById(R.id.checkMark);

            thumb.setImageBitmap(decodeThumb(photos.get(position), 240));
            mark.setVisibility(selected.contains(position) ? View.VISIBLE : View.GONE);
            return v;
        }

        /** 按目标边长降采样解码,避免 GridView 直接解码 5MP 大图. */
        private Bitmap decodeThumb(Uri uri, int reqSize) {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(is, null, bounds);
                }
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= reqSize) {
                    sample *= 2;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    return BitmapFactory.decodeStream(is, null, opts);
                }
            } catch (Exception e) {
                return null;
            }
        }
    }
}
