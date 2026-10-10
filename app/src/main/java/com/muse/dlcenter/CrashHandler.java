package com.muse.dlcenter;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 全局崩溃捕获：把堆栈写到应用私有文件，下次启动时弹窗显示，
 * 方便把真实错误信息反馈给开发者。
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    public static final String CRASH_FILE = "last_crash.txt";
    private final Context appCtx;
    private final Thread.UncaughtExceptionHandler prev;

    public static void init(Context ctx) {
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        // 避免重复安装
        if (prev instanceof CrashHandler) return;
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(ctx.getApplicationContext(), prev));
    }

    private CrashHandler(Context ctx, Thread.UncaughtExceptionHandler prev) {
        this.appCtx = ctx;
        this.prev = prev;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String log = "线程: " + t.getName() + "\n" + e.toString() + "\n" + sw.toString();
            // 只保留前 3000 字符
            if (log.length() > 3000) log = log.substring(0, 3000);
            FileWriter fw = new FileWriter(new File(appCtx.getFilesDir(), CRASH_FILE));
            fw.write(log);
            fw.close();
        } catch (Exception ignored) {}
        if (prev != null) prev.uncaughtException(t, e);
    }

    public static String readCrash(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), CRASH_FILE);
            if (!f.exists()) return null;
            byte[] buf = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            fis.read(buf);
            fis.close();
            return new String(buf, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static void clearCrash(Context ctx) {
        try { new File(ctx.getFilesDir(), CRASH_FILE).delete(); } catch (Exception ignored) {}
    }
}
