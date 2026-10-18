package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;

@Component
public class UploadTokenCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] key;

    public UploadTokenCodec(@Value("${campus.media.local-signing-key:${random.uuid}}") String signingKey) {
        String normalized = Objects.requireNonNull(signingKey, "signingKey").trim();
        if (normalized.length() < 16) {
            throw new IllegalArgumentException("campus.media.local-signing-key must contain at least 16 characters");
        }
        this.key = normalized.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(MediaAssetDO asset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(payload(asset)));
    }

    public boolean matches(MediaAssetDO asset, String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return false;
        }
        try {
            byte[] presented = Base64.getUrlDecoder().decode(presentedToken);
            return MessageDigest.isEqual(mac(payload(asset)), presented);
        } catch (IllegalArgumentException invalidBase64) {
            return false;
        }
    }

    private byte[] mac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is required by the JDK", exception);
        }
    }

    private static String payload(MediaAssetDO asset) {
        return asset.getId() + "\n" + asset.getOwnerId() + "\n" + asset.getObjectKey() + "\n"
                + asset.getGeneration() + "\n" + asset.getTicketNonce() + "\n"
                + asset.getTicketExpiresAt().toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
