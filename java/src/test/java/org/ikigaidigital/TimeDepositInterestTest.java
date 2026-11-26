package org.ikigaidigital;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the shared domain calculation independently of the legacy mutation wrapper. */
class TimeDepositInterestTest {
    /** A Student principal of 6.00 earns 0.01 and stays at 6.00 until a caller applies the credit. */
    @Test
    void returnsRoundedInterestWithoutChangingTheDepositBalance() {
        TimeDeposit deposit = new TimeDeposit(1, "student", 6.00, 31);

        BigDecimal interest = TimeDepositInterest.calculateMonthlyInterest(deposit);

        assertThat(interest).isEqualTo(new BigDecimal("0.01"));
        assertThat(deposit.getBalance()).isEqualTo(6.00);
    }
}
