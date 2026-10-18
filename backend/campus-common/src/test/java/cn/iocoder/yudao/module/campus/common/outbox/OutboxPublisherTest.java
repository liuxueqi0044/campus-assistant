package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.api.CampusApiException;
import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*; import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OutboxPublisherTest {
    private final MemoryRepository repository=new MemoryRepository();
    private final OutboxPublisher publisher=new OutboxPublisher(repository,3,Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC));
    @AfterEach void clear(){TransactionSynchronizationManager.clear();}
    @Test void rejectsNoTransaction(){assertThatThrownBy(()->publisher.publish(event(Map.of()))).isInstanceOf(IllegalStateException.class);}
    @Test void validatesSensitivePayloadBeforePersistence(){active(); assertThatThrownBy(()->publisher.publish(event(Map.of("pickup_code","x")))).isInstanceOf(CampusApiException.class); assertThat(repository.rows).isEmpty();}
    @Test void acceptsSameEventAndRejectsDifferentFrozenEnvelope(){active(); publisher.publish(event(Map.of("order","1"))); publisher.publish(event(Map.of("order","1"))); assertThat(repository.rows).hasSize(1); assertThatThrownBy(()->publisher.publish(event(Map.of("order","2")))).isInstanceOf(OutboxEventConflictException.class);}
    private static void active(){TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);}
    private static CampusEventEnvelope event(Map<String,Object> payload){return CampusEventEnvelope.builder().eventId("evt-1").type("campus.order.created").version(1).aggregateId("1").aggregateVersion(1).schoolId("1").occurredAt(OffsetDateTime.parse("2026-01-01T00:00:00Z")).payload(payload).build();}
    private static final class MemoryRepository implements OutboxRepository { final Map<String,OutboxRecord> rows=new HashMap<>();
        public void insertIfAbsent(CampusEventEnvelope e,int max,Instant now){rows.putIfAbsent(e.getEventId(),new OutboxRecord(e,OutboxStatus.PENDING,now,0,max, null,null,0,null,null,null,null,1,null,null,null,null,now,now));}
        public Optional<OutboxRecord> find(String id){return Optional.ofNullable(rows.get(id));} public Optional<ConsumerStatus> consumptionStatus(String e,String c){return Optional.empty();} public List<OutboxRecord> claim(String a,int b,Instant c,Instant d){return List.of();} public void assertLease(String a,long b,Instant c){} public ConsumerStatus startConsumer(String a,String b,long c,Instant d){return ConsumerStatus.PROCESSING;} public void succeedConsumer(String a,String b,long c,Instant d){} public void failConsumer(String a,String b,long c,String d,String e,Instant f){} public void renew(String a,long b,Instant c,Instant d){} public void retry(String a,long b,Instant c,String d,String e,String f,Instant g){} public void deadLetter(String a,long b,String c,String d,String e,Instant f){} public void succeed(String a,long b,String c,Instant d){} public boolean replay(String a,String b,String c,long d,Instant e){return true;}
    }
}
