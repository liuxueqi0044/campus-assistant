package cn.iocoder.yudao.module.campus.identity.api;

import java.util.Set;

/** Minimal policy result. It never carries resource content or reveals whether a denied resource exists. */
public record ResourceAuthorizationDecision(boolean allowed, String reasonCode, Set<String> auditLabels) {

    public ResourceAuthorizationDecision {
        auditLabels = auditLabels == null ? Set.of() : Set.copyOf(auditLabels);
        if (allowed && reasonCode != null || !allowed && !"RESOURCE_NOT_ACCESSIBLE".equals(reasonCode)) {
            throw new IllegalArgumentException("resource decision reason is inconsistent with outcome");
        }
        if (allowed && auditLabels.isEmpty()) {
            throw new IllegalArgumentException("allowed resource decision requires an audit label");
        }
        if (auditLabels.stream().anyMatch(label -> label == null || label.isBlank() || label.length() > 64)) {
            throw new IllegalArgumentException("resource audit labels must contain 1..64 characters");
        }
    }

    public static ResourceAuthorizationDecision allow(Set<String> auditLabels) {
        return new ResourceAuthorizationDecision(true, null, auditLabels);
    }

    public static ResourceAuthorizationDecision deny() {
        return new ResourceAuthorizationDecision(false, "RESOURCE_NOT_ACCESSIBLE", Set.of("DENIED"));
    }
}
