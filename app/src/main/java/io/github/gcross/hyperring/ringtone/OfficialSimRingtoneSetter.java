package io.github.gcross.hyperring.ringtone;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class OfficialSimRingtoneSetter {
    private OfficialSimRingtoneSetter() {
    }

    static RingtoneApplyResult apply(Context context, Uri ringtoneUri, SimTarget target) {
        try {
            Method method = RingtoneManager.class.getMethod("setRingtoneUri",
                    Context.class, Uri.class, PhoneAccountHandle.class);
            List<PhoneAccountHandle> accounts = getCallCapableAccounts(context);
            if (accounts.isEmpty()) {
                return RingtoneApplyResult.unsupported("未发现可用于来电的 SIM/通话账户。");
            }

            if (target == SimTarget.SIM_1) {
                return applyToIndex(context, ringtoneUri, accounts, method, 0);
            }
            if (target == SimTarget.SIM_2) {
                return applyToIndex(context, ringtoneUri, accounts, method, 1);
            }
            if (target == SimTarget.BOTH) {
                RingtoneApplyResult first = applyToIndex(context, ringtoneUri, accounts, method, 0);
                RingtoneApplyResult second = applyToIndex(context, ringtoneUri, accounts, method, 1);
                if (first.getStatus() == RingtoneApplyResult.Status.SUCCESS
                        && second.getStatus() == RingtoneApplyResult.Status.SUCCESS) {
                    return RingtoneApplyResult.success("已通过系统接口应用到 SIM 1 和 SIM 2。");
                }
                return RingtoneApplyResult.failed(first.getMessage() + "\n" + second.getMessage());
            }
            return RingtoneApplyResult.unsupported("官方双卡接口不处理该目标。");
        } catch (NoSuchMethodException e) {
            return RingtoneApplyResult.unsupported("当前系统没有公开的按 SIM 设置铃声接口。");
        } catch (SecurityException e) {
            return RingtoneApplyResult.failed("缺少读取通话账户权限：" + e.getMessage());
        } catch (Exception e) {
            return RingtoneApplyResult.failed("官方双卡接口设置失败：" + e.getMessage());
        }
    }

    public static List<String> describeAccounts(Context context) {
        List<String> labels = new ArrayList<>();
        try {
            List<PhoneAccountHandle> accounts = getCallCapableAccounts(context);
            for (int i = 0; i < accounts.size(); i++) {
                PhoneAccountHandle handle = accounts.get(i);
                labels.add("SIM " + (i + 1) + ": " + handle.getComponentName().flattenToShortString()
                        + " / " + handle.getId());
            }
        } catch (Exception e) {
            labels.add("读取通话账户失败：" + e.getMessage());
        }
        return labels;
    }

    private static RingtoneApplyResult applyToIndex(Context context, Uri ringtoneUri,
            List<PhoneAccountHandle> accounts, Method method, int index) throws Exception {
        if (accounts.size() <= index) {
            return RingtoneApplyResult.unsupported("未发现 SIM " + (index + 1) + "。");
        }
        method.invoke(null, context, ringtoneUri, accounts.get(index));
        return RingtoneApplyResult.success("已通过系统接口应用到 SIM " + (index + 1) + "。");
    }

    private static List<PhoneAccountHandle> getCallCapableAccounts(Context context) {
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager == null) {
            return new ArrayList<>();
        }
        return telecomManager.getCallCapablePhoneAccounts();
    }
}
