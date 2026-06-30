package com.dailyexpense.shared.money;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * RED → GREEN: MoneyDto scale-2 BigDecimal + INR-only currency (DB-5).
 */
class MoneyDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesAsStringWithTwoDecimalPlaces() throws Exception {
        var money = new MoneyDto(new BigDecimal("100.5"), "INR");
        ObjectNode node = mapper.valueToTree(money);
        assertThat(node.get("amount").isTextual()).isTrue();
        assertThat(node.get("amount").asText()).isEqualTo("100.50");
        assertThat(node.get("currency").asText()).isEqualTo("INR");
    }

    @Test
    void rejectsNonInrCurrency() {
        assertThatThrownBy(() -> new MoneyDto(new BigDecimal("100.00"), "USD"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INR");
    }

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new MoneyDto(new BigDecimal("100.00"), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasNoDoubleConstructor() {
        // Verify that MoneyDto does not expose a (double, String) constructor
        boolean hasDoubleConstructor = Arrays.stream(MoneyDto.class.getDeclaredConstructors())
            .anyMatch(ctor -> Arrays.stream(ctor.getParameterTypes())
                .anyMatch(p -> p == double.class || p == Double.class));
        assertThat(hasDoubleConstructor)
            .as("MoneyDto must not have a double constructor to prevent float precision loss (DB-5)")
            .isFalse();
    }

    @Test
    void scaleIsEnforcedToTwo() {
        var money = new MoneyDto(new BigDecimal("1234.5678"), "INR");
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    void serializesExactlyTwoKeys() throws Exception {
        var money = new MoneyDto(new BigDecimal("0.01"), "INR");
        ObjectNode node = mapper.valueToTree(money);
        assertThat(node.fieldNames()).toIterable()
            .containsExactlyInAnyOrder("amount", "currency");
    }

    @Test
    void roundTripsFromJson() throws Exception {
        String json = "{\"amount\":\"250.00\",\"currency\":\"INR\"}";
        MoneyDto money = mapper.readValue(json, MoneyDto.class);
        assertThat(money.amount()).isEqualByComparingTo("250.00");
        assertThat(money.currency()).isEqualTo("INR");
    }
}
