package cn.iocoder.yudao.module.campus.media.domain;

import java.util.Objects;

/**
 * Immutable inspection state for one subject content version.
 *
 * <p>The state object deliberately contains no raw text or media bytes. Provider callbacks and
 * manual decisions must retain the original content version and advance the optimistic version.
 */
public record ContentInspectionResult(String inspectionId, String subjectType, String subjectId,
                                      long contentVersion, String contentHash, String policyVersion,
                                      String providerRequestId, ContentInspectionStatus status,
                                      ContentInspectionSource source, String reasonCode, long version,
                                      Long decidedBy, String decisionReason) {

    public ContentInspectionResult {
        requireText(inspectionId, "inspectionId");
        requireText(subjectType, "subjectType");
        requireText(subjectId, "subjectId");
        requireText(contentHash, "contentHash");
        requireText(policyVersion, "policyVersion");
        requireText(providerRequestId, "providerRequestId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        if (contentVersion <= 0 || version <= 0) {
            throw new IllegalArgumentException("contentVersion and version must be positive");
        }
        if (source == ContentInspectionSource.MANUAL) {
            if (status != ContentInspectionStatus.PASS && status != ContentInspectionStatus.REJECT) {
                throw new IllegalArgumentException("manual result must be PASS or REJECT");
            }
            if (decidedBy == null || decidedBy <= 0) {
                throw new IllegalArgumentException("manual result requires a positive operator");
            }
            requireReason(decisionReason);
        } else if (decidedBy != null || decisionReason != null) {
            throw new IllegalArgumentException("automated result cannot contain manual decision data");
        }
        if (status != ContentInspectionStatus.PENDING && !hasText(reasonCode)) {
            throw new IllegalArgumentException("terminal or review result requires a reasonCode");
        }
    }

    public static ContentInspectionResult pending(String inspectionId, String subjectType, String subjectId,
                                                  long contentVersion, String contentHash, String policyVersion,
                                                  String providerRequestId) {
        return new ContentInspectionResult(inspectionId, subjectType, subjectId, contentVersion, contentHash,
                policyVersion, providerRequestId, ContentInspectionStatus.PENDING,
                ContentInspectionSource.AUTOMATED, null, 1, null, null);
    }

    /** Applies an authenticated callback for the exact content version and request version. */
    public ContentInspectionResult applyProviderResult(long callbackContentVersion,
                                                        ContentInspectionStatus nextStatus,
                                                        String nextReasonCode, long expectedVersion) {
        if (version != expectedVersion) {
            throw new IllegalStateException("INSPECTION_CONFLICT");
        }
        if (callbackContentVersion != contentVersion) {
            throw new IllegalStateException("CONTENT_VERSION_CHANGED");
        }
        Objects.requireNonNull(nextStatus, "nextStatus");
        if (nextStatus == ContentInspectionStatus.PENDING) {
            throw new IllegalArgumentException("provider callback cannot leave a pending result");
        }
        if (status != ContentInspectionStatus.PENDING && status != ContentInspectionStatus.ERROR) {
            throw new IllegalStateException("INSPECTION_CONFLICT");
        }
        return new ContentInspectionResult(inspectionId, subjectType, subjectId, contentVersion, contentHash,
                policyVersion, providerRequestId, nextStatus, ContentInspectionSource.AUTOMATED,
                nextReasonCode, version + 1, null, null);
    }

    /** Applies a reasoned operator decision only to a queued/error result at the expected version. */
    public ContentInspectionResult applyManualDecision(ContentInspectionDecision decision, String reason,
                                                       long operatorId, long expectedVersion) {
        if (version != expectedVersion) {
            throw new IllegalStateException("INSPECTION_CONFLICT");
        }
        if (status != ContentInspectionStatus.REVIEW_REQUIRED && status != ContentInspectionStatus.ERROR) {
            throw new IllegalStateException("INSPECTION_NOT_READY");
        }
        Objects.requireNonNull(decision, "decision");
        requireReason(reason);
        if (operatorId <= 0) {
            throw new IllegalArgumentException("operatorId must be positive");
        }
        return new ContentInspectionResult(inspectionId, subjectType, subjectId, contentVersion, contentHash,
                policyVersion, providerRequestId,
                decision == ContentInspectionDecision.PASS ? ContentInspectionStatus.PASS : ContentInspectionStatus.REJECT,
                ContentInspectionSource.MANUAL, "MANUAL_DECISION", version + 1, operatorId, reason);
    }

    private static void requireReason(String reason) {
        if (!hasText(reason) || reason.length() > 500) {
            throw new IllegalArgumentException("decision reason must be 1..500 characters");
        }
    }

    private static void requireText(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
