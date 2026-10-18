package cn.iocoder.yudao.module.campus.media.controller.app.vo;

import cn.iocoder.yudao.module.campus.media.application.MediaDeliveryIssueResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MediaDownloadGrantResponseTest {

    @Test
    void exposesExpiryAndBindingSnapshotWithoutStorageAddress() throws Exception {
        MediaDownloadGrantResponse response = MediaDownloadGrantResponse.from(new MediaDeliveryIssueResult(
                "opaque-token", Instant.parse("2026-09-18T00:01:00Z"), 7L, 13L));

        assertThat(response.deliveryToken()).isEqualTo("opaque-token");
        assertThat(response.expiresAt().toInstant()).isEqualTo(Instant.parse("2026-09-18T00:01:00Z"));
        assertThat(response.assetGeneration()).isEqualTo(7L);
        assertThat(response.assetVersion()).isEqualTo(13L);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
                .contains("expiresAt", "assetGeneration", "assetVersion")
                .doesNotContain("objectKey");
    }
}
