package io.github.gcross.hyperring.diagnostics;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.Settings;

import io.github.gcross.hyperring.ringtone.OfficialSimRingtoneSetter;
import io.github.gcross.hyperring.ringtone.RingtoneApplyManager;
import io.github.gcross.hyperring.shizuku.ShizukuShell;

import java.util.List;
import java.util.Locale;

public final class DeviceDiagnostics {
    private static final String[] INTERESTING_SETTINGS = {
            "ringtone",
            "ringtone_2",
            "ringtone_slot_1",
            "ringtone_slot_2",
            "ringtone_sim1",
            "ringtone_sim2",
            "ringtone_sound_slot_1",
            "ringtone_sound_slot_2",
            "miui_ringtone_sim1",
            "miui_ringtone_sim2"
    };

    private DeviceDiagnostics() {
    }

    public static String collect(Context context) {
        StringBuilder builder = new StringBuilder();
        builder.append("Device\n");
        builder.append("Manufacturer: ").append(Build.MANUFACTURER).append('\n');
        builder.append("Brand: ").append(Build.BRAND).append('\n');
        builder.append("Model: ").append(Build.MODEL).append('\n');
        builder.append("SDK: ").append(Build.VERSION.SDK_INT).append('\n');
        builder.append("Can write settings: ").append(Settings.System.canWrite(context)).append("\n\n");
        builder.append(ShizukuShell.describeStatus()).append("\n\n");

        builder.append("Call-capable accounts\n");
        List<String> accounts = OfficialSimRingtoneSetter.describeAccounts(context);
        for (String account : accounts) {
            builder.append("- ").append(account).append('\n');
        }

        builder.append("\nKnown setting keys\n");
        for (String key : INTERESTING_SETTINGS) {
            builder.append(key).append(": ")
                    .append(String.valueOf(Settings.System.getString(context.getContentResolver(), key)))
                    .append('\n');
        }

        builder.append("\nSystem keys containing ringtone\n");
        appendRingtoneSettings(context, builder);

        builder.append("\n");
        builder.append(new RingtoneApplyManager().describeHyperOsKeys(context)).append('\n');
        return builder.toString();
    }

    private static void appendRingtoneSettings(Context context, StringBuilder builder) {
        try (Cursor cursor = context.getContentResolver().query(Settings.System.CONTENT_URI,
                new String[]{"name", "value"}, null, null, "name ASC")) {
            if (cursor == null) {
                builder.append("Settings query returned null\n");
                return;
            }
            int nameIndex = cursor.getColumnIndex("name");
            int valueIndex = cursor.getColumnIndex("value");
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                if (name != null && name.toLowerCase(Locale.US).contains("ringtone")) {
                    builder.append(name).append(": ").append(cursor.getString(valueIndex)).append('\n');
                }
            }
        } catch (Exception e) {
            builder.append("Cannot list settings: ").append(e.getMessage()).append('\n');
        }
    }
}
