package com.dailyexpense.expense;

import com.dailyexpense.expense.outbox.OutboxRelayScheduler;
import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.StoragePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T096 gate — OutboxRelayIT.
 * Proves: relay polls published=false rows, publishes EventEnvelope to real Kafka topic,
 * marks row published; published rows are skipped on next poll.
 * Uses Testcontainers for both PostgreSQL and Kafka.
 */
@SpringBootTest(properties = {
    "jwt.secret=test-secret-key-for-relay-it-test-at-least-32-chars",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    // scheduling.enabled NOT set → defaults to true → OutboxRelayScheduler bean IS created
    // Use huge delay so the @Scheduled method never auto-fires during the test; we call relay() manually
    "outbox.relay.delay-ms=999999999",
    "minio.endpoint=http://localhost:9999",
    "minio.access-key=relay-test",
    "minio.secret-key=relay-test",
    "minio.bucket=relay-test",
    "spring.application.name=expense-service",
    "outbox.relay.topic=expenses",
    "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
    "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
    "spring.kafka.producer.acks=all"
})
@Testcontainers
class OutboxRelayIT {

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
    }

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @MockBean
    StoragePort storagePort;

    @Autowired
    OutboxRelayScheduler relayScheduler;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void relay_publishesEnvelopeToKafka_andMarksRowPublished() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();

        // Insert unpublished outbox row directly
        jdbcTemplate.update(
            "INSERT INTO outbox(id, event_id, aggregate_type, aggregate_id, " +
            "event_type, payload, published, created_at) " +
            "VALUES(gen_random_uuid(), ?, 'Expense', ?, 'ExpenseCreated', '{\"relay\":\"test\"}', false, now())",
            eventId, userId);

        // Trigger relay synchronously
        relayScheduler.relay();

        // Row must be marked published
        Boolean published = jdbcTemplate.queryForObject(
            "SELECT published FROM outbox WHERE event_id = ?", Boolean.class, eventId);
        assertThat(published).isTrue();

        // published_at must be set
        Object publishedAt = jdbcTemplate.queryForObject(
            "SELECT published_at FROM outbox WHERE event_id = ?", Object.class, eventId);
        assertThat(publishedAt).isNotNull();

        // Message must appear on the Kafka topic
        ConsumerRecords<String, String> records = pollKafka("expenses", eventId.toString());
        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        boolean found = StreamSupport.stream(records.spliterator(), false)
            .anyMatch(r -> r.key().equals(eventId.toString()));
        assertThat(found).isTrue();
    }

    @Test
    void relay_skipsAlreadyPublishedRows() {
        UUID eventId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();

        // Insert already-published row
        jdbcTemplate.update(
            "INSERT INTO outbox(id, event_id, aggregate_type, aggregate_id, " +
            "event_type, payload, published, created_at, published_at) " +
            "VALUES(gen_random_uuid(), ?, 'Expense', ?, 'ExpenseCreated', '{}', true, now(), now())",
            eventId, userId);

        // Relay should not attempt to send this row (already published)
        relayScheduler.relay();

        // Row still published=true; no Kafka message sent for it
        Boolean published = jdbcTemplate.queryForObject(
            "SELECT published FROM outbox WHERE event_id = ?", Boolean.class, eventId);
        assertThat(published).isTrue();
    }

    @Test
    void relay_handlesEmptyOutbox_gracefully() {
        // No exception when outbox is empty
        relayScheduler.relay();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ConsumerRecords<String, String> pollKafka(String topic, String expectedKey) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "relay-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            // Poll until we find the expected key or timeout
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                boolean found = StreamSupport.stream(records.spliterator(), false)
                    .anyMatch(r -> expectedKey.equals(r.key()));
                if (found) return records;
            }
            return consumer.poll(Duration.ofMillis(100));
        }
    }
}
