package com.muse.dlcenter;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 管理员上传文件到下载中心 */
public class UploadActivity extends Activity {

    private static final int REQ_FILE = 2001;

    private Uri fileUri;
    private String fileName;
    private TextView tvFile;
    private EditText etTitle, etDesc;
    private Button btnUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_upload);

            tvFile = findViewById(R.id.tv_file);
            etTitle = findViewById(R.id.et_title);
            etDesc = findViewById(R.id.et_desc);
            btnUpload = findViewById(R.id.btn_upload);
            Button btnPick = findViewById(R.id.btn_pick);
            ImageButton btnBack = findViewById(R.id.btn_back);

            btnPick.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "选择文件"), REQ_FILE);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                }
            });
            btnBack.setOnClickListener(v -> finish());
            btnUpload.setOnClickListener(v -> doUpload());
        } catch (Exception e) {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
            b.setTitle("上传页出错");
            b.setMessage(e.toString());
            b.setPositiveButton("关闭", (d, w) -> finish());
            b.show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            fileName = getName(fileUri);
            tvFile.setText("文件: " + fileName);
            if (etTitle.getText().toString().isEmpty()) {
                int dot = fileName.lastIndexOf('.');
                etTitle.setText(dot > 0 ? fileName.substring(0, dot) : fileName);
            }
        }
    }

    private String getName(Uri uri) {
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                c.moveToFirst();
                String n = c.getString(idx);
                c.close();
                if (n != null) return n;
            }
        } catch (Exception ignored) {}
        return "file";
    }

    private void doUpload() {
        if (fileUri == null) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        btnUpload.setEnabled(false);
        btnUpload.setText("上传中…");
        final String title = etTitle.getText().toString().trim();
        final String desc = etDesc.getText().toString().trim();

        new Thread(() -> {
            String err = null;
            try {
                String boundary = "----DlBoundary" + System.currentTimeMillis();
                URL url = new URL(ApiClient.getServer(this) + "/api/files/upload");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(30000); c.setReadTimeout(300000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Authorization", "Bearer " + ApiClient.getToken(this));
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                OutputStream os = c.getOutputStream();
                writeField(os, boundary, "title", title);
                writeField(os, boundary, "description", desc);
                os.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
                os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes("UTF-8"));
                os.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes("UTF-8"));
                InputStream is = getContentResolver().openInputStream(fileUri);
                byte[] buf = new byte[256 * 1024];
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
                org.json.JSONObject resp = new org.json.JSONObject(sb.toString());
                if (!resp.optBoolean("success")) {
                    org.json.JSONObject e = resp.optJSONObject("error");
                    throw new Exception(e != null ? e.optString("message") : "上传失败");
                }
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String msg = err;
            runOnUiThread(() -> {
                btnUpload.setEnabled(true);
                btnUpload.setText("上传");
                if (msg == null) {
                    Toast.makeText(this, "上传成功", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, "上传失败: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void writeField(OutputStream os, String b, String name, String value) throws Exception {
        os.write(("--" + b + "\r\n").getBytes("UTF-8"));
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"));
        os.write(((value == null ? "" : value) + "\r\n").getBytes("UTF-8"));
    }
}
