package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*; import java.util.Map; import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real-MySQL-only lock evidence. It is skipped unless CAMPUS_MYSQL_IT_* or CAMPUS_M005_MYSQL_* is configured. */
@Tag("M00.05") @EnabledIf("mysqlConfigured")
class M005MySqlOutboxIT {
    private static final String URL=env("CAMPUS_MYSQL_IT_URL","CAMPUS_M005_MYSQL_URL");
    private static final String USER=env("CAMPUS_MYSQL_IT_USERNAME","CAMPUS_M005_MYSQL_USERNAME");
    private static final String PASSWORD=env("CAMPUS_MYSQL_IT_PASSWORD","CAMPUS_M005_MYSQL_PASSWORD");
    private DataSource dataSource; private JdbcTemplate jdbc; private JdbcOutboxRepository repository; private Clock clock;
    static boolean mysqlConfigured(){return URL!=null&&USER!=null&&PASSWORD!=null;}
    @BeforeEach void setUp(){dataSource=new DriverManagerDataSource(URL,USER,PASSWORD);jdbc=new JdbcTemplate(dataSource);flyway().clean();flyway().migrate();repository=new JdbcOutboxRepository(jdbc,new ObjectMapper());clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);}
    @AfterEach void tearDown(){jdbc.execute("DROP TABLE IF EXISTS m005_effect");flyway().clean();}
    @Test void secondWorkerCannotClaimAndExpiredLeaseFencesOldWorker(){repository.insertIfAbsent(event("e-1"),3,clock.instant());
        OutboxRecord first=new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(s->repository.claim("a",1,clock.instant(),clock.instant().plusSeconds(1)).get(0));
        java.util.List<OutboxRecord> secondClaim = new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(s->repository.claim("b",1,clock.instant(),clock.instant().plusSeconds(1)));
        assertThat(secondClaim).isEmpty();
        OutboxRecord second=new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(s->repository.claim("b",1,clock.instant().plusSeconds(2),clock.instant().plusSeconds(3)).get(0));
        assertThat(second.leaseToken()).isGreaterThan(first.leaseToken());
        assertThatThrownBy(()->repository.succeed("e-1",first.leaseToken(),"old",clock.instant().plusSeconds(2))).isInstanceOf(OutboxLeaseLostException.class);
    }
    @Test void twoConcurrentWorkersOnlyOneCanClaimWithSkipLocked() throws Exception {
        repository.insertIfAbsent(event("e-concurrent"),3,clock.instant());
        DataSourceTransactionManager tx = new DataSourceTransactionManager(dataSource);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<List<OutboxRecord>> first = pool.submit(() -> new TransactionTemplate(tx).execute(status -> {
            List<OutboxRecord> claimed = repository.claim("worker-a",1,clock.instant(),clock.instant().plusSeconds(30));
            firstLocked.countDown();
            try { releaseFirst.await(10,TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            return claimed;
        }));
        assertThat(firstLocked.await(10,TimeUnit.SECONDS)).isTrue();
        Future<List<OutboxRecord>> second = pool.submit(() -> new TransactionTemplate(tx).execute(status ->
                repository.claim("worker-b",1,clock.instant(),clock.instant().plusSeconds(30))));
        List<OutboxRecord> secondClaim = second.get(10,TimeUnit.SECONDS);
        releaseFirst.countDown();
        List<OutboxRecord> firstClaim = first.get(10,TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(firstClaim).hasSize(1);
        assertThat(secondClaim).isEmpty();
    }
    @Test void ackCrashAfterConsumptionCommitIsDeduplicatedOnLeaseExpiry() {
        jdbc.execute("CREATE TABLE m005_effect (event_id VARCHAR(128) PRIMARY KEY) ENGINE=InnoDB");
        repository.insertIfAbsent(event("e-crash"),3,clock.instant());
        DataSourceTransactionManager tx = new DataSourceTransactionManager(dataSource);
        OutboxRecord first = new TransactionTemplate(tx).execute(status ->
                repository.claim("worker-a",1,clock.instant(),clock.instant().plusSeconds(1)).get(0));
        assertThatThrownBy(() -> {
            new TransactionTemplate(tx).execute(status -> {
                assertThat(repository.startConsumer("e-crash","effect",first.leaseToken(),clock.instant()))
                        .isEqualTo(ConsumerStatus.PROCESSING);
                jdbc.update("INSERT INTO m005_effect(event_id) VALUES (?)","e-crash");
                repository.succeedConsumer("e-crash","effect",first.leaseToken(),clock.instant());
                return null;
            });
            throw new IllegalStateException("injected outbox ACK failure");
        }).isInstanceOf(IllegalStateException.class);
        OutboxRecord second = new TransactionTemplate(tx).execute(status ->
                repository.claim("worker-b",1,clock.instant().plusSeconds(2),clock.instant().plusSeconds(3)).get(0));
        ConsumerStatus replayStatus = new TransactionTemplate(tx).execute(status ->
                repository.startConsumer("e-crash","effect",second.leaseToken(),clock.instant().plusSeconds(2)));
        assertThat(replayStatus).isEqualTo(ConsumerStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM m005_effect WHERE event_id=?",Integer.class,"e-crash"))
                .isEqualTo(1);
        repository.succeed("e-crash",second.leaseToken(),"effect",clock.instant().plusSeconds(2));
    }
    @Test void businessRollbackDoesNotPublishEvent(){DataSourceTransactionManager tx=new DataSourceTransactionManager(dataSource);OutboxPublisher publisher=new OutboxPublisher(repository,3,clock);assertThatThrownBy(()->new TransactionTemplate(tx).execute(s->{publisher.publish(event("rollback"));s.setRollbackOnly();throw new IllegalStateException("abort");})).isInstanceOf(IllegalStateException.class);assertThat(repository.find("rollback")).isEmpty();}
    @Test void retryExhaustionCanBeObservedAsDeadLetter(){repository.insertIfAbsent(event("e-2"),1,clock.instant());OutboxRecord row=new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(s->repository.claim("a",1,clock.instant(),clock.instant().plusSeconds(30)).get(0));repository.deadLetter("e-2",row.leaseToken(),"test","RETRY_EXHAUSTED","maximum delivery attempts exhausted",clock.instant());assertThat(repository.find("e-2").orElseThrow().status()).isEqualTo(OutboxStatus.DEAD_LETTER);}
    private Flyway flyway(){return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load();}
    private static CampusEventEnvelope event(String id){return CampusEventEnvelope.builder().eventId(id).type("campus.test.created").version(1).aggregateId("1").aggregateVersion(1).schoolId("1").occurredAt(OffsetDateTime.parse("2026-01-01T00:00:00Z")).payload(Map.of("safe","yes")).build();}
    private static String env(String... names){for(String name:names){String value=System.getenv(name);if(value!=null&&!value.isBlank())return value;}return null;}
}
