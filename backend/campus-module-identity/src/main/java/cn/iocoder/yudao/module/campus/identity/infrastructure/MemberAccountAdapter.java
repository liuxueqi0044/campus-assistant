package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.TerminalEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.member.service.auth.MemberAuthService;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reuses upstream member records, member-only OAuth2 tokens and audit logs. */
@Component
@RequiredArgsConstructor
public class MemberAccountAdapter {

    private static final String DEFAULT_WECHAT_NICKNAME = "微信用户";

    private final MemberUserService memberUserService;
    private final OAuth2TokenCommonApi oauth2TokenApi;
    private final LoginLogApi loginLogApi;
    private final MemberAuthService memberAuthService;

    /** Delegates revocation and upstream logout audit behavior; absent/invalid tokens remain idempotent. */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            memberAuthService.logout(token);
        }
    }

    public MemberUserDO createWechatMember() {
        return memberUserService.createUser(DEFAULT_WECHAT_NICKNAME, null, ServletUtils.getClientIP(),
                TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal());
    }

    public MemberUserDO getRequiredActiveMember(Long memberId) {
        MemberUserDO member = memberUserService.getUser(memberId);
        if (member == null || CommonStatusEnum.isDisable(member.getStatus())) {
            writeLoginLog(member, LoginResultEnum.USER_DISABLED);
            throw new CampusApiException(CampusErrorCodes.ACCOUNT_DISABLED);
        }
        return member;
    }

    /** Called only after the identity-binding transaction has completed successfully. */
    public OAuth2AccessTokenRespDTO issueMemberToken(MemberUserDO member) {
        writeLoginLog(member, LoginResultEnum.SUCCESS);
        memberUserService.updateUserLogin(member.getId(), ServletUtils.getClientIP());
        return oauth2TokenApi.createAccessToken(new OAuth2AccessTokenCreateReqDTO()
                .setUserId(member.getId()).setUserType(UserTypeEnum.MEMBER.getValue())
                .setClientId(OAuth2ClientConstants.CLIENT_ID_DEFAULT));
    }

    private void writeLoginLog(MemberUserDO member, LoginResultEnum result) {
        LoginLogCreateReqDTO request = new LoginLogCreateReqDTO();
        request.setLogType(LoginLogTypeEnum.LOGIN_SOCIAL.getType());
        request.setTraceId(TracerUtils.getTraceId());
        request.setUserId(member == null ? null : member.getId());
        request.setUserType(UserTypeEnum.MEMBER.getValue());
        request.setUsername(member == null ? null : member.getMobile());
        request.setUserAgent(ServletUtils.getUserAgent());
        request.setUserIp(ServletUtils.getClientIP());
        request.setResult(result.getResult());
        loginLogApi.createLoginLog(request);
    }
}
