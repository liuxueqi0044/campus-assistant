package cn.iocoder.yudao.module.campus.common.event;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorCodes;
import cn.iocoder.yudao.module.campus.common.api.CampusErrorDetail;
import cn.iocoder.yudao.module.campus.common.api.CampusRetryAction;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * M00.02 事件版本策略：消费者声明的「类型 → 支持版本集合」。
 *
 * <p>用途是让未知类型、未知版本进入可观察失败（死信 + 运维处理），而不是按旧结构猜测字段。
 * 支持集合由各消费者注册，公共层不猜测业务支持的版本。
 */
public final class CampusEventVersionPolicy {

    private final Map<String, Set<Integer>> supportedVersions;

    private CampusEventVersionPolicy(Map<String, Set<Integer>> supportedVersions) {
        this.supportedVersions = supportedVersions;
    }

    /**
     * @param supportedVersions 类型 → 支持的版本集合；构造后深拷贝，避免调用方事后修改
     */
    public static CampusEventVersionPolicy of(Map<String, ? extends Collection<Integer>> supportedVersions) {
        Map<String, Set<Integer>> copy = new LinkedHashMap<>();
        if (supportedVersions != null) {
            supportedVersions.forEach((type, versions) ->
                    copy.put(type, versions == null
                            ? Set.of()
                            : Set.copyOf(new LinkedHashSet<>(new TreeSet<>(versions)))));
        }
        return new CampusEventVersionPolicy(Map.copyOf(copy));
    }

    /** 登记的事件类型 */
    public Set<String> supportedTypes() {
        return supportedVersions.keySet();
    }

    /** 判断类型与版本的支持情况 */
    public CampusEventVersionStatus check(String type, int version) {
        Set<Integer> versions = supportedVersions.get(type);
        if (versions == null) {
            return CampusEventVersionStatus.UNKNOWN_TYPE;
        }
        return versions.contains(version)
                ? CampusEventVersionStatus.SUPPORTED
                : CampusEventVersionStatus.UNSUPPORTED_VERSION;
    }

    /**
     * 版本不被支持时抛出冻结错误码，调用方据此进入死信与人工处理。
     */
    public void requireSupported(String type, int version) {
        CampusEventVersionStatus status = check(type, version);
        if (status == CampusEventVersionStatus.SUPPORTED) {
            return;
        }
        CampusErrorDetail detail = CampusErrorDetail.of(CampusRetryAction.OPERATOR);
        throw new CampusApiException(CampusErrorCodes.EVENT_VERSION_UNSUPPORTED, detail,
                status + ":" + type + "@" + version);
    }

}
