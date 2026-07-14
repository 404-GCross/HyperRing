package io.github.gcross.hyperring.media;

import android.net.Uri;

public final class ImportedRingtone {
    private final Uri uri;
    private final String displayName;
    private final long bytesWritten;

    public ImportedRingtone(Uri uri, String displayName, long bytesWritten) {
        this.uri = uri;
        this.displayName = displayName;
        this.bytesWritten = bytesWritten;
    }

    public Uri getUri() {
        return uri;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }
}
