package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** MySQL 8.4 repository. Duplicate event publication is a no-op upsert. */
public final class JdbcOutboxRepository implements OutboxRepository {
    private static final int REASON_LIMIT = 500;
    private static final int ERROR_CODE_LIMIT = 64;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcOutboxRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void insertIfAbsent(CampusEventEnvelope event, int maxAttempts, Instant now) {
        String sql = "INSERT INTO campus_outbox "
                + "(event_id,event_type,event_version,aggregate_id,aggregate_version,school_id,"
                + "occurred_at,trace_id,payload_json,status,next_run_at,attempt,max_attempts,"
                + "lease_token,row_version,create_time,update_time) "
                + "VALUES (?,?,?,?,?,?,?,?,?,'PENDING',?,0,?,0,1,?,?) "
                + "ON DUPLICATE KEY UPDATE event_id=event_id";
        jdbc.update(sql, event.getEventId(), event.getType(), event.getVersion(), event.getAggregateId(),
                event.getAggregateVersion(), event.getSchoolId(),
                Timestamp.from(event.getOccurredAt().toInstant()), event.getTraceId(),
                json(event.getPayload()), Timestamp.from(now), maxAttempts,
                Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public Optional<OutboxRecord> find(String eventId) {
        return queryOne(SELECT + " WHERE event_id = ?", eventId);
    }

    @Override
    public Optional<ConsumerStatus> consumptionStatus(String eventId, String consumer) {
        try {
            String status = jdbc.queryForObject(
                    "SELECT status FROM campus_event_consumption WHERE consumer=? AND event_id=?",
                    String.class, consumer, eventId);
            return Optional.of(ConsumerStatus.valueOf(status));
        } catch (EmptyResultDataAccessException absent) {
            return Optional.empty();
        }
    }

    @Override
    public List<OutboxRecord> claim(String owner, int limit, Instant now, Instant leaseUntil) {
        if (limit < 1) return List.of();
        String candidatesSql = SELECT
                + " WHERE (status='PENDING' AND next_run_at <= ?)"
                + " OR (status='PROCESSING' AND lease_until < ?)"
                + " ORDER BY next_run_at,event_id LIMIT ? FOR UPDATE SKIP LOCKED";
        List<OutboxRecord> candidates = jdbc.query(candidatesSql, this::map,
                Timestamp.from(now), Timestamp.from(now), limit);
        List<OutboxRecord> claimed = new ArrayList<>(candidates.size());
        for (OutboxRecord row : candidates) {
            long token = row.leaseToken() + 1;
            String update = "UPDATE campus_outbox SET status='PROCESSING',attempt=attempt+1,"
                    + "lease_owner=?,lease_until=?,lease_token=?,update_time=?,row_version=row_version+1 "
                    + "WHERE event_id=? AND lease_token=?";
            int count = jdbc.update(update, owner, Timestamp.from(leaseUntil), token,
                    Timestamp.from(now), row.envelope().getEventId(), row.leaseToken());
            if (count == 1) {
                claimed.add(new OutboxRecord(row.envelope(), OutboxStatus.PROCESSING,
                        row.nextRunAt(), row.attempt() + 1, row.maxAttempts(), owner, leaseUntil,
                        token, row.targetConsumer(), row.lastConsumer(), row.lastErrorCode(),
                        row.lastErrorReason(), row.rowVersion() + 1, row.replayConsumer(),
                        row.replayReason(), row.replayedAt(), row.completedAt(), row.createdAt(), now));
            }
        }
        return claimed;
    }

    @Override
    public void assertLease(String eventId, long token, Instant now) {
        String sql = "SELECT event_id FROM campus_outbox "
                + "WHERE event_id=? AND status='PROCESSING' AND lease_token=? AND lease_until>=? FOR UPDATE";
        List<String> locked = jdbc.query(sql, (rs, ignored) -> rs.getString(1),
                eventId, token, Timestamp.from(now));
        if (locked.size() != 1) throw new OutboxLeaseLostException(eventId);
    }

    @Override
    public ConsumerStatus startConsumer(String eventId, String consumer, long token, Instant now) {
        assertLease(eventId, token, now);
        String insert = "INSERT INTO campus_event_consumption "
                + "(consumer,event_id,status,create_time,update_time) VALUES (?,?,'PROCESSING',?,?) "
                + "ON DUPLICATE KEY UPDATE status=IF(status='SUCCEEDED','SUCCEEDED','PROCESSING'),"
                + "last_error_code=IF(status='SUCCEEDED',last_error_code,NULL),"
                + "last_error_reason=IF(status='SUCCEEDED',last_error_reason,NULL),"
                + "completed_at=IF(status='SUCCEEDED',completed_at,NULL),update_time=VALUES(update_time)";
        jdbc.update(insert, consumer, eventId, Timestamp.from(now), Timestamp.from(now));
        String status = jdbc.queryForObject(
                "SELECT status FROM campus_event_consumption WHERE consumer=? AND event_id=? FOR UPDATE",
                String.class, consumer, eventId);
        return ConsumerStatus.valueOf(status);
    }

    @Override
    public void succeedConsumer(String eventId, String consumer, long token, Instant now) {
        assertLease(eventId, token, now);
        String sql = "UPDATE campus_event_consumption SET status='SUCCEEDED',last_error_code=NULL,"
                + "last_error_reason=NULL,completed_at=?,update_time=? WHERE consumer=? AND event_id=?";
        int count = jdbc.update(sql, Timestamp.from(now), Timestamp.from(now), consumer, eventId);
        if (count != 1) throw new IllegalStateException("consumer reservation disappeared");
    }

    /** Must be invoked in a short REQUIRED transaction after the handler transaction rolled back. */
    @Override
    public void failConsumer(String eventId, String consumer, long token, String errorCode,
                             String reason, Instant now) {
        assertLease(eventId, token, now);
        String sql = "INSERT INTO campus_event_consumption "
                + "(consumer,event_id,status,last_error_code,last_error_reason,create_time,update_time) "
                + "VALUES (?,?,'FAILED',?,?,?,?) ON DUPLICATE KEY UPDATE "
                + "status=IF(status='SUCCEEDED','SUCCEEDED','FAILED'),"
                + "last_error_code=IF(status='SUCCEEDED',last_error_code,VALUES(last_error_code)),"
                + "last_error_reason=IF(status='SUCCEEDED',last_error_reason,VALUES(last_error_reason)),"
                + "completed_at=IF(status='SUCCEEDED',completed_at,NULL),update_time=VALUES(update_time)";
        jdbc.update(sql, consumer, eventId, cleanCode(errorCode), clean(reason),
                Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public void renew(String eventId, long token, Instant leaseUntil, Instant now) {
        mutate(eventId, token,
                "UPDATE campus_outbox SET lease_until=?,update_time=?,row_version=row_version+1 "
                        + "WHERE event_id=? AND status='PROCESSING' AND lease_token=?",
                Timestamp.from(leaseUntil), Timestamp.from(now));
    }

    @Override
    public void retry(String eventId, long token, Instant nextRunAt, String consumer,
                      String errorCode, String reason, Instant now) {
        mutate(eventId, token,
                "UPDATE campus_outbox SET status='PENDING',next_run_at=?,lease_owner=NULL,"
                        + "lease_until=NULL,last_consumer=?,last_error_code=?,last_error_reason=?,"
                        + "target_consumer=NULL,completed_at=NULL,update_time=?,row_version=row_version+1 "
                        + "WHERE event_id=? AND status='PROCESSING' AND lease_token=?",
                Timestamp.from(nextRunAt), consumer, cleanCode(errorCode), clean(reason), Timestamp.from(now));
    }

    @Override
    public void deadLetter(String eventId, long token, String consumer, String errorCode,
                           String reason, Instant now) {
        mutate(eventId, token,
                "UPDATE campus_outbox SET status='DEAD_LETTER',lease_owner=NULL,lease_until=NULL,"
                        + "last_consumer=?,last_error_code=?,last_error_reason=?,completed_at=?,"
                        + "update_time=?,row_version=row_version+1 WHERE event_id=? "
                        + "AND status='PROCESSING' AND lease_token=?",
                consumer, cleanCode(errorCode), clean(reason), Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public void succeed(String eventId, long token, String consumer, Instant now) {
        mutate(eventId, token,
                "UPDATE campus_outbox SET status='SUCCEEDED',lease_owner=NULL,lease_until=NULL,"
                        + "last_consumer=?,last_error_code=NULL,last_error_reason=NULL,target_consumer=NULL,"
                        + "completed_at=?,update_time=?,row_version=row_version+1 WHERE event_id=? "
                        + "AND status='PROCESSING' AND lease_token=?",
                consumer, Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public boolean replay(String eventId, String consumer, String reason, long expectedVersion, Instant now) {
        requireReason(reason);
        String sql = "UPDATE campus_outbox SET status='PENDING',next_run_at=?,attempt=0,"
                + "lease_owner=NULL,lease_until=NULL,target_consumer=?,replay_consumer=?,replay_reason=?,"
                + "replayed_at=?,completed_at=NULL,update_time=?,row_version=row_version+1 "
                + "WHERE event_id=? AND row_version=? AND NOT(status='PROCESSING' AND lease_until>?)";
        int count = jdbc.update(sql, Timestamp.from(now), consumer, consumer, clean(reason),
                Timestamp.from(now), Timestamp.from(now), eventId, expectedVersion, Timestamp.from(now));
        if (count == 0 && find(eventId).isPresent()) throw new OutboxVersionConflictException(eventId);
        return count == 1;
    }

    private void mutate(String eventId, long token, String sql, Object... values) {
        Object[] args = Arrays.copyOf(values, values.length + 2);
        args[values.length] = eventId;
        args[values.length + 1] = token;
        if (jdbc.update(sql, args) != 1) throw new OutboxLeaseLostException(eventId);
    }

    private Optional<OutboxRecord> queryOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, this::map, args));
        } catch (EmptyResultDataAccessException absent) {
            return Optional.empty();
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("event payload cannot be serialized", e);
        }
    }

    private Map<String, Object> payload(String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("persisted event payload cannot be decoded", e);
        }
    }

    private OutboxRecord map(ResultSet result, int ignored) throws SQLException {
        CampusEventEnvelope event = CampusEventEnvelope.builder()
                .eventId(result.getString("event_id")).type(result.getString("event_type"))
                .version(result.getInt("event_version")).aggregateId(result.getString("aggregate_id"))
                .aggregateVersion(result.getLong("aggregate_version")).schoolId(result.getString("school_id"))
                .occurredAt(result.getTimestamp("occurred_at").toInstant().atOffset(ZoneOffset.UTC))
                .traceId(result.getString("trace_id")).payload(payload(result.getString("payload_json"))).build();
        return new OutboxRecord(event, OutboxStatus.valueOf(result.getString("status")),
                instant(result, "next_run_at"), result.getInt("attempt"), result.getInt("max_attempts"),
                result.getString("lease_owner"), instant(result, "lease_until"), result.getLong("lease_token"),
                result.getString("target_consumer"), result.getString("last_consumer"),
                result.getString("last_error_code"), result.getString("last_error_reason"),
                result.getLong("row_version"), result.getString("replay_consumer"),
                result.getString("replay_reason"), instant(result, "replayed_at"),
                instant(result, "completed_at"), instant(result, "create_time"), instant(result, "update_time"));
    }

    private static Instant instant(ResultSet result, String key) throws SQLException {
        Timestamp value = result.getTimestamp(key);
        return value == null ? null : value.toInstant();
    }

    static String cleanCode(String code) {
        if (code == null || code.isBlank()) return "UNSPECIFIED";
        String value = code.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return value.length() > ERROR_CODE_LIMIT ? value.substring(0, ERROR_CODE_LIMIT) : value;
    }

    static String clean(String reason) {
        if (reason == null || reason.isBlank()) return "unspecified";
        String value = reason.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        return value.length() > REASON_LIMIT ? value.substring(0, REASON_LIMIT) : value;
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("replay reason must not be blank");
    }

    private static final String SELECT = "SELECT event_id,event_type,event_version,aggregate_id,"
            + "aggregate_version,school_id,occurred_at,trace_id,payload_json,status,next_run_at,attempt,"
            + "max_attempts,lease_owner,lease_until,lease_token,target_consumer,last_consumer,last_error_code,"
            + "last_error_reason,row_version,replay_consumer,replay_reason,replayed_at,completed_at,"
            + "create_time,update_time FROM campus_outbox";
}
