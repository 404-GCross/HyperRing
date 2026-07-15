package io.github.gcross.hyperring.ringtone;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import io.github.gcross.hyperring.shizuku.ShizukuShell;

final class HyperOsRingtoneSetter {
    private static final String PREFS = "hyperos_ringtone_keys";
    private static final String SIM1_KEY = "sim1_key";
    private static final String SIM2_KEY = "sim2_key";
    private static final String DEFAULT_SIM1_KEY = "ringtone_sound_slot_1";
    private static final String DEFAULT_SIM2_KEY = "ringtone_sound_slot_2";
    private static final String UNIFORM_KEY = "ringtone_sound_use_uniform";
    private static final String SYSTEM_RINGTONE_KEY = "ringtone";
    private static final String SIM1_DISPLAY_KEY = "more_ringtone_value_call64";
    private static final String SIM1_LEGACY_DISPLAY_KEY = "more_ringtone_value_call1";
    private static final String SIM2_DISPLAY_KEY = "more_ringtone_value_call128";
    private static final String SIM1_MIUI_DISPLAY_KEY = "miui_ringtone_sim1";
    private static final String SIM2_MIUI_DISPLAY_KEY = "miui_ringtone_sim2";

    private HyperOsRingtoneSetter() {
    }

    static RingtoneApplyResult apply(Context context, Uri ringtoneUri, String ringtonePath,
            SimTarget target) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String sim1Key = resolveSim1Key(context, prefs);
        String sim2Key = resolveSim2Key(context, prefs);

        if ((target == SimTarget.SIM_1 || target == SimTarget.BOTH) && isBlank(sim1Key)) {
            return RingtoneApplyResult.unsupported("HyperOS SIM1 私有 key 尚未校准。");
        }
        if ((target == SimTarget.SIM_2 || target == SimTarget.BOTH) && isBlank(sim2Key)) {
            return RingtoneApplyResult.unsupported("HyperOS SIM2 私有 key 尚未校准。");
        }

        String value = ringtoneUri.toString();
        WriteReport report = new WriteReport();
        safePutInt(context, UNIFORM_KEY, 0, report, false);

        boolean sim1Required = target == SimTarget.SIM_1 || target == SimTarget.BOTH;
        boolean sim2Required = target == SimTarget.SIM_2 || target == SimTarget.BOTH;

        if (sim1Required) {
            boolean sim1Applied = safePutPrimaryString(context, sim1Key, value, report, true,
                    false);
            if (sim1Applied) {
                safePutSystemString(context, SYSTEM_RINGTONE_KEY, value, report);
                safeWriteDisplayPath(context, SIM1_DISPLAY_KEY, ringtonePath, report);
                safeWriteDisplayPath(context, SIM1_LEGACY_DISPLAY_KEY, ringtonePath, report);
                safeWriteDisplayPath(context, SIM1_MIUI_DISPLAY_KEY, ringtonePath, report);
            } else {
                report.warn("SIM1 display sync", "主铃声 key 未通过读回校验，已跳过显示缓存同步");
            }
        }
        if (sim2Required) {
            boolean sim2Applied = safePutPrimaryString(context, sim2Key, value, report, true,
                    false);
            if (sim2Applied) {
                safeWriteDisplayPath(context, SIM2_DISPLAY_KEY, ringtonePath, report);
                safeWriteDisplayPath(context, SIM2_MIUI_DISPLAY_KEY, ringtonePath, report);
            } else {
                report.warn("SIM2 display sync", "主铃声 key 未通过读回校验，已跳过显示缓存同步");
            }
        }

        int requiredCount = 0;
        if (sim1Required) {
            requiredCount++;
        }
        if (sim2Required) {
            requiredCount++;
        }

