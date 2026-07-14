package io.github.gcross.hyperring.ringtone;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

final class HyperOsRingtoneSetter {
    private static final String PREFS = "hyperos_ringtone_keys";
    private static final String SIM1_KEY = "sim1_key";
    private static final String SIM2_KEY = "sim2_key";
    private static final String DEFAULT_SIM1_KEY = "ringtone_sound_slot_1";
    private static final String DEFAULT_SIM2_KEY = "ringtone_sound_slot_2";
    private static final String UNIFORM_KEY = "ringtone_sound_use_uniform";

    private HyperOsRingtoneSetter() {
    }

    static RingtoneApplyResult apply(Context context, Uri ringtoneUri, SimTarget target) {
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
        try {
            Settings.System.putInt(context.getContentResolver(), UNIFORM_KEY, 0);
            if (target == SimTarget.SIM_1 || target == SimTarget.BOTH) {
                Settings.System.putString(context.getContentResolver(), sim1Key, value);
                Settings.System.putString(context.getContentResolver(), Settings.System.RINGTONE,
                        value);
            }
            if (target == SimTarget.SIM_2 || target == SimTarget.BOTH) {
                Settings.System.putString(context.getContentResolver(), sim2Key, value);
            }
            return RingtoneApplyResult.success("已写入已校准的 HyperOS 双卡铃声 key。");
        } catch (Exception e) {
            return RingtoneApplyResult.failed("写入 HyperOS 私有 key 失败：" + e.getMessage());
        }
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

    private static String emptyLabel(String value) {
        return isBlank(value) ? "未校准" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
