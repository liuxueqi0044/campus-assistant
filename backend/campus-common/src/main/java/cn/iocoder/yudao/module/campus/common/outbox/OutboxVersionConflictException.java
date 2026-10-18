package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;

/** Explicit optimistic-lock failure used by the operator replay API. */
public class OutboxVersionConflictException extends CampusApiException {

    public OutboxVersionConflictException(String eventId) {
        super(CampusErrorCodes.OUTBOX_REPLAY_CONFLICT);
    }
}
