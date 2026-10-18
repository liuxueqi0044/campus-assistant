package cn.iocoder.yudao.module.campus.media.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentInspectionResultTest {

    private ContentInspectionResult pending() {
        return ContentInspectionResult.pending("inspection-1", "LISTING", "listing-9", 3,
                "hash-v3", "policy-1", "provider-request-1");
    }

    @Test
    void staleProviderCallbackCannotApproveNewContentVersion() {
        assertThatThrownBy(() -> pending().applyProviderResult(2, ContentInspectionStatus.PASS, "AUTO_PASS", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CONTENT_VERSION_CHANGED");
    }

    @Test
    void reviewRequiredCanBeManuallyDecidedWithReasonAndVersionAdvance() {
        ContentInspectionResult review = pending().applyProviderResult(3,
                ContentInspectionStatus.REVIEW_REQUIRED, "AUTO_REVIEW", 1);

        ContentInspectionResult decided = review.applyManualDecision(ContentInspectionDecision.PASS,
                "operator confirmed the policy exception", 42, 2);

        assertThat(decided.status()).isEqualTo(ContentInspectionStatus.PASS);
        assertThat(decided.source()).isEqualTo(ContentInspectionSource.MANUAL);
        assertThat(decided.decidedBy()).isEqualTo(42L);
        assertThat(decided.version()).isEqualTo(3);
        assertThat(decided.contentVersion()).isEqualTo(3);
        assertThat(decided.contentHash()).isEqualTo("hash-v3");
    }

    @Test
    void manualDecisionRejectsStaleVersionAndNonQueuedState() {
        ContentInspectionResult review = pending().applyProviderResult(3,
                ContentInspectionStatus.REVIEW_REQUIRED, "AUTO_REVIEW", 1);
        assertThatThrownBy(() -> review.applyManualDecision(ContentInspectionDecision.REJECT, "reason", 42, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INSPECTION_CONFLICT");

        ContentInspectionResult pass = pending().applyProviderResult(3,
                ContentInspectionStatus.PASS, "AUTO_PASS", 1);
        assertThatThrownBy(() -> pass.applyManualDecision(ContentInspectionDecision.REJECT, "reason", 42, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INSPECTION_NOT_READY");
    }

    @Test
    void manualDecisionRequiresBoundedReasonAndNeverStoresContent() {
        ContentInspectionResult review = pending().applyProviderResult(3,
                ContentInspectionStatus.REVIEW_REQUIRED, "AUTO_REVIEW", 1);
        assertThatThrownBy(() -> review.applyManualDecision(ContentInspectionDecision.REJECT, "", 42, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(review.toString()).doesNotContain("raw").doesNotContain("bytes");
    }

    @Test
    void providerCallbackAcceptsEveryNonPendingStateAndAdvancesVersion() {
        for (ContentInspectionStatus status : new ContentInspectionStatus[]{
                ContentInspectionStatus.PASS, ContentInspectionStatus.REJECT,
                ContentInspectionStatus.REVIEW_REQUIRED, ContentInspectionStatus.ERROR}) {
            ContentInspectionResult result = pending().applyProviderResult(3, status, "AUTO_RESULT", 1);
            assertThat(result.status()).isEqualTo(status);
            assertThat(result.source()).isEqualTo(ContentInspectionSource.AUTOMATED);
            assertThat(result.version()).isEqualTo(2);
            assertThat(result.decidedBy()).isNull();
            assertThat(result.decisionReason()).isNull();
        }
    }

    @Test
    void pendingCallbackAndDuplicateTerminalCallbackAreRejected() {
        assertThatThrownBy(() -> pending().applyProviderResult(3, ContentInspectionStatus.PENDING,
                "AUTO_PENDING", 1))
                .isInstanceOf(IllegalArgumentException.class);

        ContentInspectionResult pass = pending().applyProviderResult(3,
                ContentInspectionStatus.PASS, "AUTO_PASS", 1);
        assertThatThrownBy(() -> pass.applyProviderResult(3, ContentInspectionStatus.PASS,
                "AUTO_PASS", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INSPECTION_CONFLICT");
    }

    @Test
    void errorCanBeManuallyResolvedButManualPendingStateCannotBeConstructed() {
        ContentInspectionResult error = pending().applyProviderResult(3,
                ContentInspectionStatus.ERROR, "PROVIDER_TIMEOUT", 1);
        ContentInspectionResult rejected = error.applyManualDecision(ContentInspectionDecision.REJECT,
                "provider timeout requires operator rejection", 42, 2);
        assertThat(rejected.status()).isEqualTo(ContentInspectionStatus.REJECT);
        assertThat(rejected.source()).isEqualTo(ContentInspectionSource.MANUAL);

        assertThatThrownBy(() -> new ContentInspectionResult("inspection-1", "LISTING", "listing-9", 3,
                "hash-v3", "policy-1", "provider-request-1", ContentInspectionStatus.PENDING,
                ContentInspectionSource.MANUAL, "MANUAL_DECISION", 2, 42L, "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("manual result must be PASS or REJECT");
    }

    @Test
    void callbackReasonAndManualDecisionInputsAreBounded() {
        assertThatThrownBy(() -> pending().applyProviderResult(3, ContentInspectionStatus.PASS, "", 1))
                .isInstanceOf(IllegalArgumentException.class);

        String tooLong = "x".repeat(501);
        ContentInspectionResult review = pending().applyProviderResult(3,
                ContentInspectionStatus.REVIEW_REQUIRED, "AUTO_REVIEW", 1);
        assertThatThrownBy(() -> review.applyManualDecision(ContentInspectionDecision.PASS,
                tooLong, 42, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
