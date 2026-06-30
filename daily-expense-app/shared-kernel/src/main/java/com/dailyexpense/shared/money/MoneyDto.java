package com.dailyexpense.shared.money;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object for monetary amounts. INR-only, scale-2, BigDecimal — never double/float (DB-5).
 * Serializes amount as a JSON string "100.50" to avoid floating-point representation errors.
 */
public final class MoneyDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private final BigDecimal amount;

    private final String currency;

    @JsonCreator
    public MoneyDto(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency) {
        if (currency == null || !"INR".equals(currency)) {
            throw new IllegalArgumentException("Currency must be INR; received: " + currency);
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    // Explicit: there is NO MoneyDto(double, String) constructor — double/float are prohibited (DB-5).

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    @JsonProperty("amount")
    public BigDecimal getAmount() {
        return amount;
    }

    @JsonProperty("currency")
    public String getCurrency() {
        return currency;
    }

    public static MoneyDto ofInr(BigDecimal amount) {
        return new MoneyDto(amount, "INR");
    }
}
