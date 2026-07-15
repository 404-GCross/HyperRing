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
            safePutString(context, sim1Key, value, report, true);
            safePutString(context, SYSTEM_RINGTONE_KEY, value, report, false);
            safeWriteDisplayPath(context, SIM1_DISPLAY_KEY, ringtonePath, report);
            safeWriteDisplayPath(context, SIM1_LEGACY_DISPLAY_KEY, ringtonePath, report);
        }
        if (sim2Required) {
            safePutString(context, sim2Key, value, report, true);
            safeWriteDisplayPath(context, SIM2_DISPLAY_KEY, ringtonePath, report);
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

    private static void safePutString(Context context, String key, String value, WriteReport report,
            boolean required) {
        try {
            ShizukuShell.CommandResult result = ShizukuShell.putSystemString(key, value);
            boolean ok = result.isSuccess();
            if (required && ok) {
                report.requiredSuccesses++;
            }
            if (!ok) {
                report.warn(key, result.errorMessage());
            }
        } catch (Exception e) {
            report.warn(key, e.getMessage());
        }
    }

    private static void safePutDisplayString(Context context, String key, String value,
            WriteReport report) {
        ShizukuShell.CommandResult systemResult = null;
        ShizukuShell.CommandResult secureResult = null;
        Exception systemError = null;
        Exception secureError = null;

        try {
            systemResult = ShizukuShell.putSystemString(key, value);
        } catch (Exception e) {
            systemError = e;
        }
        try {
            secureResult = ShizukuShell.putSecureString(key, value);
        } catch (Exception e) {
            secureError = e;
        }

        boolean systemOk = systemResult != null && systemResult.isSuccess();
        boolean secureOk = secureResult != null && secureResult.isSuccess();
        if (!systemOk && !secureOk) {
            report.warn(key, "system: " + describeWriteFailure(systemResult, systemError)
                    + "; secure: " + describeWriteFailure(secureResult, secureError));
        }
    }

    private static void safePutInt(Context context, String key, int value, WriteReport report,
            boolean required) {
        try {
            ShizukuShell.CommandResult result = ShizukuShell.putSystemInt(key, value);
            boolean ok = result.isSuccess();
            if (required && ok) {
                report.requiredSuccesses++;
            }
            if (!ok) {
                report.warn(key, result.errorMessage());
            }
        } catch (Exception e) {
            report.warn(key, e.getMessage());
        }
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
