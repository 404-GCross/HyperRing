package io.github.gcross.hyperring.shizuku;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Method;

public final class SettingsUserService extends Binder {
    public static final int TRANSACTION_PUT_SETTING = IBinder.FIRST_CALL_TRANSACTION;

    public SettingsUserService() {
    }

    public void destroy() {
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code != TRANSACTION_PUT_SETTING) {
            try {
                return super.onTransact(code, data, reply, flags);
            } catch (Exception e) {
                reply.writeException(e);
                return true;
            }
        }

        String namespace = data.readString();
        String key = data.readString();
        String value = data.readString();
        try {
            WriteResult result = putSetting(namespace, key, value);
            reply.writeNoException();
            reply.writeInt(result.success ? 0 : 1);
            reply.writeString(result.message);
        } catch (Exception e) {
            reply.writeException(e);
        }
        return true;
    }

    private static WriteResult putSetting(String namespace, String key, String value)
            throws Exception {
        Context context = systemContext();
        ContentResolver resolver = context.getContentResolver();
        Uri uri = Uri.parse("content://settings/" + namespace);

        StringBuilder detail = new StringBuilder();
        try {
            Bundle extras = new Bundle();
            extras.putString("value", value);
            extras.putInt("_user", 0);
            Bundle reply = resolver.call(uri, "PUT_" + namespace, key, extras);
            detail.append("call=ok");
            if (reply != null && !reply.isEmpty()) {
                detail.append(", callReply=").append(reply);
            }
        } catch (Exception e) {
            detail.append("call=").append(e.getClass().getSimpleName())
                    .append(":").append(e.getMessage());
        }

        ContentValues updateValues = new ContentValues();
        updateValues.put("value", value);
        try {
            int updated = resolver.update(uri, updateValues, "name=?", new String[]{key});
            detail.append(", update=").append(updated);
        } catch (Exception e) {
            detail.append(", update=").append(e.getClass().getSimpleName())
                    .append(":").append(e.getMessage());
        }

        String actual = readSetting(resolver, uri, key);
        if (!value.equals(actual)) {
            ContentValues insertValues = new ContentValues();
            insertValues.put("name", key);
            insertValues.put("value", value);
            try {
                Uri inserted = resolver.insert(uri, insertValues);
                detail.append(", insert=").append(inserted);
            } catch (Exception e) {
                detail.append(", insert=").append(e.getClass().getSimpleName())
                        .append(":").append(e.getMessage());
            }
            actual = readSetting(resolver, uri, key);
        }

        detail.append(", actual=").append(actual);
        return new WriteResult(value.equals(actual), detail.toString());
    }

    private static String readSetting(ContentResolver resolver, Uri uri, String key) {
        try (Cursor cursor = resolver.query(uri, new String[]{"value"},
                "name=?", new String[]{key}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            return "read:" + e.getClass().getSimpleName() + ":" + e.getMessage();
        }
        return null;
    }

    private static Context systemContext() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
        Object activityThread = currentActivityThread.invoke(null);
        if (activityThread == null) {
            Method systemMain = activityThreadClass.getDeclaredMethod("systemMain");
            activityThread = systemMain.invoke(null);
        }
        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        return (Context) getSystemContext.invoke(activityThread);
    }

    private static final class WriteResult {
        private final boolean success;
        private final String message;

        private WriteResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
