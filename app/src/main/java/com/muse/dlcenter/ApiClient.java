package com.muse.dlcenter;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** 下载中心 API 客户端 */
public class ApiClient {

    private static final String PREF = "dlcenter_pref";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER = "user_json";
    private static final String KEY_SERVER = "server_url";
    public static final String DEFAULT_SERVER = "http://100.105.107.86:3000";

    // 微信 AppID（站长在开放平台审核通过后填入）
    public static final String WECHAT_APPID = "";

    public static String getServer(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY_SERVER, DEFAULT_SERVER);
    }

    public static void setServer(Context ctx, String url) {
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_SERVER, url).apply();
    }

    public static String getToken(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TOKEN, null);
    }

    public static void saveAuth(Context ctx, String token, String userJson) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_TOKEN, token).putString(KEY_USER, userJson).apply();
    }

    public static void clearAuth(Context ctx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .remove(KEY_TOKEN).remove(KEY_USER).apply();
    }

    public static boolean isAdmin(Context ctx) {
        try {
            String u = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_USER, null);
            if (u == null) return false;
            JSONObject jo = new JSONObject(u);
            String role = jo.optString("role");
            return role.equals("admin") || jo.optInt("role_id") == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public static class Result {
        public boolean ok;
        public JSONObject data;
        public String error;
    }

    private static String readAll(HttpURLConnection c) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(
                (c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream()), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static HttpURLConnection open(Context ctx, String path) throws Exception {
        URL url = new URL(getServer(ctx) + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        String t = getToken(ctx);
        if (t != null) c.setRequestProperty("Authorization", "Bearer " + t);
        return c;
    }

    /** 账号密码登录 */
    public static Result login(Context ctx, String identifier, String password) {
        Result r = new Result();
        try {
            HttpURLConnection c = open(ctx, "/api/auth/login");
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            JSONObject body = new JSONObject();
            body.put("identifier", identifier);
            body.put("password", password);
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            JSONObject resp = new JSONObject(readAll(c));
            if (resp.optBoolean("success")) {
                JSONObject data = resp.getJSONObject("data");
                saveAuth(ctx, data.getString("accessToken"), data.getJSONObject("user").toString());
                r.ok = true; r.data = data;
            } else {
                r.ok = false;
                r.error = resp.optJSONObject("error") != null
                        ? resp.optJSONObject("error").optString("message") : "登录失败";
            }
        } catch (Exception e) {
            r.ok = false; r.error = "网络错误: " + e.getMessage();
        }
        return r;
    }

    /** 微信登录：用 SDK 拿到的 code 换 token */
    public static Result wechatLogin(Context ctx, String code) {
        Result r = new Result();
        try {
            HttpURLConnection c = open(ctx, "/api/auth/wechat");
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            JSONObject body = new JSONObject();
            body.put("code", code);
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            JSONObject resp = new JSONObject(readAll(c));
            if (resp.optBoolean("success")) {
                JSONObject data = resp.getJSONObject("data");
                saveAuth(ctx, data.getString("access_token"), data.getJSONObject("user").toString());
                r.ok = true; r.data = data;
            } else {
                r.ok = false;
                r.error = resp.optJSONObject("error") != null
                        ? resp.optJSONObject("error").optString("message") : "微信登录失败";
            }
        } catch (Exception e) {
            r.ok = false; r.error = "网络错误: " + e.getMessage();
        }
        return r;
    }

    // ---------- 文件 ----------
    public static class DlFile {
        public long id; public String title; public String description;
        public String originalName; public String mime; public long size;
        public int downloads; public String createdAt;
    }

    public static List<DlFile> getFiles(Context ctx) throws Exception {
        HttpURLConnection c = open(ctx, "/api/files");
        JSONObject resp = new JSONObject(readAll(c));
        List<DlFile> list = new ArrayList<DlFile>();
        if (resp.optBoolean("success")) {
            JSONArray arr = resp.getJSONObject("data").getJSONArray("files");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                DlFile f = new DlFile();
                f.id = o.getLong("id");
                f.title = o.optString("title");
                f.description = o.optString("description");
                f.originalName = o.optString("original_name");
                f.mime = o.optString("mime");
                f.size = o.optLong("size");
                f.downloads = o.optInt("downloads");
                f.createdAt = o.optString("created_at");
                list.add(f);
            }
        } else {
            JSONObject e = resp.optJSONObject("error");
            throw new Exception(e != null ? e.optString("message") : "获取失败");
        }
        return list;
    }

    public static String downloadUrl(Context ctx, long id) {
        return getServer(ctx) + "/api/files/download/" + id;
    }

    public static void deleteFile(Context ctx, long id) throws Exception {
        HttpURLConnection c = open(ctx, "/api/files/" + id);
        c.setRequestMethod("DELETE");
        JSONObject resp = new JSONObject(readAll(c));
        if (!resp.optBoolean("success")) {
            JSONObject e = resp.optJSONObject("error");
            throw new Exception(e != null ? e.optString("message") : "删除失败");
        }
    }

    // ---------- 资料 ----------
    public static JSONObject getProfile(Context ctx) throws Exception {
        HttpURLConnection c = open(ctx, "/api/profile/me");
        JSONObject resp = new JSONObject(readAll(c));
        if (resp.optBoolean("success")) return resp.getJSONObject("data").getJSONObject("user");
        JSONObject e = resp.optJSONObject("error");
        throw new Exception(e != null ? e.optString("message") : "获取失败");
    }

    public static String updateDisplayName(Context ctx, String name) throws Exception {
        HttpURLConnection c = open(ctx, "/api/profile/me");
        c.setRequestMethod("PUT");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        JSONObject body = new JSONObject();
        body.put("display_name", name);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        JSONObject resp = new JSONObject(readAll(c));
        if (resp.optBoolean("success")) return resp.getJSONObject("data").getString("display_name");
        JSONObject e = resp.optJSONObject("error");
        throw new Exception(e != null ? e.optString("message") : "更新失败");
    }

    public static String avatarUrl(Context ctx, long userId) {
        return getServer(ctx) + "/api/profile/avatar/" + userId;
    }
}
