package cn.iocoder.yudao.module.campus.media.application;

import java.time.Instant;

/** A persisted binding view. Time bounds always come from the first successful insert. */
public record MediaBindingResult(String assetId, String bindingId, long assetGeneration, long assetVersion,
                                 Instant validFrom, Instant expiresAt) {
}
