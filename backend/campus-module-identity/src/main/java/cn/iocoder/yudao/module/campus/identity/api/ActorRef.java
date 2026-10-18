package cn.iocoder.yudao.module.campus.identity.api;

import java.util.Objects;

/** Authenticated server-side actor; request body identity fields must never construct this value. */
public record ActorRef(ActorType type, long id) {

    public ActorRef {
        Objects.requireNonNull(type, "type");
        if (id <= 0) {
            throw new IllegalArgumentException("actor id must be positive");
        }
    }
}
