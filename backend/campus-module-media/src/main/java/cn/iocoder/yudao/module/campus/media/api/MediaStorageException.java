package cn.iocoder.yudao.module.campus.media.api;

public class MediaStorageException extends RuntimeException {

    public enum Reason { UNAVAILABLE, NOT_FOUND, TOO_LARGE, ALREADY_EXISTS }

    private final Reason reason;

    public MediaStorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public MediaStorageException(Reason reason, String message) {
        this(reason, message, null);
    }

    public Reason reason() {
        return reason;
    }
}
