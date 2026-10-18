package cn.iocoder.yudao.module.campus.common.health;

import cn.iocoder.yudao.module.campus.common.api.CampusResponse;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M00.01 健康检查入口：{@code GET /health/readiness}。
 *
 * <p>契约（M00.01 接口契约）：对外只给必要状态，不含依赖地址、版本、异常原因或 profile。
 * 内部诊断详情由后续运维模块通过专用后台权限提供，不能因为普通学生已经登录就暴露。
 *
 * <p>探测失败只返回状态枚举，不记录连接串与异常堆栈，避免把数据库地址、口令线索写入日志。
 */
@RestController
@Slf4j
public class CampusReadinessController {

    public static final String READINESS_PATH = "/health/readiness";

    public static final String STATUS_UP = "UP";
    public static final String STATUS_DOWN = "DOWN";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    public static final String CHECK_DATABASE = "database";
    public static final String CHECK_CACHE = "cache";

    /** 连接有效性检查超时（秒），避免就绪探测长期挂住 */
    private static final int CONNECTION_VALIDATION_TIMEOUT_SECONDS = 1;

    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;

    public CampusReadinessController(ObjectProvider<DataSource> dataSourceProvider,
                                     ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
        this.dataSourceProvider = dataSourceProvider;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    }

    @GetMapping(READINESS_PATH)
    @PermitAll // 就绪探测发生在登录之前；安全配置默认 anyRequest().authenticated()，因此必须显式放开
    public CampusResponse<Map<String, Object>> readiness() {
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put(CHECK_DATABASE, databaseStatus());
        checks.put(CHECK_CACHE, cacheStatus());
        // 数据库或缓存缺失（UNKNOWN）同样不能接受流量；只有全部必需依赖明确 UP 才就绪。
        boolean up = checks.values().stream().allMatch(STATUS_UP::equals);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", up ? STATUS_UP : STATUS_DOWN);
        // M00.02：校园入口统一使用 code/message/data/traceId 信封，就绪探测同样带 traceId
        return CampusResponse.success(data);
    }

    private String databaseStatus() {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return STATUS_UNKNOWN;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(CONNECTION_VALIDATION_TIMEOUT_SECONDS) ? STATUS_UP : STATUS_DOWN;
        } catch (Exception ex) {
            // 不输出异常内容：可能包含连接串或账号信息
            return STATUS_DOWN;
        }
    }

    private String cacheStatus() {
        RedisConnectionFactory connectionFactory = redisConnectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            return STATUS_UNKNOWN;
        }
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
            return STATUS_UP;
        } catch (Exception ex) {
            return STATUS_DOWN;
        }
    }

}
