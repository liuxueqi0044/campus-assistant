package cn.iocoder.yudao.module.campus.identity.domain.runner;

public record RunnerQualificationAcceptance(long qualificationId, long version) {
    public RunnerQualificationAcceptance {
        if (qualificationId <= 0 || version <= 0) {
            throw new IllegalArgumentException("qualification id and version must be positive");
        }
    }
}
