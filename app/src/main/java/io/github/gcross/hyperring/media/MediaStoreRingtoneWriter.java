package io.github.gcross.hyperring.media;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class MediaStoreRingtoneWriter {
    private static final int BUFFER_SIZE = 64 * 1024;

    public ImportedRingtone importAsRingtone(Context context, Uri inputUri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String displayName = resolveDisplayName(resolver, inputUri);
        String mimeType = resolveMimeType(resolver, inputUri, displayName);
        String title = stripExtension(displayName);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.Audio.Media.TITLE, title);
        values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
        values.put(MediaStore.Audio.Media.IS_ALARM, false);
        values.put(MediaStore.Audio.Media.IS_MUSIC, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }

        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Uri outputUri = resolver.insert(collection, values);
        if (outputUri == null) {
            throw new IOException("MediaStore insert returned null");
        }

        long bytesWritten = copy(resolver, inputUri, outputUri);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues publish = new ContentValues();
            publish.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(outputUri, publish, null, null);
        }

        return new ImportedRingtone(outputUri, displayName, bytesWritten);
    }

    private long copy(ContentResolver resolver, Uri inputUri, Uri outputUri) throws IOException {
        try (InputStream input = resolver.openInputStream(inputUri);
                OutputStream output = resolver.openOutputStream(outputUri)) {
            if (input == null) {
                throw new IOException("Cannot open selected audio");
            }
            if (output == null) {
                throw new IOException("Cannot open ringtone output");
            }
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                total += read;
            }
            return total;
        }
    }

    private String resolveDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "HyperRing-" + System.currentTimeMillis() + ".m4a";
    }

    private String resolveMimeType(ContentResolver resolver, Uri uri, String displayName) {
        String mimeType = resolver.getType(uri);
        if (mimeType != null && mimeType.startsWith("audio/")) {
            return mimeType;
        }

        int dot = displayName.lastIndexOf('.');
        if (dot >= 0 && dot < displayName.length() - 1) {
            String extension = displayName.substring(dot + 1).toLowerCase(Locale.US);
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }
        return "audio/mpeg";
    }

    private String stripExtension(String displayName) {
        int dot = displayName.lastIndexOf('.');
        if (dot > 0) {
            return displayName.substring(0, dot);
        }
        return displayName;
    }
}
