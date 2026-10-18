package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.media.infrastructure.dal.dataobject.MediaAssetDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UploadTokenCodecTest {

    private final UploadTokenCodec codec = new UploadTokenCodec("test-only-signing-key-32-characters");

    @Test
    void tokenIsBoundToOwnerObjectGenerationAndExpiry() {
        MediaAssetDO asset = asset();
        String token = codec.issue(asset);

        assertThat(codec.matches(asset, token)).isTrue();
        asset.setGeneration(2L);
        assertThat(codec.matches(asset, token)).isFalse();
        assertThat(codec.matches(asset, "not-base64***")).isFalse();
    }

    private static MediaAssetDO asset() {
        MediaAssetDO asset = new MediaAssetDO();
        asset.setId(10L);
        asset.setOwnerId(20L);
        asset.setObjectKey("private/membership/g1/object");
        asset.setGeneration(1L);
        asset.setTicketNonce("nonce");
        asset.setTicketExpiresAt(LocalDateTime.parse("2026-09-17T00:10:00"));
        return asset;
    }
}
