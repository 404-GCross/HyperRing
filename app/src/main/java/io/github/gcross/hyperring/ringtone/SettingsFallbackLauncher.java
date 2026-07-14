package io.github.gcross.hyperring.ringtone;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public final class SettingsFallbackLauncher {
    private SettingsFallbackLauncher() {
    }

    public static void openSoundSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_SOUND_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openWriteSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
        context.startActivity(intent);
    }
}
