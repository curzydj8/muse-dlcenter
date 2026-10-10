# Muse 下载中心

前后端一体化的文件下载中心 Android App。

## 功能

### 下载中心
- 打开即主页面，无需登录即可浏览
- 右上角微信登录（需配置 AppID）
- 登录后下载文件（系统下载器，后台下载）
- 管理员可上传/删除文件

### 音乐播放器（内置）
- 本地音乐播放：随机开局、列表置顶、内嵌封面
- 同名 `.smi` 歌词同步显示
- 播放列表可删除（带确认）

### 个人资料
- 修改昵称
- 更换头像

## 技术架构

```
App (Android 原生 Java)
  ↓ HTTPS/Tailscale
Ubuntu Server (Node.js + Express + SQLite)
  ├── /api/files    文件中心
  ├── /api/profile  用户资料
  └── /api/auth     登录（含微信）
```

## 服务端部署

见 `/opt/mars-geek/server/routes/`：
- `files.js` - 文件列表/下载/上传/删除
- `profile.js` - 昵称/头像
- `wechat.js` - 微信登录（需 `WECHAT_APPID` / `WECHAT_SECRET`）

## 构建

```bash
./build.sh
```

需要：JDK 17、Android SDK（build-tools 34.0.0、platform android-34）、签名密钥。

## 版本历史

- v1.0 (2026-10-10)：首个版本

## License

MIT
