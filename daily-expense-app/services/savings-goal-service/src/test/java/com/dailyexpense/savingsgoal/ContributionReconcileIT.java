package com.dailyexpense.savingsgoal;

import com.dailyexpense.savingsgoal.domain.ContributionSource;
import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.port.ContributionPort;
import com.dailyexpense.savingsgoal.repository.ContributionEntryRepository;
import com.dailyexpense.savingsgoal.repository.SavingsGoalRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * T082 — ContributionReconcileIT: real Kafka (Testcontainers) proves:
 * - Secondary flow: ExpenseLinkedToSavingsGoalEvent → entry source=LINKED_EXPENSE
 * - Reconciliation: amount-adjust, unlink/delete → entry updated/removed + total recomputed
 * - Idempotency: duplicate eventId → single insert (processed_events guard)
 * - Auto-complete: single SavingsGoalCompletedEvent when total reaches target
 */
@SpringBootTest(
    properties = {
        "jwt.secret=test-secret-key-for-reconcile-it-test-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "outbox.relay.delay-ms=999999999",
        "scheduling.enabled=false"
    }
)
@Testcontainers
class ContributionReconcileIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("savings_goal_db")
        .withUsername("savings_goal_user")
        .withPassword("savings_goal_pass");

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

    @MockBean
    ContributionPort contributionPort;

    @Autowired
    SavingsGoalRepository goalRepository;

    @Autowired
    ContributionEntryRepository entryRepository;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() {
        entryRepository.deleteAll();
        goalRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM processed_events");
        jdbcTemplate.execute("DELETE FROM outbox");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }

    // ── Secondary flow: ExpenseLinkedToSavingsGoalEvent ───────────────────────

    @Test
    void linkedExpenseEvent_createsEntry_source_LINKED_EXPENSE() throws Exception {
        SavingsGoal goal = createGoal("Holiday", new BigDecimal("50000"));
        UUID expenseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String envelope = buildEnvelope(eventId, "ExpenseLinkedToSavingsGoalEvent",
            goal.getUserId(),
            """
            {"savingsGoalId":"%s","userId":"%s","expenseId":"%s","amount":"10000","date":"2026-06-28"}
            """.formatted(goal.getId(), goal.getUserId(), expenseId));

        producer.send(new ProducerRecord<>("expenses", eventId.toString(), envelope)).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            var entries = entryRepository.findBySavingsGoalId(goal.getId(),
                org.springframework.data.domain.Pageable.unpaged());
            assertThat(entries.getContent()).hasSize(1);
            assertThat(entries.getContent().get(0).getSource()).isEqualTo(ContributionSource.LINKED_EXPENSE);
            assertThat(entries.getContent().get(0).getExpenseId()).isEqualTo(expenseId);
        });

        SavingsGoal updated = goalRepository.findById(goal.getId()).orElseThrow();
        assertThat(updated.getTotalContributed()).isEqualByComparingTo("10000");
    }

    // ── Idempotency: duplicate eventId → single insert ────────────────────────

    @Test
    void linkedExpenseEvent_duplicate_singleEffect() throws Exception {
        SavingsGoal goal = createGoal("Dedup Test", new BigDecimal("50000"));
        UUID expenseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String envelope = buildEnvelope(eventId, "ExpenseLinkedToSavingsGoalEvent",
            goal.getUserId(),
            """
            {"savingsGoalId":"%s","userId":"%s","expenseId":"%s","amount":"5000","date":"2026-06-28"}
            """.formatted(goal.getId(), goal.getUserId(), expenseId));

        producer.send(new ProducerRecord<>("expenses", eventId.toString(), envelope)).get();
        producer.send(new ProducerRecord<>("expenses", eventId.toString(), envelope)).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = entryRepository.count();
            assertThat(count).isEqualTo(1);
        });

        SavingsGoal updated = goalRepository.findById(goal.getId()).orElseThrow();
        assertThat(updated.getTotalContributed()).isEqualByComparingTo("5000");
    }

    // ── Reconciliation: amount adjusted ───────────────────────────────────────

    @Test
    void amountAdjustedEvent_updatesEntry_andRecomputesTotal() throws Exception {
        SavingsGoal goal = createGoal("Adjust Test", new BigDecimal("50000"));
        UUID expenseId = UUID.randomUUID();

        sendLinkedEvent(goal, expenseId, "8000");

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(entryRepository.findBySavingsGoalIdAndExpenseId(goal.getId(), expenseId)).isPresent()
        );

        UUID adjustEventId = UUID.randomUUID();
        String adjustEnvelope = buildEnvelope(adjustEventId, "ContributionAmountAdjustedEvent",
            goal.getUserId(),
            """
            {"savingsGoalId":"%s","userId":"%s","expenseId":"%s","newAmount":"12000"}
            """.formatted(goal.getId(), goal.getUserId(), expenseId));

        producer.send(new ProducerRecord<>("expenses", adjustEventId.toString(), adjustEnvelope)).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            var entry = entryRepository.findBySavingsGoalIdAndExpenseId(goal.getId(), expenseId);
            assertThat(entry).isPresent();
            assertThat(entry.get().getAmount()).isEqualByComparingTo("12000");

            SavingsGoal updated = goalRepository.findById(goal.getId()).orElseThrow();
            assertThat(updated.getTotalContributed()).isEqualByComparingTo("12000");
        });
    }

    // ── Reconciliation: expense unlinked ──────────────────────────────────────

    @Test
    void unlinkedEvent_removesEntry_andRecomputesTotal() throws Exception {
        SavingsGoal goal = createGoal("Unlink Test", new BigDecimal("50000"));
        UUID expenseId = UUID.randomUUID();

        sendLinkedEvent(goal, expenseId, "5000");
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(entryRepository.findBySavingsGoalIdAndExpenseId(goal.getId(), expenseId)).isPresent()
        );

        UUID unlinkEventId = UUID.randomUUID();
        String unlinkEnvelope = buildEnvelope(unlinkEventId, "ExpenseUnlinkedFromSavingsGoalEvent",
            goal.getUserId(),
            """
            {"savingsGoalId":"%s","userId":"%s","expenseId":"%s"}
            """.formatted(goal.getId(), goal.getUserId(), expenseId));

        producer.send(new ProducerRecord<>("expenses", unlinkEventId.toString(), unlinkEnvelope)).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(entryRepository.findBySavingsGoalIdAndExpenseId(goal.getId(), expenseId)).isEmpty();

            SavingsGoal updated = goalRepository.findById(goal.getId()).orElseThrow();
            assertThat(updated.getTotalContributed()).isEqualByComparingTo("0");
        });
    }

    // ── Auto-complete via secondary flow ──────────────────────────────────────

    @Test
    void autoComplete_viaLinkedEvent_firesExactlyOnce() throws Exception {
        SavingsGoal goal = createGoal("Auto-Complete via Event", new BigDecimal("10000"));
        UUID expenseId = UUID.randomUUID();

        sendLinkedEvent(goal, expenseId, "10000");

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            SavingsGoal updated = goalRepository.findById(goal.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(GoalStatus.COMPLETED);
        });

        int completedEvents = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE event_type='SavingsGoalCompletedEvent'", Integer.class);
        assertThat(completedEvents).isEqualTo(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SavingsGoal createGoal(String name, BigDecimal target) {
        SavingsGoal goal = new SavingsGoal();
        goal.setId(UUID.randomUUID());
        goal.setUserId(UUID.randomUUID());
        goal.setName(name);
        goal.setTargetAmount(target);
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setTotalContributed(BigDecimal.ZERO);
        goal.setCreatedAt(Instant.now());
        goal.setUpdatedAt(Instant.now());
        return goalRepository.save(goal);
    }

    private void sendLinkedEvent(SavingsGoal goal, UUID expenseId, String amount) throws Exception {
        UUID eventId = UUID.randomUUID();
        String envelope = buildEnvelope(eventId, "ExpenseLinkedToSavingsGoalEvent",
            goal.getUserId(),
            """
            {"savingsGoalId":"%s","userId":"%s","expenseId":"%s","amount":"%s","date":"2026-06-28"}
            """.formatted(goal.getId(), goal.getUserId(), expenseId, amount));
        producer.send(new ProducerRecord<>("expenses", eventId.toString(), envelope)).get();
    }

    private String buildEnvelope(UUID eventId, String eventType, UUID userId, String payload) {
        return """
            {"eventId":"%s","eventType":"%s","eventVersion":"1.0","occurredAt":"%s",
             "producer":"expense-service","userId":"%s","traceId":null,"payload":"%s"}
            """.formatted(
            eventId, eventType, Instant.now(),
            userId,
            payload.replace("\"", "\\\"").replace("\n", ""));
    }
}
