package com.dailyexpense.budget;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.domain.BudgetPeriodLedger;
import com.dailyexpense.budget.domain.BudgetScope;
import com.dailyexpense.budget.domain.PeriodType;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.budget.repository.BudgetRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * T093 — BUD-INV-5 proof: EIGHTY_PERCENT and EXCEEDED each fire AT MOST ONCE per period.
 * Uses real Kafka (Testcontainers). Duplicate Kafka redelivery of the same eventId
 * is absorbed by ProcessedEventGuard (processed_events table) + fired_* flags on the ledger.
 * BUD-INV-7: deactivated budget → no alerts, even when thresholds crossed.
 */
@SpringBootTest(
    properties = {
        "jwt.secret=test-secret-key-for-budget-alert-kafka-test-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "outbox.relay.delay-ms=999999999",
        "scheduling.enabled=false"
    }
)
@Testcontainers
class BudgetAlertKafkaRedeliveryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("budget_db")
        .withUsername("budget_user")
        .withPassword("budget_pass");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("kafka.topics.expenses", () -> "expenses");
    }

    @Autowired
    BudgetRepository budgetRepository;

    @Autowired
    BudgetPeriodLedgerRepository ledgerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM budget_period_ledgers");
        budgetRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM processed_events");
        jdbcTemplate.execute("DELETE FROM outbox");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }

    // ── BUD-INV-5: EIGHTY_PERCENT fires exactly once ─────────────────────────

    @Test
    void eightyPercentAlert_firesExactlyOnce_onRepeatsAndDuplicateRedelivery() throws Exception {
        UUID userId = UUID.randomUUID();
        Budget budget = createActiveBudget(userId, new BigDecimal("10000"));
        BudgetPeriodLedger ledger = createCurrentLedger(budget);

        UUID eventId = UUID.randomUUID();
        // 8500 spent → 85% → crosses 80% threshold
        String envelope = buildExpenseEnvelope(eventId, userId, null, "8500");

        // Send same event twice (simulates Kafka at-least-once redelivery)
        producer.send(new ProducerRecord<>("expenses", eventId.toString(), envelope)).get();
        producer.send(new ProducerRecord<>("expenses", eventId.toString(), envelope)).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            int alerts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type='BudgetAlertFiredEvent' " +
                "AND payload LIKE '%EIGHTY_PERCENT%'", Integer.class);
            assertThat(alerts).isEqualTo(1);
        });

        // Verify flag set in ledger
        BudgetPeriodLedger updated = ledgerRepository.findById(ledger.getId()).orElseThrow();
        assertThat(updated.isFiredEightyPercent()).isTrue();
        assertThat(updated.isFiredExceeded()).isFalse();
    }

    // ── BUD-INV-5: EXCEEDED fires exactly once ────────────────────────────────

    @Test
    void exceededAlert_firesExactlyOnce_onDuplicateRedelivery() throws Exception {
        UUID userId = UUID.randomUUID();
        Budget budget = createActiveBudget(userId, new BigDecimal("5000"));
        BudgetPeriodLedger ledger = createCurrentLedger(budget);

        UUID eventId = UUID.randomUUID();
        // 6000 spent → 120% → crosses both 80% and EXCEEDED
        String envelope = buildExpenseEnvelope(eventId, userId, null, "6000");

        // Send twice
        producer.send(new ProducerRecord<>("expenses", eventId.toString(), envelope)).get();
        producer.send(new ProducerRecord<>("expenses", eventId.toString(), envelope)).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            int eighty = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type='BudgetAlertFiredEvent' " +
                "AND payload LIKE '%EIGHTY_PERCENT%'", Integer.class);
            int exceeded = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type='BudgetAlertFiredEvent' " +
                "AND payload LIKE '%EXCEEDED%'", Integer.class);
            assertThat(eighty).isEqualTo(1);
            assertThat(exceeded).isEqualTo(1);
        });

        BudgetPeriodLedger updated = ledgerRepository.findById(ledger.getId()).orElseThrow();
        assertThat(updated.isFiredEightyPercent()).isTrue();
        assertThat(updated.isFiredExceeded()).isTrue();
    }

    // ── BUD-INV-5: separate events don't re-trigger already-fired flags ───────

    @Test
    void eightyPercent_alreadyFired_secondEventProducesNoNewAlert() throws Exception {
        UUID userId = UUID.randomUUID();
        Budget budget = createActiveBudget(userId, new BigDecimal("10000"));
        createCurrentLedger(budget);

        UUID firstEventId = UUID.randomUUID();
        // 8500 → triggers 80%
        producer.send(new ProducerRecord<>("expenses", firstEventId.toString(),
            buildExpenseEnvelope(firstEventId, userId, null, "8500"))).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            int alerts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type='BudgetAlertFiredEvent' " +
                "AND payload LIKE '%EIGHTY_PERCENT%'", Integer.class);
            assertThat(alerts).isEqualTo(1);
        });

        // Clear outbox to verify second event produces nothing new
        jdbcTemplate.execute("DELETE FROM outbox");

        UUID secondEventId = UUID.randomUUID();
        // Another 500 → 90% but flag already set → no second alert
        producer.send(new ProducerRecord<>("expenses", secondEventId.toString(),
            buildExpenseEnvelope(secondEventId, userId, null, "500"))).get();

        // Wait for processing, then verify no new alert
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            int processed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_type='ExpenseCreatedEvent'",
                Integer.class);
            assertThat(processed).isGreaterThanOrEqualTo(2);
        });

        int newAlerts = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE event_type='BudgetAlertFiredEvent' " +
            "AND payload LIKE '%EIGHTY_PERCENT%'", Integer.class);
        assertThat(newAlerts).isEqualTo(0);
    }

    // ── BUD-INV-7: deactivated budget fires no alerts ─────────────────────────

    @Test
    void deactivatedBudget_noAlertsEvenWhenThresholdCrossed() throws Exception {
        UUID userId = UUID.randomUUID();
        Budget budget = createActiveBudget(userId, new BigDecimal("10000"));
        createCurrentLedger(budget);

        // Deactivate budget
        budget.setActive(false);
        budget.setUpdatedAt(Instant.now());
        budgetRepository.save(budget);

        UUID eventId = UUID.randomUUID();
        // 9000 spent → 90% → would trigger 80% and EXCEEDED alerts if active
        producer.send(new ProducerRecord<>("expenses", eventId.toString(),
            buildExpenseEnvelope(eventId, userId, null, "9000"))).get();

        // Wait for event to be processed
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            int processed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_type='ExpenseCreatedEvent'",
                Integer.class);
            assertThat(processed).isGreaterThanOrEqualTo(1);
        });

        // No alerts should have been fired
        int alerts = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE event_type='BudgetAlertFiredEvent'", Integer.class);
        assertThat(alerts).isEqualTo(0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Budget createActiveBudget(UUID userId, BigDecimal limit) {
        Instant now = Instant.now();
        Budget b = new Budget();
        b.setId(UUID.randomUUID());
        b.setUserId(userId);
        b.setScope(BudgetScope.OVERALL);
        b.setBudgetLimit(limit);
        b.setPeriodType(PeriodType.MONTHLY);
        b.setActive(true);
        b.setRolloverEnabled(false);
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        return budgetRepository.save(b);
    }

    private BudgetPeriodLedger createCurrentLedger(Budget budget) {
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();
        BudgetPeriodLedger l = new BudgetPeriodLedger();
        l.setId(UUID.randomUUID());
        l.setBudgetId(budget.getId());
        l.setUserId(budget.getUserId());
        l.setPeriodStart(today.withDayOfMonth(1));
        l.setPeriodEnd(today.withDayOfMonth(today.lengthOfMonth()));
        l.setCarriedIn(BigDecimal.ZERO);
        l.setSpent(BigDecimal.ZERO);
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        return ledgerRepository.save(l);
    }

    private String buildExpenseEnvelope(UUID eventId, UUID userId, UUID categoryId, String amount) {
        String catField = categoryId != null
            ? "\"categoryId\":\"" + categoryId + "\","
            : "";
        String payloadJson = String.format(
            "{\"userId\":\"%s\",%s\"amount\":\"%s\",\"date\":\"%s\"}",
            userId, catField, amount, LocalDate.now());
        String escapedPayload = payloadJson.replace("\"", "\\\"");
        return String.format(
            "{\"eventId\":\"%s\",\"eventType\":\"ExpenseCreatedEvent\",\"eventVersion\":\"1.0\"," +
            "\"occurredAt\":\"%s\",\"producer\":\"expense-service\",\"userId\":\"%s\"," +
            "\"traceId\":null,\"payload\":\"%s\"}",
            eventId, Instant.now(), userId, escapedPayload);
    }
}
