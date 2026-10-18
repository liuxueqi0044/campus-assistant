package cn.iocoder.yudao.module.campus.media.application;

import java.util.List;

/** Results are ordered by asset id to make retries independent from the caller's list ordering. */
public record MediaBindingBatchResult(List<MediaBindingResult> bindings) {
}
