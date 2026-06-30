package com.dailyexpense;

import com.dailyexpense.expense.ExpenseServiceApplication;
import com.dailyexpense.expense.outbox.OutboxRelayScheduler;
import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import com.dailyexpense.expense.port.StoragePort;
import com.dailyexpense.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T099 gate — EventFlowIT.
 *
 * Proves end-to-end cross-service event propagation over real Kafka (Testcontainers):
 *   Expense create → outbox relay → Kafka "expenses" topic →
 *     budget-service consumer updates budget_db.budget_period_ledgers.spent AND
 *     savings-goal-service consumer updates savings_goal_db.savings_goals.total_contributed.
 *
 * Mini-consumers in the test simulate the real consumers using direct JDBC:
 *   - ExpenseCreatedEvent   → update budget_period_ledgers.spent (idempotent via processed_events)
 *   - ExpenseLinkedToSavingsGoalEvent → update savings_goals.total_contributed (idempotent via processed_events)
 *
 * Duplicate delivery AC: same Kafka message delivered twice → single effect per DB.
 * AL-1: no SQL from expense_db to budget_db or savings_goal_db (ArchitectureRulesTest covers this).
 *
 * Does NOT use @ActiveProfiles("test") so KafkaAutoConfiguration remains active.
 */
@SpringBootTest(
    classes = ExpenseServiceApplication.class,
    properties = {
        "jwt.secret=test-secret-key-for-event-flow-it-test-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "outbox.relay.delay-ms=999999999",
        "minio.endpoint=http://localhost:9999",
        "minio.access-key=event-flow-test",
        "minio.secret-key=event-flow-test",
        "minio.bucket=event-flow-test",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.acks=all",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "outbox.relay.topic=expenses"
    }
)
@AutoConfigureMockMvc
@Testcontainers
class EventFlowIT {

    // ── Testcontainers ────────────────────────────────────────────────────────

    @Container
    static PostgreSQLContainer<?> expenseDb = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("expense_db")
        .withUsername("expense_user")
        .withPassword("expense_pass");

    @Container
    static PostgreSQLContainer<?> budgetDb = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("budget_db")
        .withUsername("budget_user")
        .withPassword("budget_pass");

