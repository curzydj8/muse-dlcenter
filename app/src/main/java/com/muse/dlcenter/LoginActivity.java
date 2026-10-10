package com.muse.dlcenter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** 登录页：微信登录（需 AppID）+ 账号密码 */
public class LoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            CrashHandler.init(this);
            if (ApiClient.getToken(this) != null) {
                finish();
                return;
            }
            setContentView(R.layout.activity_login);

            EditText etServer = findViewById(R.id.et_server);
            EditText etUser = findViewById(R.id.et_user);
            EditText etPass = findViewById(R.id.et_pass);
            Button btnLogin = findViewById(R.id.btn_login);
            Button btnWechat = findViewById(R.id.btn_wechat);
            TextView tvCrash = findViewById(R.id.tv_crash);

            etServer.setText(ApiClient.getServer(this));

            // 上次崩溃提示
            String crash = CrashHandler.readCrash(this);
            if (crash != null) {
                tvCrash.setText("上次闪退原因:\n" + crash.substring(0, Math.min(500, crash.length())));
                tvCrash.setVisibility(android.view.View.VISIBLE);
            }

            btnLogin.setOnClickListener(v -> {
                String server = etServer.getText().toString().trim();
                String user = etUser.getText().toString().trim();
                String pass = etPass.getText().toString();
                if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
                    return;
                }
                ApiClient.setServer(this, server);
                btnLogin.setEnabled(false);
                btnLogin.setText("登录中…");
                new Thread(() -> {
                    ApiClient.Result r = ApiClient.login(this, user, pass);
                    runOnUiThread(() -> {
                        btnLogin.setEnabled(true);
                        btnLogin.setText("登录");
                        if (r.ok) {
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(this, r.error, Toast.LENGTH_LONG).show();
                        }
                    });
                }).start();
            });

            btnWechat.setOnClickListener(v -> {
                if (ApiClient.WECHAT_APPID.isEmpty()) {
                    Toast.makeText(this, "微信登录尚未配置，请先用账号登录", Toast.LENGTH_LONG).show();
                    return;
                }
                // TODO: 接入微信 SDK，发授权请求，onResp 拿 code 调 ApiClient.wechatLogin
                Toast.makeText(this, "微信 SDK 接入中…", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
            b.setTitle("登录页出错");
            b.setMessage(e.toString());
            b.setPositiveButton("关闭", (d, w) -> finish());
            b.show();
        }
    }
}
