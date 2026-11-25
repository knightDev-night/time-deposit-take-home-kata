package org.ikigaidigital;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TimeDepositInterestTest {
    @Test
    void returnsRoundedInterestWithoutChangingTheDepositBalance() {
        TimeDeposit deposit = new TimeDeposit(1, "student", 6.00, 31);

        BigDecimal interest = TimeDepositInterest.calculateMonthlyInterest(deposit);

        assertThat(interest).isEqualTo(new BigDecimal("0.01"));
        assertThat(deposit.getBalance()).isEqualTo(6.00);
    }
}
