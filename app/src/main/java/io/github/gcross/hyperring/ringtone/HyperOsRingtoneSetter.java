package io.github.gcross.hyperring.ringtone;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

final class HyperOsRingtoneSetter {
    private static final String PREFS = "hyperos_ringtone_keys";
    private static final String SIM1_KEY = "sim1_key";
    private static final String SIM2_KEY = "sim2_key";

    private HyperOsRingtoneSetter() {
    }

    static RingtoneApplyResult apply(Context context, Uri ringtoneUri, SimTarget target) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String sim1Key = prefs.getString(SIM1_KEY, "");
        String sim2Key = prefs.getString(SIM2_KEY, "");

        if ((target == SimTarget.SIM_1 || target == SimTarget.BOTH) && isBlank(sim1Key)) {
            return RingtoneApplyResult.unsupported("HyperOS SIM1 私有 key 尚未校准。");
        }
        if ((target == SimTarget.SIM_2 || target == SimTarget.BOTH) && isBlank(sim2Key)) {
            return RingtoneApplyResult.unsupported("HyperOS SIM2 私有 key 尚未校准。");
        }

        String value = ringtoneUri.toString();
        try {
            if (target == SimTarget.SIM_1 || target == SimTarget.BOTH) {
                Settings.System.putString(context.getContentResolver(), sim1Key, value);
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
        String sim1Key = prefs.getString(SIM1_KEY, "");
        String sim2Key = prefs.getString(SIM2_KEY, "");
        return "HyperOS key: SIM1=" + emptyLabel(sim1Key) + ", SIM2=" + emptyLabel(sim2Key);
    }

    private static String emptyLabel(String value) {
        return isBlank(value) ? "未校准" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
