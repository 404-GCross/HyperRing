package io.github.gcross.hyperring.shizuku;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import io.github.gcross.hyperring.ringtone.RingtoneApplyResult;
import rikka.shizuku.Shizuku;

public final class ShizukuShell {
    private static final Method NEW_PROCESS_METHOD = resolveNewProcessMethod();

    private ShizukuShell() {
    }

    public static boolean isConnected() {
        return Shizuku.getBinder() != null && Shizuku.pingBinder();
    }

    public static boolean isAuthorized() {
        return isConnected() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isPreV11() {
        return Shizuku.isPreV11();
    }

    public static boolean shouldShowRequestPermissionRationale() {
        return isConnected() && Shizuku.shouldShowRequestPermissionRationale();
    }

    public static RingtoneApplyResult ensureReady(Context context) {
        if (!isConnected()) {
            return RingtoneApplyResult.needShizukuPermission(
                    "Shizuku 未连接，请先在设备上启动 Shizuku 后再试。");
        }
        if (!isAuthorized()) {
            return RingtoneApplyResult.needShizukuPermission(
                    "Shizuku 已连接，但尚未授权 HyperRing。");
        }
        return null;
    }

    public static CommandResult putSystemString(String key, String value) throws IOException {
        return putString("system", key, value);
    }

    public static CommandResult putSecureString(String key, String value) throws IOException {
        return putString("secure", key, value);
    }

    public static CommandResult putSystemInt(String key, int value) throws IOException {
        return putString("system", key, String.valueOf(value));
    }

    public static CommandResult putSecureInt(String key, int value) throws IOException {
        return putString("secure", key, String.valueOf(value));
    }

    public static String getSystemString(String key) throws IOException {
        return getString("system", key);
    }

    public static String getSecureString(String key) throws IOException {
        return getString("secure", key);
    }

    private static CommandResult putString(String namespace, String key, String value)
            throws IOException {
        return run("settings", "--user", "0", "put", namespace, key, value);
    }

    private static String getString(String namespace, String key) throws IOException {
        CommandResult result = run("settings", "--user", "0", "get", namespace, key);
        if (!result.isSuccess()) {
            throw new IOException(result.errorMessage());
        }
        return result.getStdout();
    }

    public static String describeStatus() {
        if (!isConnected()) {
            return "Shizuku: 未连接";
        }
        StringBuilder builder = new StringBuilder("Shizuku: 已连接");
        builder.append(", ").append(isAuthorized() ? "已授权" : "未授权");
        if (isPreV11()) {
            builder.append(", pre-v11");
        }
        if (shouldShowRequestPermissionRationale()) {
            builder.append(", 需要重新授权");
        }
        int version = safeVersion();
        if (version > 0) {
            builder.append(", version=").append(version);
        }
        int uid = safeUid();
        if (uid >= 0) {
            builder.append(", uid=").append(uid);
        }
        String seContext = safeSeContext();
        if (seContext != null && !seContext.isEmpty()) {
            builder.append(", context=").append(seContext);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.append(", SDK=").append(Build.VERSION.SDK_INT);
        }
        return builder.toString();
    }

    private static int safeVersion() {
        try {
            return Shizuku.getVersion();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static int safeUid() {
        try {
            return Shizuku.getUid();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String safeSeContext() {
        try {
            return Shizuku.getSELinuxContext();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CommandResult run(String... command) throws IOException {
        if (!isAuthorized()) {
            throw new IOException("Shizuku 未授权");
        }
        try {
            Process process = (Process) NEW_PROCESS_METHOD.invoke(null, (Object) command, null, null);
            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());
            int exitCode = process.waitFor();
            return new CommandResult(command, exitCode, stdout, stderr);
        } catch (ReflectiveOperationException e) {
            throw new IOException("无法调用 Shizuku 进程执行命令：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待 Shizuku 命令执行被中断", e);
        }
    }

    private static Method resolveNewProcessMethod() {
        try {
            Method method = Shizuku.class.getDeclaredMethod("newProcess",
                    String[].class, String[].class, String.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Shizuku API 缺少 newProcess 方法", e);
        }
    }

    private static String readAll(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString().trim();
    }

    public static final class CommandResult {
        private final String[] command;
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        CommandResult(String[] command, int exitCode, String stdout, String stderr) {
            this.command = command;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        public String errorMessage() {
            StringBuilder builder = new StringBuilder();
            builder.append("exit=").append(exitCode);
            if (stderr != null && !stderr.isEmpty()) {
                builder.append(", stderr=").append(stderr);
            }
            if (stdout != null && !stdout.isEmpty()) {
                builder.append(", stdout=").append(stdout);
            }
            return builder.toString();
        }

        public String commandLine() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < command.length; i++) {
                if (i > 0) {
                    builder.append(' ');
                }
                builder.append(command[i]);
            }
            return builder.toString();
        }

        public String getStdout() {
            return stdout == null ? "" : stdout;
        }
    }
}
