package org.ikigaidigital;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Characterizes the public calculator contract, including its in-place updates and legacy arithmetic. */
class TimeDepositCalculatorTest {
    private final TimeDepositCalculator calculator = new TimeDepositCalculator();

    /**
     * Uses a principal of 1,200 so each annual rate yields a distinct whole-unit monthly
     * credit. Days 29 to 31, 44 to 46, and 364 to 366 catch off-by-one errors. Day 400
     * checks that Basic and Premium continue earning after a year.
     *
     * @param planType legacy plan key
     * @param days elapsed days around the relevant eligibility cutoff
     * @param expectedBalance principal plus the expected monthly credit, if eligible
     */
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

    /** Distinct plans, principals, and day counts in one batch catch reuse of another deposit's inputs. */
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

    /** An empty mutable list is accepted as a no-op and stays empty. */
    @Test
    void acceptsAnEmptyDepositList() {
        List<TimeDeposit> deposits = new ArrayList<>();

        calculator.updateBalance(deposits);

        assertThat(deposits).isEmpty();
    }

    /** An eligible Basic deposit with zero principal still earns zero interest. */
    @Test
    void leavesAnEligibleZeroBalanceAtZero() {
        TimeDeposit deposit = new TimeDeposit(1, "basic", 0.00, 31);

        calculator.updateBalance(List.of(deposit));

        assertBalance(deposit, 0.00);
    }

    /**
     * An unknown key and differently cased versions of all known keys leave a mature
     * deposit's balance unchanged, guarding against case normalization or a default rate.
     *
     * @param planType unsupported plan key or capitalization
     */
    @ParameterizedTest(name = "{0} earns no interest")
    @ValueSource(strings = {"unknown", "Basic", "STUDENT", "Premium"})
    void leavesUnknownAndDifferentlyCasedPlansUnchanged(String planType) {
        TimeDeposit deposit = new TimeDeposit(1, planType, 1200.00, 60);

        calculator.updateBalance(List.of(deposit));

        assertBalance(deposit, 1200.00);
    }

    /** A second update uses the credited principal and reaches 4,840.08, exposing a stale-principal calculation. */
    @Test
    void repeatedMonthlyUpdatesUseThePreviouslyUpdatedBalance() {
        TimeDeposit deposit = new TimeDeposit(1, "premium", 4800.00, 46);
        List<TimeDeposit> deposits = List.of(deposit);

        calculator.updateBalance(deposits);
        assertBalance(deposit, 4820.00);

        calculator.updateBalance(deposits);
        assertBalance(deposit, 4840.08);
    }

    /** Existing references see the new balance because the original object and its metadata are retained. */
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

    /** A 0.42 credit on 100.123 produces 100.543, catching rounding of the entire balance. */
    @Test
    void roundsInterestBeforeAddingItToTheFractionalBalance() {
        TimeDeposit deposit = new TimeDeposit(1, "premium", 100.123, 46);

        calculator.updateBalance(List.of(deposit));

        assertBalance(deposit, 100.543);
    }

    /**
     * Student principals of 2.00 and 6.00 put the computed double just above the 0.005 tie
     * and just below the 0.015 tie. Both earn 0.01. Exact decimal half-up rounding would
     * make the second credit 0.02 and break legacy compatibility.
     *
     * @param balance principal that gives a mathematical half-cent tie
     * @param expectedBalance principal plus the legacy binary-derived credit
     */
    @ParameterizedTest(name = "Student balance {0} becomes {1} at a half-cent tie")
    @CsvSource({"2.00, 2.01", "6.00, 6.01"})
    void preservesLegacyBinaryFloatingPointRoundingAtHalfCentTies(double balance, double expectedBalance) {
        TimeDeposit deposit = new TimeDeposit(1, "student", balance, 31);

        calculator.updateBalance(List.of(deposit));

        assertBalance(deposit, expectedBalance);
    }

    /**
     * Checks a legacy double balance with a tolerance far smaller than a cent.
     *
     * @param deposit updated deposit, also used to identify failures
     * @param expectedBalance expected principal after the update
     */
    private static void assertBalance(TimeDeposit deposit, double expectedBalance) {
        assertThat(deposit.getBalance())
            .as("balance for %s deposit %d at day %d", deposit.getPlanType(), deposit.getId(), deposit.getDays())
            .isCloseTo(expectedBalance, within(1e-9));
    }
}
