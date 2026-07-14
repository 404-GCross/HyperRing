package io.github.gcross.hyperring.media;

import android.net.Uri;

public final class ImportedRingtone {
    private final Uri uri;
    private final String displayName;
    private final String absolutePath;
    private final long bytesWritten;

    public ImportedRingtone(Uri uri, String displayName, String absolutePath, long bytesWritten) {
        this.uri = uri;
        this.displayName = displayName;
        this.absolutePath = absolutePath;
        this.bytesWritten = bytesWritten;
    }

    public Uri getUri() {
        return uri;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }
}
