package cn.iocoder.yudao.module.campus.identity.infrastructure;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Server-side jscode2session adapter. App id and secret are read exclusively from the existing
 * {@code wx.miniapp} server configuration; client input can neither select nor override an app.
 */
@Component
public class WechatMiniAppIdentityAdapter implements WechatIdentityAdapter {

    private final RestClient restClient;
    private final String appId;
    private final String secret;

    public WechatMiniAppIdentityAdapter(@Value("${wx.miniapp.appid}") String appId,
                                        @Value("${wx.miniapp.secret}") String secret,
                                        @Value("${campus.wechat.connect-timeout:3s}") Duration connectTimeout,
                                        @Value("${campus.wechat.read-timeout:5s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl("https://api.weixin.qq.com")
                .requestFactory(requestFactory).build();
        this.appId = appId;
        this.secret = secret;
    }

    @Override
    public WechatIdentity exchangeCode(String code) {
        try {
            SessionResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/sns/jscode2session")
                            .queryParam("appid", appId).queryParam("secret", secret)
                            .queryParam("js_code", code).queryParam("grant_type", "authorization_code").build())
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(SessionResponse.class);
            if (response == null) {
                throw new CampusApiException(CampusErrorCodes.IDENTITY_PROVIDER_UNAVAILABLE);
            }
            if (response.errCode != null && response.errCode != 0) {
                // Err 40029 and 40163 are invalid/used codes. Other channel-side failures are retriable.
                if (response.errCode == 40029 || response.errCode == 40163) {
                    throw new CampusApiException(CampusErrorCodes.WECHAT_CODE_INVALID);
                }
                throw new CampusApiException(CampusErrorCodes.IDENTITY_PROVIDER_UNAVAILABLE);
            }
            if (response.openId == null || response.openId.isBlank()) {
                throw new CampusApiException(CampusErrorCodes.IDENTITY_PROVIDER_UNAVAILABLE);
            }
            return new WechatIdentity(appId, response.openId, blankToNull(response.unionId));
        } catch (CampusApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            // Deliberately no credential/code logging here.
            throw new CampusApiException(CampusErrorCodes.IDENTITY_PROVIDER_UNAVAILABLE);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true) // session_key is intentionally ignored and never materialized in our domain.
    private static final class SessionResponse {
        @JsonProperty("openid")
        private String openId;
        @JsonProperty("unionid")
        private String unionId;
        @JsonProperty("errcode")
        private Integer errCode;
    }
}
