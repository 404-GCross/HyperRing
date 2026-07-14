package io.github.gcross.hyperring.ringtone;

public final class RingtoneApplyResult {
    public enum Status {
        SUCCESS,
        NEED_WRITE_SETTINGS_PERMISSION,
        NEED_SHIZUKU_PERMISSION,
        UNSUPPORTED,
        FAILED
    }

    private final Status status;
    private final String message;

    private RingtoneApplyResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public static RingtoneApplyResult success(String message) {
        return new RingtoneApplyResult(Status.SUCCESS, message);
    }

    public static RingtoneApplyResult needSettingsPermission() {
        return new RingtoneApplyResult(Status.NEED_WRITE_SETTINGS_PERMISSION,
                "需要允许 HyperRing 修改系统设置，才能应用铃声。");
    }

    public static RingtoneApplyResult needShizukuPermission(String message) {
        return new RingtoneApplyResult(Status.NEED_SHIZUKU_PERMISSION, message);
    }

    public static RingtoneApplyResult unsupported(String message) {
        return new RingtoneApplyResult(Status.UNSUPPORTED, message);
    }

    public static RingtoneApplyResult failed(String message) {
        return new RingtoneApplyResult(Status.FAILED, message);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
