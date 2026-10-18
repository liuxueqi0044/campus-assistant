package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;

/** Raised when a worker attempts to mutate a row after its fencing lease was replaced. */
public class OutboxLeaseLostException extends CampusApiException {

    public OutboxLeaseLostException(String eventId) {
        super(CampusErrorCodes.OUTBOX_LEASE_LOST);
    }
}
