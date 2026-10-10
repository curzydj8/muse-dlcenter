package com.muse.dlcenter;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 下载中心：文件列表 + 下载（管理员可上传/删除） */
public class MainActivity extends Activity {

    private ListView lvFiles;
    private FileAdapter adapter;
    private List<ApiClient.DlFile> files = new ArrayList<ApiClient.DlFile>();
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            CrashHandler.init(this);
            setContentView(R.layout.activity_main);

            lvFiles = findViewById(R.id.lv_files);
            tvEmpty = findViewById(R.id.tv_empty);
            Button btnUpload = findViewById(R.id.btn_upload);
            Button btnProfile = findViewById(R.id.btn_profile);
            Button btnLogout = findViewById(R.id.btn_logout);
            Button btnRefresh = findViewById(R.id.btn_refresh);
            Button btnWechatLogin = findViewById(R.id.btn_wechat_login);
            Button btnPlayer = findViewById(R.id.btn_player);

            if (btnWechatLogin == null || lvFiles == null) {
                throw new RuntimeException("布局加载异常，请截图反馈");
            }

            adapter = new FileAdapter();
            lvFiles.setAdapter(adapter);

            btnRefresh.setOnClickListener(v -> loadFiles());
            btnUpload.setOnClickListener(v -> startActivity(new Intent(this, UploadActivity.class)));
            btnPlayer.setOnClickListener(v -> startActivity(new Intent(this, PlayerActivity.class)));
            btnProfile.setOnClickListener(v -> {
                if (ApiClient.getToken(this) == null) {
                    startActivity(new Intent(this, LoginActivity.class));
                } else {
                    startActivity(new Intent(this, ProfileActivity.class));
                }
            });
            btnLogout.setOnClickListener(v -> {
                ApiClient.clearAuth(this);
                refreshUI();
                Toast.makeText(this, "已退出", Toast.LENGTH_SHORT).show();
            });
            btnWechatLogin.setOnClickListener(v ->
                    startActivity(new Intent(this, LoginActivity.class)));

            refreshUI();
        } catch (Exception e) {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
            b.setTitle("启动出错");
            b.setMessage(e.toString() + "\n请截图发给开发者");
            b.setPositiveButton("关闭", (d, w) -> finish());
            b.show();
        }
    }

    /** 根据登录状态刷新 UI */
    private void refreshUI() {
        boolean loggedIn = ApiClient.getToken(this) != null;
        Button btnUpload = findViewById(R.id.btn_upload);
        Button btnProfile = findViewById(R.id.btn_profile);
        Button btnLogout = findViewById(R.id.btn_logout);
        Button btnWechatLogin = findViewById(R.id.btn_wechat_login);

        btnWechatLogin.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        btnLogout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        btnProfile.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        // 只有管理员显示上传按钮
        btnUpload.setVisibility(loggedIn && ApiClient.isAdmin(this) ? View.VISIBLE : View.GONE);

        if (loggedIn) {
            loadFiles();
        } else {
            files.clear();
            adapter.notifyDataSetChanged();
            tvEmpty.setText("点击右上角微信登录后查看文件");
            tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 登录/上传返回时刷新状态
        refreshUI();
    }

    private void loadFiles() {
        tvEmpty.setText("加载中…");
        tvEmpty.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                final List<ApiClient.DlFile> list = ApiClient.getFiles(this);
                runOnUiThread(() -> {
                    files = list;
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    if (list.isEmpty()) tvEmpty.setText("还没有文件");
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    tvEmpty.setText("加载失败: " + e.getMessage());
                    tvEmpty.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void downloadFile(ApiClient.DlFile f) {
        if (ApiClient.getToken(this) == null) {
            Toast.makeText(this, "请先微信登录后再下载", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(
                    Uri.parse(ApiClient.downloadUrl(this, f.id)));
            req.addRequestHeader("Authorization", "Bearer " + ApiClient.getToken(this));
            req.setTitle(f.title);
            req.setDescription(f.originalName);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, f.originalName);
            dm.enqueue(req);
            Toast.makeText(this, "开始下载: " + f.originalName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile(ApiClient.DlFile f, int pos) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage("确定删除《" + f.title + "》吗？")
                .setPositiveButton("删除", (d, w) -> new Thread(() -> {
                    try {
                        ApiClient.deleteFile(this, f.id);
                        runOnUiThread(() -> {
                            files.remove(pos);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                        });
                    } catch (final Exception e) {
                        runOnUiThread(() ->
                                Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }).start())
                .setNegativeButton("取消", null)
                .show();
    }

    private String fmtSize(long s) {
        if (s < 1024) return s + "B";
        if (s < 1024 * 1024) return String.format("%.1fKB", s / 1024.0);
        if (s < 1024 * 1024 * 1024) return String.format("%.1fMB", s / 1024.0 / 1024.0);
        return String.format("%.2fGB", s / 1024.0 / 1024.0 / 1024.0);
    }

    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return files.size(); }
        @Override public Object getItem(int p) { return files.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override
        public View getView(int position, View cv, ViewGroup parent) {
            if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_file, parent, false);
            final ApiClient.DlFile f = files.get(position);
            final int pos = position;
            ((TextView) cv.findViewById(R.id.tv_title)).setText(f.title);
            ((TextView) cv.findViewById(R.id.tv_info)).setText(
                    f.originalName + " · " + fmtSize(f.size) + " · 下载 " + f.downloads + " 次");
            cv.findViewById(R.id.btn_download).setOnClickListener(v -> downloadFile(f));
            ImageButton btnDel = cv.findViewById(R.id.btn_delete);
            btnDel.setVisibility(ApiClient.isAdmin(MainActivity.this) ? View.VISIBLE : View.GONE);
            btnDel.setOnClickListener(v -> deleteFile(f, pos));
            return cv;
        }
    }
}
