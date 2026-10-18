package cn.iocoder.yudao.module.campus.identity.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.identity.infrastructure.MemberAccountAdapter;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentity;
import cn.iocoder.yudao.module.campus.identity.infrastructure.WechatIdentityBindingRepository;
import cn.iocoder.yudao.module.campus.identity.infrastructure.dal.dataobject.WechatIdentityBindingDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedicated transactional bean: member creation and mapping insertion must commit or roll back together.
 * The caller catches the unique-key race outside this transaction, avoiding Spring's rollback-only trap.
 */
@Service
@RequiredArgsConstructor
public class WechatIdentityBindingService {

    private final WechatIdentityBindingRepository bindingRepository;
    private final MemberAccountAdapter memberAccountAdapter;

    @Transactional(rollbackFor = Exception.class)
    public Long resolveOrCreateMember(WechatIdentity identity) {
        WechatIdentityBindingDO existing = bindingRepository.findByAppIdAndOpenId(identity.appId(), identity.openId());
        if (existing != null) {
            return existing.getMemberId();
        }
        WechatIdentityBindingDO unionBinding = bindingRepository.findByAppIdAndUnionId(identity.appId(), identity.unionId());
        if (unionBinding != null && !identity.openId().equals(unionBinding.getOpenId())) {
            // UnionID is never a silent account merge key.
            throw new CampusApiException(CampusErrorCodes.IDENTITY_BINDING_CONFLICT);
        }
        MemberUserDO member = memberAccountAdapter.createWechatMember();
        WechatIdentityBindingDO binding = new WechatIdentityBindingDO();
        binding.setAppId(identity.appId());
        binding.setOpenId(identity.openId());
        binding.setUnionId(identity.unionId());
        binding.setMemberId(member.getId());
        bindingRepository.insert(binding); // unique app_id/open_id is the concurrency authority
        return member.getId();
    }

    /** Read only after a failed transaction has rolled back its just-created member. */
    public Long resolveConcurrentWinner(WechatIdentity identity, DataIntegrityViolationException ignored) {
        WechatIdentityBindingDO winner = bindingRepository.findByAppIdAndOpenId(identity.appId(), identity.openId());
        if (winner != null) {
            return winner.getMemberId();
        }
        WechatIdentityBindingDO unionWinner = bindingRepository.findByAppIdAndUnionId(identity.appId(), identity.unionId());
        if (unionWinner != null && !identity.openId().equals(unionWinner.getOpenId())) {
            throw new CampusApiException(CampusErrorCodes.IDENTITY_BINDING_CONFLICT);
        }
        throw ignored;
    }
}
