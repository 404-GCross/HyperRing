package io.github.gcross.hyperring.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    public static CommandResult cmdPutSystemString(String key, String value) throws IOException {
        return cmdPutString("system", key, value);
    }

    public static CommandResult cmdPutSecureString(String key, String value) throws IOException {
        return cmdPutString("secure", key, value);
    }

    public static CommandResult servicePutSystemString(Context context, String key, String value)
            throws IOException {
        return servicePutString(context, "system", key, value);
    }

    public static CommandResult servicePutSecureString(Context context, String key, String value)
            throws IOException {
        return servicePutString(context, "secure", key, value);
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

    public static CommandResult updateSystemString(String key, String value) throws IOException {
        if (!canUseContentBinding(value)) {
            return CommandResult.skipped(new String[]{"content", "update", "system", key},
                    "content 命令的 --bind 不能可靠处理包含冒号的值");
        }
        return updateString("system", key, value);
    }

    public static CommandResult updateSecureString(String key, String value) throws IOException {
        if (!canUseContentBinding(value)) {
            return CommandResult.skipped(new String[]{"content", "update", "secure", key},
                    "content 命令的 --bind 不能可靠处理包含冒号的值");
        }
        return updateString("secure", key, value);
    }

    public static CommandResult rebuildSystemString(String key, String value) throws IOException {
        if (!canUseContentBinding(value)) {
            return CommandResult.skipped(new String[]{"content", "rebuild", "system", key},
                    "已跳过：value 包含冒号，避免 delete 成功但 insert 失败");
        }
        return rebuildString("system", key, value);
    }

    public static CommandResult rebuildSecureString(String key, String value) throws IOException {
        if (!canUseContentBinding(value)) {
            return CommandResult.skipped(new String[]{"content", "rebuild", "secure", key},
                    "已跳过：value 包含冒号，避免 delete 成功但 insert 失败");
        }
        return rebuildString("secure", key, value);
    }

    public static CommandResult callPutSystemString(String key, String value) throws IOException {
        if (!canUseContentBinding(value)) {
            return CommandResult.skipped(new String[]{"content", "call", "system", key},
                    "content 命令的 --extra 不能可靠处理包含冒号的值");
        }
        return callPutString("system", key, value);
    }

    public static CommandResult callPutSecureString(String key, String value) throws IOException {
        if (!canUseContentBinding(value)) {
            return CommandResult.skipped(new String[]{"content", "call", "secure", key},
                    "content 命令的 --extra 不能可靠处理包含冒号的值");
        }
        return callPutString("secure", key, value);
    }

    private static CommandResult putString(String namespace, String key, String value)
            throws IOException {
        return run("settings", "--user", "0", "put", namespace, key, value);
    }

    private static CommandResult cmdPutString(String namespace, String key, String value)
            throws IOException {
        return run("cmd", "settings", "put", "--user", "0", namespace, key, value);
    }

    private static CommandResult servicePutString(Context context, String namespace, String key,
            String value) throws IOException {
        if (!isAuthorized()) {
            throw new IOException("Shizuku 未授权");
        }

        String[] command = new String[]{"shizuku-service", "put", namespace, key};
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<IBinder> binderRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        ComponentName componentName = new ComponentName(context.getPackageName(),
                SettingsUserService.class.getName());
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(componentName)
                .daemon(false)
                .processNameSuffix("settings")
                .debuggable(false)
                .version(1);
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binderRef.set(service);
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                latch.countDown();
            }

            @Override
            public void onBindingDied(ComponentName name) {
                errorRef.set(new IOException("Shizuku UserService 绑定已死亡"));
                latch.countDown();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                errorRef.set(new IOException("Shizuku UserService 返回空绑定"));
                latch.countDown();
            }
        };

        try {
            Shizuku.bindUserService(args, connection);
            if (!latch.await(10, TimeUnit.SECONDS)) {
                return new CommandResult(command, 1, "", "等待 Shizuku UserService 超时");
            }
            Exception bindError = errorRef.get();
            if (bindError != null) {
                return new CommandResult(command, 1, "", bindError.getMessage());
            }
            IBinder binder = binderRef.get();
            if (binder == null) {
                return new CommandResult(command, 1, "", "未获得 Shizuku UserService Binder");
            }
            return transactPutSetting(command, binder, namespace, key, value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待 Shizuku UserService 被中断", e);
        } catch (Exception e) {
            throw new IOException("调用 Shizuku UserService 失败：" + e.getMessage(), e);
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true);
            } catch (Exception ignored) {
            }
        }
    }

    private static CommandResult transactPutSetting(String[] command, IBinder binder,
            String namespace, String key, String value) throws IOException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeString(namespace);
            data.writeString(key);
            data.writeString(value);
            boolean transacted = binder.transact(SettingsUserService.TRANSACTION_PUT_SETTING,
                    data, reply, 0);
            if (!transacted) {
                return new CommandResult(command, 1, "", "Binder transact 返回 false");
            }
            reply.readException();
            int exitCode = reply.readInt();
            String message = reply.readString();
            return new CommandResult(command, exitCode, message, "");
        } catch (Exception e) {
            throw new IOException("Shizuku UserService transact 失败：" + e.getMessage(), e);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static CommandResult updateString(String namespace, String key, String value)
            throws IOException {
        return run("content", "update", "--uri", settingsUri(namespace), "--user", "0",
                "--bind", "value:s:" + value, "--where", "name='" + escapeSql(key) + "'");
    }

    private static CommandResult rebuildString(String namespace, String key, String value)
            throws IOException {
        CommandResult delete = run("content", "delete", "--uri", settingsUri(namespace),
                "--user", "0", "--where", "name='" + escapeSql(key) + "'");
        CommandResult insert = run("content", "insert", "--uri", settingsUri(namespace),
                "--user", "0", "--bind", "name:s:" + key, "--bind", "value:s:" + value);
        return CommandResult.combine(new String[]{"content", "rebuild", namespace, key},
                delete, insert);
    }

    private static CommandResult callPutString(String namespace, String key, String value)
            throws IOException {
        return run("content", "call", "--uri", settingsUri(namespace), "--user", "0",
                "--method", "PUT_" + namespace, "--arg", key,
                "--extra", "value:s:" + value, "--extra", "_user:i:0");
    }

    private static String getString(String namespace, String key) throws IOException {
        CommandResult result = run("settings", "--user", "0", "get", namespace, key);
        if (!result.isSuccess()) {
            throw new IOException(result.errorMessage());
        }
        return result.getStdout();
    }

    private static String settingsUri(String namespace) {
        return "content://settings/" + namespace;
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private static boolean canUseContentBinding(String value) {
        return value == null || !value.contains(":");
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

        static CommandResult combine(String[] command, CommandResult first, CommandResult second) {
            String stdout = "delete: " + first.errorMessage() + "; insert: " + second.errorMessage();
            return new CommandResult(command,
                    first.isSuccess() && second.isSuccess() ? 0 : 1,
                    stdout, "");
        }

        static CommandResult skipped(String[] command, String reason) {
            return new CommandResult(command, 2, reason, "");
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
