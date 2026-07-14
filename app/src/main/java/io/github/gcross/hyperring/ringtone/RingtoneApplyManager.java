package io.github.gcross.hyperring.ringtone;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;

import io.github.gcross.hyperring.shizuku.ShizukuShell;

public final class RingtoneApplyManager {
    public RingtoneApplyResult apply(Context context, Uri ringtoneUri, SimTarget target) {
        return apply(context, ringtoneUri, null, target);
    }

    public RingtoneApplyResult apply(Context context, Uri ringtoneUri, String ringtonePath,
            SimTarget target) {
        if (target == SimTarget.IMPORT_ONLY) {
            return RingtoneApplyResult.success("已导入系统铃声库，未修改当前铃声。");
        }

        if (target == SimTarget.SYSTEM_DEFAULT) {
            return setSystemDefault(context, ringtoneUri);
        }

        RingtoneApplyResult shizukuReady = ShizukuShell.ensureReady(context);
        if (shizukuReady != null) {
            return shizukuReady;
        }

        return HyperOsRingtoneSetter.apply(context, ringtoneUri, ringtonePath, target);
    }

    public void saveHyperOsKeys(Context context, String sim1Key, String sim2Key) {
        HyperOsRingtoneSetter.saveKeys(context, sim1Key, sim2Key);
    }

    public String describeHyperOsKeys(Context context) {
        return HyperOsRingtoneSetter.describeKeys(context);
    }

    private RingtoneApplyResult setSystemDefault(Context context, Uri ringtoneUri) {
        try {
            RingtoneManager.setActualDefaultRingtoneUri(context,
                    RingtoneManager.TYPE_RINGTONE, ringtoneUri);
            return RingtoneApplyResult.success("已设置为系统默认电话铃声。");
        } catch (Exception e) {
            return RingtoneApplyResult.failed("设置系统默认铃声失败：" + e.getMessage());
        }
    }
}
