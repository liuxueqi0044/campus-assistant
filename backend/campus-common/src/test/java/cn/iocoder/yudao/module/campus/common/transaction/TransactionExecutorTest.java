package cn.iocoder.yudao.module.campus.common.transaction;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.SQLTransactionRollbackException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("M00.03")
class TransactionExecutorTest {

    @Test
    void shouldRetryTheWholeRequiredTransactionOnlyForWhitelistedMySqlLockConflict() {
        PlatformTransactionManager transactionManager = transactionManager();
        AtomicInteger pauses = new AtomicInteger();
        TransactionExecutor executor = new TransactionExecutor(
                new org.springframework.transaction.support.TransactionTemplate(transactionManager),
                ignored -> pauses.incrementAndGet());
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.required(TransactionPolicy.retryMySqlLockConflicts("claim-outbox", 2), status -> {
            if (attempts.incrementAndGet() == 1) {
                throw new CannotAcquireLockException("MySQL lock wait timeout");
            }
            return "committed";
        });

        assertThat(result).isEqualTo("committed");
        assertThat(attempts).hasValue(2);
        assertThat(pauses).hasValue(1);
        verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    @Test
    void shouldExposeRetryableConflictAfterBoundedAttempts() {
        PlatformTransactionManager transactionManager = transactionManager();
        TransactionExecutor executor = new TransactionExecutor(
                new org.springframework.transaction.support.TransactionTemplate(transactionManager), ignored -> {
                });

        assertThatThrownBy(() -> executor.required(
                TransactionPolicy.retryMySqlLockConflicts("claim-outbox", 2),
                status -> {
                    throw new CannotAcquireLockException("MySQL lock wait timeout");
                }))
                .isInstanceOf(TransactionRetryExhaustedException.class)
                .hasMessageContaining(TransactionRetryExhaustedException.CODE)
                .extracting(exception -> (TransactionRetryExhaustedException) exception)
                .satisfies(exception -> {
                    assertThat(exception.getOperation()).isEqualTo("claim-outbox");
                    assertThat(exception.getAttempts()).isEqualTo(2);
                });
    }

    @Test
    void shouldNotRetryOptimisticVersionConflict() {
        PlatformTransactionManager transactionManager = transactionManager();
        TransactionExecutor executor = new TransactionExecutor(
                new org.springframework.transaction.support.TransactionTemplate(transactionManager), ignored -> {
                });

        assertThatThrownBy(() -> executor.required(
                TransactionPolicy.retryMySqlLockConflicts("update-order", 3),
                status -> {
                    throw new OptimisticLockingFailureException("stale version");
                }))
                .isInstanceOf(OptimisticLockingFailureException.class);
        verify(transactionManager, times(1)).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void shouldRespectDeadlockOnlyWhitelistForSqlTransactionRollbackException() {
        PlatformTransactionManager transactionManager = transactionManager();
        TransactionExecutor executor = new TransactionExecutor(
                new org.springframework.transaction.support.TransactionTemplate(transactionManager), ignored -> {
                });
        TransactionPolicy deadlockOnly = new TransactionPolicy("deadlock-only",
                Set.of(RetryableTransactionError.MYSQL_DEADLOCK), 2);

        assertThatThrownBy(() -> executor.required(deadlockOnly, status -> {
            throw new IllegalStateException(new SQLTransactionRollbackException("lock wait", "40001", 1205));
        })).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(TransactionRetryExhaustedException.class);
        verify(transactionManager, times(1)).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void shouldRespectLockTimeoutOnlyWhitelistForSqlTransactionRollbackException() {
        PlatformTransactionManager transactionManager = transactionManager();
        TransactionExecutor executor = new TransactionExecutor(
                new org.springframework.transaction.support.TransactionTemplate(transactionManager), ignored -> {
                });
        TransactionPolicy timeoutOnly = new TransactionPolicy("timeout-only",
                Set.of(RetryableTransactionError.MYSQL_LOCK_WAIT_TIMEOUT), 2);

        assertThatThrownBy(() -> executor.required(timeoutOnly, status -> {
            throw new IllegalStateException(new SQLTransactionRollbackException("deadlock", "40001", 1213));
        })).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(TransactionRetryExhaustedException.class);
        verify(transactionManager, times(1)).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void shouldRequireExplicitNoRetryPolicyShape() {
        assertThat(TransactionPolicy.withoutRetry("send-to-gateway").maxAttempts()).isEqualTo(1);
        assertThatThrownBy(() -> new TransactionPolicy("bad", java.util.Set.of(), 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransactionPolicy("bad", java.util.Set.of(RetryableTransactionError.MYSQL_DEADLOCK), 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        return transactionManager;
    }
}
