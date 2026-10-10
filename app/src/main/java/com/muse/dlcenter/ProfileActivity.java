package com.muse.dlcenter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 个人资料：改昵称、换头像 */
public class ProfileActivity extends Activity {

    private static final int REQ_AVATAR = 3001;

    private ImageView ivAvatar;
    private EditText etName;
    private TextView tvEmail;
    private long userId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ivAvatar = findViewById(R.id.iv_avatar);
        etName = findViewById(R.id.et_name);
        tvEmail = findViewById(R.id.tv_email);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnPickAvatar = findViewById(R.id.btn_pick_avatar);
        ImageButton btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());
        btnPickAvatar.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(i, "选择头像"), REQ_AVATAR);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
            }
        });
        btnSave.setOnClickListener(v -> saveName());

        loadProfile();
    }

    private void loadProfile() {
        new Thread(() -> {
            try {
                final JSONObject u = ApiClient.getProfile(this);
                userId = u.getLong("id");
                final String name = u.optString("display_name", u.optString("username", ""));
                final String email = u.optString("email", "");
                final boolean hasAvatar = !u.optString("avatar", "").isEmpty();
                runOnUiThread(() -> {
                    etName.setText(name);
                    tvEmail.setText(email);
                    if (hasAvatar) loadAvatarImage();
                });
            } catch (final Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadAvatarImage() {
        new Thread(() -> {
            try {
                URL url = new URL(ApiClient.avatarUrl(this, userId));
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(15000);
                InputStream is = c.getInputStream();
                final android.graphics.Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                runOnUiThread(() -> { if (bmp != null) ivAvatar.setImageBitmap(bmp); });
            } catch (Exception ignored) {}
        }).start();
    }

    private void saveName() {
        final String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "昵称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                final String newName = ApiClient.updateDisplayName(this, name);
                runOnUiThread(() -> Toast.makeText(this, "已保存: " + newName, Toast.LENGTH_SHORT).show());
            } catch (final Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_AVATAR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadAvatar(data.getData());
        }
    }

    private void uploadAvatar(Uri uri) {
        Toast.makeText(this, "上传头像中…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String err = null;
            try {
                String boundary = "----AvatarBoundary" + System.currentTimeMillis();
                URL url = new URL(ApiClient.getServer(this) + "/api/profile/me/avatar");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(30000); c.setReadTimeout(60000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Authorization", "Bearer " + ApiClient.getToken(this));
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                OutputStream os = c.getOutputStream();
                os.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
                os.write(("Content-Disposition: form-data; name=\"avatar\"; filename=\"avatar.jpg\"\r\n").getBytes("UTF-8"));
                os.write(("Content-Type: image/jpeg\r\n\r\n").getBytes("UTF-8"));
                InputStream is = getContentResolver().openInputStream(uri);
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                is.close();
                os.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
                os.flush(); os.close();
                int code = c.getResponseCode();
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(
                        code < 400 ? c.getInputStream() : c.getErrorStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject resp = new JSONObject(sb.toString());
                if (!resp.optBoolean("success")) {
                    JSONObject e = resp.optJSONObject("error");
                    throw new Exception(e != null ? e.optString("message") : "上传失败");
                }
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String msg = err;
            runOnUiThread(() -> {
                if (msg == null) {
                    Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show();
                    loadAvatarImage();
                } else {
                    Toast.makeText(this, "上传失败: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
