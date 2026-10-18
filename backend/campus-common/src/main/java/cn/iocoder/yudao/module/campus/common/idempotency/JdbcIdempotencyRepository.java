package cn.iocoder.yudao.module.campus.common.idempotency;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/** MySQL implementation using an atomic no-op upsert to avoid duplicate-key rollback-only traps. */
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private static final String SELECT_FOR_UPDATE = """
            SELECT id, platform_tenant_id, actor_id, action, key_hash, request_hash, status,
                   processing_token, business_reference, result_json, result_version, expires_at
              FROM campus_idempotency
             WHERE platform_tenant_id = ? AND actor_id = ? AND action = ? AND key_hash = ?
             FOR UPDATE
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public IdempotencyReservation reserve(IdempotencyKey key, String requestHash, Instant expiresAt,
                                          String processingToken) {
        int affected = jdbcTemplate.update("""
                INSERT INTO campus_idempotency
                    (platform_tenant_id, actor_id, action, key_hash, request_hash, status,
                     processing_token, result_version, expires_at, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?, 1, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE id = id
                """, key.platformTenantId(), key.actorId(), key.action(), key.keyHash(), requestHash,
                processingToken, Timestamp.from(expiresAt));
        IdempotencyRecord record;
        try {
            record = jdbcTemplate.queryForObject(SELECT_FOR_UPDATE, this::map,
                    key.platformTenantId(), key.actorId(), key.action(), key.keyHash());
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException("idempotency row disappeared after reservation", exception);
        }
        // The token is unique to this attempt, so this remains correct even when a driver reports
        // a non-standard affected-row count for ON DUPLICATE KEY UPDATE.
        return new IdempotencyReservation(record, processingToken.equals(record.processingToken())
                && "PROCESSING".equals(record.status().name()) && affected > 0);
    }

    @Override
    public void complete(IdempotencyKey key, String processingToken, String businessReference,
                         long resultVersion, String resultJson) {
        int affected = jdbcTemplate.update("""
                UPDATE campus_idempotency
                   SET status = 'SUCCEEDED', processing_token = NULL, business_reference = ?,
                       result_json = ?, result_version = ?, update_time = CURRENT_TIMESTAMP(3)
                 WHERE platform_tenant_id = ? AND actor_id = ? AND action = ? AND key_hash = ?
                   AND status = 'PROCESSING' AND processing_token = ?
                """, businessReference, resultJson, resultVersion, key.platformTenantId(), key.actorId(),
                key.action(), key.keyHash(), processingToken);
        if (affected != 1) {
            throw new IllegalStateException("idempotency reservation was lost before completion");
        }
    }

    private IdempotencyRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new IdempotencyRecord(
                rs.getLong("id"),
                new IdempotencyKey(rs.getLong("platform_tenant_id"), rs.getLong("actor_id"),
                        rs.getString("action"), rs.getString("key_hash")),
                rs.getString("request_hash"),
                IdempotencyStatus.valueOf(rs.getString("status")),
                rs.getString("processing_token"),
                rs.getString("business_reference"),
                rs.getString("result_json"),
                rs.getLong("result_version"),
                rs.getTimestamp("expires_at").toInstant());
    }
}
