package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;

/** The immutable event identifier was reused for a different fact. */
public class OutboxEventConflictException extends CampusApiException {

    public OutboxEventConflictException() {
        super(CampusErrorCodes.OUTBOX_EVENT_ID_CONFLICT);
    }
}
