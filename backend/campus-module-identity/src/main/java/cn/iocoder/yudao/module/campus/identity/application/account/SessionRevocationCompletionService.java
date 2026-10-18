package cn.iocoder.yudao.module.campus.identity.application.account;

import cn.iocoder.yudao.module.campus.identity.infrastructure.AccountLifecycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits cleanup acknowledgement independently because the lifecycle transaction has already committed. */
@Service
@RequiredArgsConstructor
public class SessionRevocationCompletionService {

    private final AccountLifecycleRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markComplete(long userId, long authVersion) {
        return repository.markSessionRevoked(userId, authVersion);
    }
}