        if (report.requiredSuccesses >= requiredCount) {
            String message = "已写入 HyperOS 双卡铃声主 key。";
            if (report.hasWarnings()) {
                message += "\n部分辅助 key 写入失败，不影响铃声主设置：\n" + report.warnings;
            }
            return RingtoneApplyResult.success(message);
        }
        return RingtoneApplyResult.failed("写入 HyperOS 铃声主 key 失败：\n" + report.warnings);
    }

    static void saveKeys(Context context, String sim1Key, String sim2Key) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(SIM1_KEY, sim1Key == null ? "" : sim1Key.trim())
                .putString(SIM2_KEY, sim2Key == null ? "" : sim2Key.trim())
                .apply();
    }

    static String describeKeys(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String sim1Key = resolveSim1Key(context, prefs);
        String sim2Key = resolveSim2Key(context, prefs);
        return "HyperOS key: SIM1=" + emptyLabel(sim1Key) + ", SIM2=" + emptyLabel(sim2Key);
    }

    private static String resolveSim1Key(Context context, SharedPreferences prefs) {
        String configured = prefs.getString(SIM1_KEY, "");
        if (!isBlank(configured)) {
            return configured;
        }
        return isXiaomiDevice() ? DEFAULT_SIM1_KEY : "";
    }

    private static String resolveSim2Key(Context context, SharedPreferences prefs) {
        String configured = prefs.getString(SIM2_KEY, "");
        if (!isBlank(configured)) {
            return configured;
        }
        return isXiaomiDevice() ? DEFAULT_SIM2_KEY : "";
    }

    private static boolean isXiaomiDevice() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        String brand = Build.BRAND == null ? "" : Build.BRAND.toLowerCase();
        return manufacturer.contains("xiaomi") || brand.contains("xiaomi")
                || brand.contains("redmi") || brand.contains("poco");
    }

    private static void safeWriteDisplayPath(Context context, String key, String ringtonePath,
            WriteReport report) {
        if (!isBlank(ringtonePath)) {
            safePutDisplayString(context, key, ringtonePath, report);
        }
    }

    private static boolean safePutPrimaryString(Context context, String key, String value,
            WriteReport report, boolean required, boolean preferSecure) {
        NamespaceWriteResult system = writeSystemString(key, value);
        NamespaceWriteResult secure = writeSecureString(key, value);
        NamespaceWriteResult preferred = preferSecure ? secure : system;
        boolean ok = value.equals(preferred.actual);
        if (required && ok) {
            report.requiredSuccesses++;
        }
        if (!ok) {
            String preferredName = preferSecure ? "secure" : "system";
            report.warn(key, "首选表=" + preferredName
                    + ", system={" + describeNamespaceWrite(system) + "}"
                    + ", secure={" + describeNamespaceWrite(secure) + "}");
        }
        return ok;
    }

    private static void safePutSystemString(Context context, String key, String value,
            WriteReport report) {
        NamespaceWriteResult system = writeSystemString(key, value);
        if (!value.equals(system.actual)) {
            report.warn(key, "system={" + describeNamespaceWrite(system) + "}");
        }
    }

    private static void safePutDisplayString(Context context, String key, String value,
            WriteReport report) {
        NamespaceWriteResult system = writeSystemString(key, value);
        NamespaceWriteResult secure = writeSecureString(key, value);
        if (!value.equals(system.actual) && !value.equals(secure.actual)) {
            report.warn(key, "system={" + describeNamespaceWrite(system)
                    + "}; secure={" + describeNamespaceWrite(secure) + "}");
        }
    }

    private static void safePutInt(Context context, String key, int value, WriteReport report,
            boolean required) {
        String stringValue = String.valueOf(value);
        NamespaceWriteResult system = writeSystemString(key, stringValue);
        NamespaceWriteResult secure = writeSecureString(key, stringValue);
        boolean ok = stringValue.equals(system.actual);
        if (required && ok) {
            report.requiredSuccesses++;
        }
        if (!ok) {
            report.warn(key, "system={" + describeNamespaceWrite(system)
                    + "}; secure={" + describeNamespaceWrite(secure) + "}");
        }
    }

    private static NamespaceWriteResult writeSystemString(String key, String value) {
        NamespaceWriteResult result = new NamespaceWriteResult();
        try {
            result.callPut = ShizukuShell.callPutSystemString(key, value);
        } catch (Exception e) {
            result.callError = e;
        }
        try {
            result.put = ShizukuShell.putSystemString(key, value);
        } catch (Exception e) {
            result.putError = e;
        }
        try {
            result.cmdPut = ShizukuShell.cmdPutSystemString(key, value);
        } catch (Exception e) {
            result.cmdPutError = e;
        }
        try {
            result.update = ShizukuShell.updateSystemString(key, value);
        } catch (Exception e) {
            result.updateError = e;
        }
        try {
            result.actual = ShizukuShell.getSystemString(key);
        } catch (Exception e) {
            result.readError = e;
        }
        if (!value.equals(result.actual)) {
            try {
                result.rebuild = ShizukuShell.rebuildSystemString(key, value);
            } catch (Exception e) {
                result.rebuildError = e;
            }
            try {
                result.actualAfterRebuild = ShizukuShell.getSystemString(key);
                result.actual = result.actualAfterRebuild;
            } catch (Exception e) {
                result.readAfterRebuildError = e;
            }
        }
        return result;
    }

    private static NamespaceWriteResult writeSecureString(String key, String value) {
        NamespaceWriteResult result = new NamespaceWriteResult();
        try {
            result.callPut = ShizukuShell.callPutSecureString(key, value);
        } catch (Exception e) {
            result.callError = e;
        }
        try {
            result.put = ShizukuShell.putSecureString(key, value);
        } catch (Exception e) {
            result.putError = e;
        }
        try {
            result.cmdPut = ShizukuShell.cmdPutSecureString(key, value);
        } catch (Exception e) {
            result.cmdPutError = e;
        }
        try {
            result.update = ShizukuShell.updateSecureString(key, value);
        } catch (Exception e) {
            result.updateError = e;
        }
        try {
            result.actual = ShizukuShell.getSecureString(key);
        } catch (Exception e) {
            result.readError = e;
        }
        if (!value.equals(result.actual)) {
            try {
                result.rebuild = ShizukuShell.rebuildSecureString(key, value);
            } catch (Exception e) {
                result.rebuildError = e;
            }
            try {
                result.actualAfterRebuild = ShizukuShell.getSecureString(key);
                result.actual = result.actualAfterRebuild;
            } catch (Exception e) {
                result.readAfterRebuildError = e;
            }
        }
        return result;
    }

    private static String describeNamespaceWrite(NamespaceWriteResult result) {
        return "call=" + describeWriteFailure(result.callPut, result.callError)
                + ", put=" + describeWriteFailure(result.put, result.putError)
                + ", cmdPut=" + describeWriteFailure(result.cmdPut, result.cmdPutError)
                + ", update=" + describeWriteFailure(result.update, result.updateError)
                + ", rebuild=" + describeWriteFailure(result.rebuild, result.rebuildError)
                + ", actual=" + result.actual
                + (result.actualAfterRebuild == null ? ""
                        : ", actualAfterRebuild=" + result.actualAfterRebuild)
                + (result.readError == null ? "" : ", read=" + result.readError.getMessage())
                + (result.readAfterRebuildError == null ? ""
                        : ", readAfterRebuild=" + result.readAfterRebuildError.getMessage());
    }

    private static final class NamespaceWriteResult {
        private ShizukuShell.CommandResult callPut;
        private ShizukuShell.CommandResult put;
        private ShizukuShell.CommandResult cmdPut;
        private ShizukuShell.CommandResult update;
        private ShizukuShell.CommandResult rebuild;
        private Exception callError;
        private Exception putError;
        private Exception cmdPutError;
        private Exception updateError;
        private Exception rebuildError;
        private Exception readError;
        private Exception readAfterRebuildError;
        private String actual;
        private String actualAfterRebuild;
    }

    private static final class WriteReport {
        private int requiredSuccesses;
        private final StringBuilder warnings = new StringBuilder();

        private void warn(String key, String message) {
            warnings.append("- ").append(key).append(": ").append(message).append('\n');
        }

        private boolean hasWarnings() {
            return warnings.length() > 0;
        }
    }

    private static String describeWriteFailure(ShizukuShell.CommandResult result, Exception error) {
        if (result != null) {
            return result.errorMessage();
        }
        return error == null ? "未执行" : error.getMessage();
    }

    private static String emptyLabel(String value) {
        return isBlank(value) ? "未校准" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
