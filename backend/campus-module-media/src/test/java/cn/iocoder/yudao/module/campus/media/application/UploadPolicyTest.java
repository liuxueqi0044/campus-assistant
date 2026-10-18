package cn.iocoder.yudao.module.campus.media.application;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.media.domain.MediaPurpose;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadPolicyTest {

    private final UploadPolicy policy = new UploadPolicy();

    @Test
    void acceptsMembershipEvidenceWithinLimit() {
        UploadPolicy.PolicyDecision result = policy.requireAllowed(
                "MEMBERSHIP_VERIFICATION", "card.png", 1024, "image/png");

        assertThat(result.purpose()).isEqualTo(MediaPurpose.MEMBERSHIP_VERIFICATION);
        assertThat(result.fileName()).isEqualTo("card.png");
        assertThat(result.mime()).isEqualTo("image/png");
    }

    @Test
    void rejectsUnknownPurposeAndPathLikeFileNames() {
        assertFailure(() -> policy.requireAllowed("CHAT", "a.png", 1, "image/png"),
                CampusErrorCodes.UPLOAD_PURPOSE_DENIED.getName());
        assertFailure(() -> policy.requireAllowed("MEMBERSHIP_VERIFICATION", "../card.png", 1, "image/png"),
                CampusErrorCodes.REQUEST_INVALID.getName());
    }

    @Test
    void rejectsOversizeAndDisallowedMime() {
        assertFailure(() -> policy.requireAllowed("MEMBERSHIP_VERIFICATION", "card.png",
                        MediaPurpose.MEMBERSHIP_VERIFICATION.maxBytes() + 1, "image/png"),
                CampusErrorCodes.FILE_SIZE_EXCEEDED.getName());
        assertFailure(() -> policy.requireAllowed("MEMBERSHIP_VERIFICATION", "card.exe", 1,
                        "application/octet-stream"), CampusErrorCodes.UPLOAD_PURPOSE_DENIED.getName());
    }

    private static void assertFailure(Runnable action, String expectedName) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CampusApiException.class,
                        error -> assertThat(error.getSpec().getName()).isEqualTo(expectedName));
    }
}
