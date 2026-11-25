package org.ikigaidigital;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TimeDepositCalculatorTest {
    private final TimeDepositCalculator calculator = new TimeDepositCalculator();

    @ParameterizedTest(name = "{0} at day {1} ends at {2}")
    @CsvSource({
        "basic,    29, 1200.00",
        "basic,    30, 1200.00",
        "basic,    31, 1201.00",
        "basic,   400, 1201.00",
        "student,  29, 1200.00",
        "student,  30, 1200.00",
        "student,  31, 1203.00",
        "student, 364, 1203.00",
        "student, 365, 1203.00",
        "student, 366, 1200.00",
        "premium,  29, 1200.00",
        "premium,  30, 1200.00",
        "premium,  31, 1200.00",
        "premium,  44, 1200.00",
        "premium,  45, 1200.00",
        "premium,  46, 1205.00",
        "premium, 400, 1205.00"
    })
    void appliesOneTwelfthOfTheAnnualRateOnlyOnEligibleDays(String planType, int days, double expectedBalance) {
        TimeDeposit deposit = new TimeDeposit(1, planType, 1200.00, days);

        calculator.updateBalance(List.of(deposit));

        assertBalance(deposit, expectedBalance);
    }

    @Test
    void appliesEachDepositsOwnPlanBalanceAndEligibilityInAMixedBatch() {
        TimeDeposit basic = new TimeDeposit(1, "basic", 2400.00, 400);
        TimeDeposit student = new TimeDeposit(2, "student", 1800.00, 365);
        TimeDeposit eligiblePremium = new TimeDeposit(3, "premium", 600.00, 46);
        TimeDeposit ineligiblePremium = new TimeDeposit(4, "premium", 1234.56, 45);

        calculator.updateBalance(List.of(basic, student, eligiblePremium, ineligiblePremium));

        assertBalance(basic, 2402.00);
        assertBalance(student, 1804.50);
        assertBalance(eligiblePremium, 602.50);
        assertBalance(ineligiblePremium, 1234.56);
    }

    @Test
    void acceptsAnEmptyDepositList() {
        List<TimeDeposit> deposits = new ArrayList<>();

        calculator.updateBalance(deposits);

        assertThat(deposits).isEmpty();
    }

    @Test
    void leavesAnEligibleZeroBalanceAtZero() {
        TimeDeposit deposit = new TimeDeposit(1, "basic", 0.00, 31);

        calculator.updateBalance(List.of(deposit));

        assertBalance(deposit, 0.00);
    }

    @ParameterizedTest(name = "{0} earns no interest")
    @ValueSource(strings = {"unknown", "Basic", "STUDENT", "Premium"})
    void leavesUnknownAndDifferentlyCasedPlansUnchanged(String planType) {
        TimeDeposit deposit = new TimeDeposit(1, planType, 1200.00, 60);

        calculator.updateBalance(List.of(deposit));

        assertBalance(deposit, 1200.00);
    }

    @Test
    void repeatedMonthlyUpdatesUseThePreviouslyUpdatedBalance() {
        TimeDeposit deposit = new TimeDeposit(1, "premium", 4800.00, 46);
        List<TimeDeposit> deposits = List.of(deposit);

        calculator.updateBalance(deposits);
        assertBalance(deposit, 4820.00);

        calculator.updateBalance(deposits);
        assertBalance(deposit, 4840.08);
    }

    @Test
    void updatesTheOriginalDepositWithoutChangingItsIdPlanOrDays() {
        TimeDeposit deposit = new TimeDeposit(42, "student", 1200.00, 364);
        List<TimeDeposit> deposits = new ArrayList<>(List.of(deposit));

        calculator.updateBalance(deposits);

        assertThat(deposits).hasSize(1);
        assertThat(deposits.get(0)).isSameAs(deposit);
        assertBalance(deposit, 1203.00);
        assertThat(deposit.getId()).isEqualTo(42);
        assertThat(deposit.getPlanType()).isEqualTo("student");
        assertThat(deposit.getDays()).isEqualTo(364);
    }

    @Test
    void roundsInterestBeforeAddingItToTheFractionalBalance() {
        TimeDeposit deposit = new TimeDeposit(1, "premium", 100.123, 46);

        calculator.updateBalance(List.of(deposit));

        assertBalance(deposit, 100.543);
    }

    @ParameterizedTest(name = "Student balance {0} becomes {1} at a half-cent tie")
    @CsvSource({"2.00, 2.01", "6.00, 6.01"})
    void preservesLegacyBinaryFloatingPointRoundingAtHalfCentTies(double balance, double expectedBalance) {
        TimeDeposit deposit = new TimeDeposit(1, "student", balance, 31);

        calculator.updateBalance(List.of(deposit));

        // Legacy double arithmetic puts 0.005 just above its decimal tie and 0.015 just below it.
        // Exact decimal HALF_UP would produce 6.02 for the second case.
        assertBalance(deposit, expectedBalance);
    }

    private static void assertBalance(TimeDeposit deposit, double expectedBalance) {
        assertThat(deposit.getBalance())
            .as("balance for %s deposit %d at day %d", deposit.getPlanType(), deposit.getId(), deposit.getDays())
            .isCloseTo(expectedBalance, within(1e-9));
    }
}
