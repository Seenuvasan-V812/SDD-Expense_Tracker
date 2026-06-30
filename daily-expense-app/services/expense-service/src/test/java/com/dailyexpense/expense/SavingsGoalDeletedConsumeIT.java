package com.dailyexpense.expense;

import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.StoragePort;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * T119 gate — SavingsGoalDeletedConsumeIT.
 *
 * Proves:
 * 1. SavingsGoalDeletedEvent consumed → UPDATE expenses SET savings_goal_id=NULL WHERE
 *    savings_goal_id=:goalId AND user_id=:userId (Expenses NOT deleted).
 * 2. Idempotent: duplicate Kafka delivery of same eventId → single UPDATE (dup row in
 *    expense_db.processed_events prevents second run).
 * 3. No savings_goal_db SQL (AL-1) — verified by ArchitectureRulesTest.
 *
 * Uses Testcontainers for real Kafka and expense_db PostgreSQL.
 * Does NOT use @ActiveProfiles("test") so KafkaAutoConfiguration is NOT excluded.
 */
@SpringBootTest(
    properties = {
        "jwt.secret=test-secret-key-for-savings-goal-deleted-it-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "outbox.relay.delay-ms=999999999",
        "scheduling.enabled=false",
        "minio.endpoint=http://localhost:9999",
        "minio.access-key=sg-deleted-test",
        "minio.secret-key=sg-deleted-test",
        "minio.bucket=sg-deleted-test",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.acks=all",
        "spring.kafka.consumer.auto-offset-reset=earliest"
    }
)
@Testcontainers
class SavingsGoalDeletedConsumeIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("expense_db")
        .withUsername("expense_user")
        .withPassword("expense_pass");

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
        registry.add("kafka.topics.savings-goals", () -> "savings-goals");
    }

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @MockBean
    StoragePort storagePort;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM processed_events");
        jdbcTemplate.execute("DELETE FROM expenses");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }

    // ── T119 AC-1: SavingsGoalDeletedEvent → savings_goal_id set to NULL ────────

    @Test
    void savingsGoalDeleted_clearsGoalIdOnLinkedExpenses() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();

        insertExpenseWithGoal(expenseId, userId, categoryId, goalId, "2500.0000");

        String eventId = UUID.randomUUID().toString();
        String envelope = buildSavingsGoalDeletedEnvelope(eventId, goalId, userId);

        producer.send(new ProducerRecord<>("savings-goals", eventId, envelope)).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            UUID actual = jdbcTemplate.queryForObject(
                "SELECT savings_goal_id FROM expenses WHERE id = ?", UUID.class, expenseId);
            assertThat(actual).isNull();
        });

        // Expense row must still exist — NOT deleted
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM expenses WHERE id = ?", Integer.class, expenseId);
        assertThat(count).isEqualTo(1);
    }

    // ── T119 AC-2: Expenses without this goalId are unaffected ─────────────────

    @Test
    void savingsGoalDeleted_doesNotAffectExpensesWithDifferentGoalId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deletedGoalId = UUID.randomUUID();
        UUID otherGoalId   = UUID.randomUUID();
        UUID categoryId    = UUID.randomUUID();

        UUID linkedExpenseId  = UUID.randomUUID();
        UUID unlinkedExpenseId = UUID.randomUUID();

        insertExpenseWithGoal(linkedExpenseId,  userId, categoryId, deletedGoalId, "1000.0000");
        insertExpenseWithGoal(unlinkedExpenseId, userId, categoryId, otherGoalId,  "1500.0000");

        String eventId = UUID.randomUUID().toString();
        producer.send(new ProducerRecord<>("savings-goals", eventId,
            buildSavingsGoalDeletedEnvelope(eventId, deletedGoalId, userId))).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            UUID actual = jdbcTemplate.queryForObject(
                "SELECT savings_goal_id FROM expenses WHERE id = ?", UUID.class, linkedExpenseId);
            assertThat(actual).isNull();
        });

        // Expense with otherGoalId must be unchanged
        UUID otherActual = jdbcTemplate.queryForObject(
            "SELECT savings_goal_id FROM expenses WHERE id = ?", UUID.class, unlinkedExpenseId);
        assertThat(otherActual).isEqualTo(otherGoalId);
    }

    // ── T119 AC-3: Idempotent — duplicate eventId → single UPDATE ───────────────

    @Test
    void duplicateDelivery_noDoubleUpdate_idempotentViaProcessedEvents() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID expenseId = UUID.randomUUID();

        insertExpenseWithGoal(expenseId, userId, categoryId, goalId, "3000.0000");

        String eventId = UUID.randomUUID().toString();
        String envelope = buildSavingsGoalDeletedEnvelope(eventId, goalId, userId);

        // Deliver twice
        producer.send(new ProducerRecord<>("savings-goals", eventId, envelope)).get();
        producer.send(new ProducerRecord<>("savings-goals", eventId, envelope)).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            UUID actual = jdbcTemplate.queryForObject(
                "SELECT savings_goal_id FROM expenses WHERE id = ?", UUID.class, expenseId);
            assertThat(actual).isNull();
        });

        // Exactly one processed_events row for this eventId — duplicate blocked at the guard
        Integer processedCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_events WHERE event_id = ?::uuid",
            Integer.class, eventId);
        assertThat(processedCount).isEqualTo(1);
    }

    // ── T119 AC-4: Goal delete across users doesn't bleed ───────────────────────

    @Test
    void savingsGoalDeleted_userScopingPreventsBleed() throws Exception {
        UUID ownerUserId   = UUID.randomUUID();
        UUID foreignUserId = UUID.randomUUID();
        UUID goalId        = UUID.randomUUID();
        UUID categoryId    = UUID.randomUUID();

        UUID ownerExpenseId   = UUID.randomUUID();
        UUID foreignExpenseId = UUID.randomUUID();

        insertExpenseWithGoal(ownerExpenseId,   ownerUserId,   categoryId, goalId, "2000.0000");
        insertExpenseWithGoal(foreignExpenseId, foreignUserId, categoryId, goalId, "1000.0000");

        // Only ownerUserId's goal is deleted
        String eventId = UUID.randomUUID().toString();
        producer.send(new ProducerRecord<>("savings-goals", eventId,
            buildSavingsGoalDeletedEnvelope(eventId, goalId, ownerUserId))).get();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            UUID actual = jdbcTemplate.queryForObject(
                "SELECT savings_goal_id FROM expenses WHERE id = ?", UUID.class, ownerExpenseId);
            assertThat(actual).isNull();
        });

        // foreignUserId's expense must still have the goalId (different user scoping — AL-5)
        UUID foreignActual = jdbcTemplate.queryForObject(
            "SELECT savings_goal_id FROM expenses WHERE id = ?", UUID.class, foreignExpenseId);
        assertThat(foreignActual).isEqualTo(goalId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void insertExpenseWithGoal(UUID expenseId, UUID userId, UUID categoryId,
                                       UUID goalId, String amount) {
        jdbcTemplate.update(
            "INSERT INTO expenses(id, user_id, amount, currency, expense_date, category_id, " +
            "payment_method, savings_goal_id, created_at, updated_at) " +
            "VALUES(?, ?, ?, 'INR', current_date, ?, 'UPI', ?, now(), now())",
            expenseId, userId, new java.math.BigDecimal(amount), categoryId, goalId);
    }

    private String buildSavingsGoalDeletedEnvelope(String eventId, UUID goalId, UUID userId) {
        String innerPayload = String.format("{\"goalId\":\"%s\",\"userId\":\"%s\"}", goalId, userId);
        String escapedPayload = innerPayload.replace("\"", "\\\"");
        return String.format(
            "{\"eventId\":\"%s\",\"eventType\":\"SavingsGoalDeletedEvent\"," +
            "\"eventVersion\":\"1.0\",\"occurredAt\":\"%s\"," +
            "\"producer\":\"savings-goal-service\",\"userId\":\"%s\"," +
            "\"traceId\":null,\"payload\":\"%s\"}",
            eventId, Instant.now(), userId, escapedPayload);
    }
}
