package cn.iocoder.yudao.module.campus.common.idempotency;

import java.time.Instant;

/** Persistence port for M00.04. Implementations must lock the row before returning it. */
public interface IdempotencyRepository {

    IdempotencyReservation reserve(IdempotencyKey key, String requestHash, Instant expiresAt,
                                   String processingToken);

    void complete(IdempotencyKey key, String processingToken, String businessReference,
                  long resultVersion, String resultJson);
}
