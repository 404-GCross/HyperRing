package io.github.gcross.hyperring.shizuku;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Field;
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
        StringBuilder detail = new StringBuilder();
        Object activityManager = activityManager();
        Object provider = null;
        try {
            provider = getContentProviderExternal(activityManager, "settings");
            if (provider == null) {
                return new WriteResult(false, "provider=null");
            }

            Bundle putExtras = new Bundle();
            putExtras.putString("value", value);
            putExtras.putInt("_user", 0);
            Bundle putReply = providerCall(provider, "settings", "PUT_" + namespace, key,
                    putExtras);
            detail.append("providerCall=ok");
            if (putReply != null && !putReply.isEmpty()) {
                detail.append(", putReply=").append(putReply);
            }
        } catch (Exception e) {
            detail.append("providerCall=").append(e.getClass().getSimpleName())
                    .append(":").append(e.getMessage());
        } finally {
            removeContentProviderExternal(activityManager, "settings");
        }

        String actual = readSetting(namespace, key);
        detail.append(", actual=").append(actual);
        return new WriteResult(value.equals(actual), detail.toString());
    }

    private static String readSetting(String namespace, String key) {
        Object activityManager = null;
        Object provider = null;
        try {
            activityManager = activityManager();
            provider = getContentProviderExternal(activityManager, "settings");
            if (provider == null) {
                return null;
            }

            Bundle extras = new Bundle();
            extras.putInt("_user", 0);
            Bundle reply = providerCall(provider, "settings", "GET_" + namespace, key, extras);
            if (reply == null) {
                return null;
            }
            return reply.getString("value");
        } catch (Exception e) {
            return "read:" + e.getClass().getSimpleName() + ":" + rootMessage(e);
        } finally {
            if (activityManager != null) {
                removeContentProviderExternal(activityManager, "settings");
            }
        }
    }

    private static Object activityManager() throws Exception {
        try {
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            Method getService = activityManagerClass.getDeclaredMethod("getService");
            getService.setAccessible(true);
            return getService.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getService = serviceManagerClass.getDeclaredMethod("getService", String.class);
            Object activityBinder = getService.invoke(null, "activity");
            Class<?> stubClass = Class.forName("android.app.IActivityManager$Stub");
            Method asInterface = stubClass.getDeclaredMethod("asInterface", IBinder.class);
            return asInterface.invoke(null, activityBinder);
        }
    }

    private static Object getContentProviderExternal(Object activityManager, String authority)
            throws Exception {
        Method method = findMethod(activityManager.getClass(), "getContentProviderExternal",
                String.class, int.class, IBinder.class, String.class);
        Object holder = method.invoke(activityManager, authority, 0, null,
                "io.github.gcross.hyperring");
        if (holder == null) {
            return null;
        }
        Field providerField = holder.getClass().getField("provider");
        return providerField.get(holder);
    }

    private static void removeContentProviderExternal(Object activityManager, String authority) {
        try {
            Method method = findMethod(activityManager.getClass(), "removeContentProviderExternal",
                    String.class, IBinder.class);
            method.invoke(activityManager, authority, null);
        } catch (Exception ignored) {
        }
    }

    private static Bundle providerCall(Object provider, String authority, String methodName,
            String arg, Bundle extras) throws Exception {
        for (Method method : provider.getClass().getMethods()) {
            if (!"call".equals(method.getName())) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            try {
                if (types.length == 5
                        && "android.content.AttributionSource".equals(types[0].getName())) {
                    method.setAccessible(true);
                    return (Bundle) method.invoke(provider, attributionSource(), authority,
                            methodName, arg, extras);
                }
                if (types.length == 6 && types[0] == String.class) {
                    method.setAccessible(true);
                    return (Bundle) method.invoke(provider, null, null, authority, methodName,
                            arg, extras);
                }
                if (types.length == 5 && types[0] == String.class) {
                    method.setAccessible(true);
                    return (Bundle) method.invoke(provider, null, authority, methodName, arg,
                            extras);
                }
                if (types.length == 4 && types[0] == String.class) {
                    method.setAccessible(true);
                    return (Bundle) method.invoke(provider, null, methodName, arg, extras);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new NoSuchMethodException("IContentProvider.call");
    }

    private static Object attributionSource() throws Exception {
        Class<?> builderClass = Class.forName("android.content.AttributionSource$Builder");
        Object builder = builderClass.getConstructor(int.class).newInstance(android.system.Os.getuid());
        try {
            Method setPackageName = builderClass.getDeclaredMethod("setPackageName", String.class);
            setPackageName.invoke(builder, "io.github.gcross.hyperring");
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Method setAttributionTag = builderClass.getDeclaredMethod("setAttributionTag",
                    String.class);
            setAttributionTag.invoke(builder, (String) null);
        } catch (NoSuchMethodException ignored) {
        }
        Method build = builderClass.getDeclaredMethod("build");
        return build.invoke(builder);
    }

    private static Method findMethod(Class<?> startClass, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> type = startClass;
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.toString() : message;
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
