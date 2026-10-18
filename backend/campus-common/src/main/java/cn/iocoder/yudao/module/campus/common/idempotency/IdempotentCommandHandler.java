package cn.iocoder.yudao.module.campus.common.idempotency;

/** Business callback executed inside the same transaction as the durable reservation. */
@FunctionalInterface
public interface IdempotentCommandHandler<T> {

    CommandExecution<T> execute();
}