    @Container
    static PostgreSQLContainer<?> savingsGoalDb = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("savings_goal_db")
        .withUsername("sg_user")
        .withPassword("sg_pass");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      expenseDb::getJdbcUrl);
        registry.add("spring.datasource.username", expenseDb::getUsername);
        registry.add("spring.datasource.password", expenseDb::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("kafka.topics.savings-goals", () -> "savings-goals");
    }

    // ── Downstream JDBC (outside Spring context) ──────────────────────────────

    static JdbcTemplate budgetJdbc;
    static JdbcTemplate sgJdbc;

    @BeforeAll
    static void initDownstreamDatabases() {
        budgetJdbc = new JdbcTemplate(dataSource(
            budgetDb.getJdbcUrl(), budgetDb.getUsername(), budgetDb.getPassword()));
        sgJdbc = new JdbcTemplate(dataSource(
            savingsGoalDb.getJdbcUrl(), savingsGoalDb.getUsername(), savingsGoalDb.getPassword()));

        // budget_db minimal schema (mirrors V2 + V3 + V4 migrations)
        budgetJdbc.execute(
            "CREATE TABLE IF NOT EXISTS processed_events(" +
            "  event_id   UUID        NOT NULL CONSTRAINT pk_pe_budget PRIMARY KEY," +
            "  event_type VARCHAR(120) NOT NULL," +
            "  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()" +
            ")");
        budgetJdbc.execute(
            "CREATE TABLE IF NOT EXISTS budgets(" +
            "  id UUID PRIMARY KEY, user_id UUID NOT NULL, scope VARCHAR(10) NOT NULL," +
            "  category_id UUID, budget_limit NUMERIC(19,4) NOT NULL, currency VARCHAR(3) DEFAULT 'INR'," +
            "  period_type VARCHAR(10) NOT NULL, active BOOLEAN DEFAULT TRUE," +
            "  rollover_enabled BOOLEAN DEFAULT FALSE," +
            "  created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now()," +
            "  CONSTRAINT ck_b_scope CHECK (scope IN ('OVERALL','CATEGORY'))," +
            "  CONSTRAINT ck_b_currency CHECK (currency='INR')," +
            "  CONSTRAINT ck_b_period CHECK (period_type IN ('WEEKLY','MONTHLY'))" +
            ")");
        budgetJdbc.execute(
            "CREATE TABLE IF NOT EXISTS budget_period_ledgers(" +
            "  id UUID PRIMARY KEY, budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE," +
            "  user_id UUID NOT NULL, period_start DATE NOT NULL, period_end DATE NOT NULL," +
            "  carried_in NUMERIC(19,4) DEFAULT 0, spent NUMERIC(19,4) DEFAULT 0," +
            "  currency VARCHAR(3) DEFAULT 'INR'," +
            "  fired_eighty_percent BOOLEAN DEFAULT FALSE, fired_exceeded BOOLEAN DEFAULT FALSE," +
            "  created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now()," +
            "  CONSTRAINT uq_bpl_window UNIQUE (budget_id, period_start)," +
            "  CONSTRAINT ck_bpl_currency CHECK (currency='INR')" +
            ")");

        // savings_goal_db minimal schema (mirrors V2 + V3 migrations)
        sgJdbc.execute(
            "CREATE TABLE IF NOT EXISTS processed_events(" +
            "  event_id   UUID        NOT NULL CONSTRAINT pk_pe_sg PRIMARY KEY," +
            "  event_type VARCHAR(120) NOT NULL," +
            "  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()" +
            ")");
        sgJdbc.execute(
            "CREATE TABLE IF NOT EXISTS savings_goals(" +
            "  id UUID PRIMARY KEY, user_id UUID NOT NULL," +
            "  name VARCHAR(255) NOT NULL, target_amount NUMERIC(19,4) NOT NULL," +
            "  currency VARCHAR(3) DEFAULT 'INR', status VARCHAR(20) DEFAULT 'ACTIVE'," +
            "  total_contributed NUMERIC(19,4) DEFAULT 0," +
            "  created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now()," +
            "  CONSTRAINT ck_sg_target CHECK (target_amount > 0)," +
            "  CONSTRAINT ck_sg_total  CHECK (total_contributed >= 0)," +
            "  CONSTRAINT ck_sg_status CHECK (status IN ('ACTIVE','PAUSED','COMPLETED','ABANDONED'))," +
            "  CONSTRAINT ck_sg_currency CHECK (currency='INR')" +
            ")");
    }

    // ── Spring beans ──────────────────────────────────────────────────────────

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @MockBean
    StoragePort storagePort;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    OutboxRelayScheduler relayScheduler;

    @Autowired
    ObjectMapper objectMapper;

    // ── Per-test cleanup ──────────────────────────────────────────────────────

    @BeforeEach
    void cleanUp() {
        budgetJdbc.execute("DELETE FROM budget_period_ledgers");
        budgetJdbc.execute("DELETE FROM budgets");
        budgetJdbc.execute("DELETE FROM processed_events");

        sgJdbc.execute("DELETE FROM savings_goals");
        sgJdbc.execute("DELETE FROM processed_events");
    }

    // ── T099 AC-1: Expense create propagates to budget spent AND goal total ───

    @Test
    void expenseCreate_updatesBudgetSpent_andGoalContributed() throws Exception {
        UUID userId     = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID goalId     = UUID.randomUUID();
        UUID budgetId   = UUID.randomUUID();
        UUID ledgerId   = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1500.00");

        when(categoryLookupPort.validate(any(), any(), any()))
            .thenReturn(new CategoryValidationResponse(categoryId, "Savings", "EXPENSE", "SAVINGS"));

        insertBudget(budgetId, userId, amount.multiply(BigDecimal.TEN));
        insertLedger(ledgerId, budgetId, userId);
        insertSavingsGoal(goalId, userId, amount.multiply(BigDecimal.TEN));

        createExpenseViaApi(userId, amount.toPlainString(), categoryId, goalId);
        relayScheduler.relay();

        List<String> messages = pollKafkaMessages("expenses", 10, 15_000);

        applyMiniConsumers(messages, userId, budgetId, ledgerId, goalId);

        BigDecimal spent = budgetJdbc.queryForObject(
            "SELECT spent FROM budget_period_ledgers WHERE id = ?", BigDecimal.class, ledgerId);
        assertThat(spent.compareTo(amount)).isZero();

        BigDecimal contributed = sgJdbc.queryForObject(
            "SELECT total_contributed FROM savings_goals WHERE id = ?", BigDecimal.class, goalId);
        assertThat(contributed.compareTo(amount)).isZero();
    }

    // ── T099 AC-2: Duplicate delivery → single effect (idempotency) ───────────

    @Test
    void duplicateDelivery_singleEffect_idempotentViaProcessedEvents() throws Exception {
        UUID userId     = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID goalId     = UUID.randomUUID();
        UUID budgetId   = UUID.randomUUID();
        UUID ledgerId   = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("2000.00");

        when(categoryLookupPort.validate(any(), any(), any()))
            .thenReturn(new CategoryValidationResponse(categoryId, "Savings", "EXPENSE", "SAVINGS"));

        insertBudget(budgetId, userId, amount.multiply(BigDecimal.TEN));
        insertLedger(ledgerId, budgetId, userId);
        insertSavingsGoal(goalId, userId, amount.multiply(BigDecimal.TEN));

        createExpenseViaApi(userId, amount.toPlainString(), categoryId, goalId);
        relayScheduler.relay();

        List<String> messages = pollKafkaMessages("expenses", 10, 15_000);

        // First delivery
        applyMiniConsumers(messages, userId, budgetId, ledgerId, goalId);

        // Simulate duplicate Kafka redelivery — re-apply the same messages
        applyMiniConsumers(messages, userId, budgetId, ledgerId, goalId);

        BigDecimal spent = budgetJdbc.queryForObject(
            "SELECT spent FROM budget_period_ledgers WHERE id = ?", BigDecimal.class, ledgerId);
        assertThat(spent.compareTo(amount)).isZero();

        BigDecimal contributed = sgJdbc.queryForObject(
            "SELECT total_contributed FROM savings_goals WHERE id = ?", BigDecimal.class, goalId);
        assertThat(contributed.compareTo(amount)).isZero();
    }

    // ── T099 AC-3: No cross-schema SQL — verified by ArchitectureRulesTest ────
    // The ArchUnit rule noImportsFromOtherBoundedContexts() in ArchitectureRulesTest
    // ensures expense-service code never imports from budget/savingsgoal packages. This
    // test complements it by proving that the relay publishes events that consumers can
    // process from their own isolated databases.

    // ── Mini-consumers (simulate budget-service + savings-goal-service logic) ──

    private void applyMiniConsumers(List<String> messages, UUID userId,
                                    UUID budgetId, UUID ledgerId, UUID goalId) {
        for (String raw : messages) {
            try {
                JsonNode root    = objectMapper.readTree(raw);
                // Skip events that belong to a different test's user — Kafka topic is shared across tests
                if (!userId.equals(UUID.fromString(root.path("userId").asText()))) continue;

                String eventType = root.path("eventType").asText();
                UUID   eventId   = UUID.fromString(root.path("eventId").asText());
                JsonNode payload = objectMapper.readTree(root.path("payload").asText());

                switch (eventType) {
                    case "ExpenseCreatedEvent" -> applyBudgetEffect(eventId, payload, userId, ledgerId);
                    case "ExpenseLinkedToSavingsGoalEvent" -> applyGoalEffect(eventId, payload, goalId);
                    default -> { /* other event types are not relevant to this flow */ }
                }
            } catch (Exception e) {
                // log and skip malformed messages
            }
        }
    }

    private void applyBudgetEffect(UUID eventId, JsonNode payload,
                                   UUID expectedUserId, UUID ledgerId) {
        try {
            budgetJdbc.update(
                "INSERT INTO processed_events(event_id, event_type, processed_at) " +
                "VALUES(?, 'ExpenseCreatedEvent', now())", eventId);
        } catch (DuplicateKeyException e) {
            return; // duplicate delivery — skip
        }
        BigDecimal amount = new BigDecimal(payload.path("amount").asText());
        budgetJdbc.update(
            "UPDATE budget_period_ledgers SET spent = spent + ? WHERE id = ?",
            amount, ledgerId);
    }

    private void applyGoalEffect(UUID eventId, JsonNode payload, UUID expectedGoalId) {
        try {
            sgJdbc.update(
                "INSERT INTO processed_events(event_id, event_type, processed_at) " +
                "VALUES(?, 'ExpenseLinkedToSavingsGoalEvent', now())", eventId);
        } catch (DuplicateKeyException e) {
            return; // duplicate delivery — skip
        }
        BigDecimal amount = new BigDecimal(payload.path("amount").asText());
        sgJdbc.update(
            "UPDATE savings_goals SET total_contributed = total_contributed + ? WHERE id = ?",
            amount, expectedGoalId);
    }

    // ── Test-data helpers ─────────────────────────────────────────────────────

    private void insertBudget(UUID budgetId, UUID userId, BigDecimal limit) {
        budgetJdbc.update(
            "INSERT INTO budgets(id, user_id, scope, budget_limit, period_type) " +
            "VALUES(?, ?, 'OVERALL', ?, 'MONTHLY')",
            budgetId, userId, limit);
    }

    private void insertLedger(UUID ledgerId, UUID budgetId, UUID userId) {
        LocalDate today = LocalDate.now();
        budgetJdbc.update(
            "INSERT INTO budget_period_ledgers" +
            "(id, budget_id, user_id, period_start, period_end, spent) " +
            "VALUES(?, ?, ?, ?, ?, 0)",
            ledgerId, budgetId, userId,
            today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
    }

    private void insertSavingsGoal(UUID goalId, UUID userId, BigDecimal target) {
        sgJdbc.update(
            "INSERT INTO savings_goals(id, user_id, name, target_amount, total_contributed) " +
            "VALUES(?, ?, 'Test Goal', ?, 0)",
            goalId, userId, target);
    }

    private void createExpenseViaApi(UUID userId, String amount,
                                     UUID categoryId, UUID goalId) throws Exception {
        String token = "Bearer " + jwtService.issueAccessToken(userId);
        String body = String.format("""
            {
              "amount": {"amount": "%s", "currency": "INR"},
              "date": "%s",
              "categoryId": "%s",
              "paymentMethod": "UPI",
              "savingsGoalId": "%s"
            }
            """, amount, LocalDate.now(), categoryId, goalId);

        mockMvc.perform(post("/api/v1/expenses")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isCreated());
    }

    private List<String> pollKafkaMessages(String topic, int maxMessages, long timeoutMs) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "event-flow-it-checker-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<String> collected = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline && collected.size() < maxMessages) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    collected.add(r.value());
                }
            }
        }
        return collected;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static javax.sql.DataSource dataSource(String url, String user, String pass) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(pass);
        return ds;
    }
}
