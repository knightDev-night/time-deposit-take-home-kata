package org.ikigaidigital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Shared domain rules for the legacy calculator and database-backed application service.
 * The immutable table pairs each plan's annual rate with its eligibility predicate. All
 * plans share the formula and rounding, so each plan can be expressed as one table entry.
 */
public final class TimeDepositInterest {
    private static final int INTEREST_FREE_DAYS = 30;
    private static final int MONTHS_PER_YEAR = 12;
    private static final int INTEREST_SCALE = 2;
    private static final BigDecimal NO_INTEREST = BigDecimal.ZERO.setScale(INTEREST_SCALE);

    private static final Map<String, PlanRule> PLANS = Map.of(
            "basic", new PlanRule(0.01, days -> true),
            "student", new PlanRule(0.03, days -> days <= 365),
            "premium", new PlanRule(0.05, days -> days > 45)
    );

    /** Keeps this stateless calculation utility from being instantiated. */
    private TimeDepositInterest() {
    }

    /**
     * Calculates one month's interest without mutating the deposit. All plans start with
     * 30 interest-free days. Basic earns 1% annually from day 31, Student earns 3% annually
     * from day 31 through day 365, and Premium earns 5% annually from day 46. Plan lookup is
     * case-sensitive.
     *
     * <p>For eligible deposits, multiply the balance by the annual rate, then divide by 12,
     * in binary floating point. Convert that exact binary result to {@link BigDecimal} and
     * round it to cents with {@link RoundingMode#HALF_UP}. Preserving the operation order and
     * conversion keeps the legacy behavior near half-cent boundaries.
     *
     * @param deposit source of the current balance, plan key, and elapsed days
     * @return interest rounded to two decimal places, or zero for an unknown or ineligible plan
     * @throws NullPointerException if the deposit is null, the plan key is null after the
     *         waiting period, or the balance is null for an eligible plan
     * @throws NumberFormatException if the calculated interest is non-finite
     */
    public static BigDecimal calculateMonthlyInterest(TimeDeposit deposit) {
        int days = deposit.getDays();
        if (days <= INTEREST_FREE_DAYS) {
            return NO_INTEREST;
        }

        PlanRule plan = PLANS.get(deposit.getPlanType());
        if (plan == null || !plan.eligibleAfterWaitingPeriod().test(days)) {
            return NO_INTEREST;
        }

        double interest = deposit.getBalance() * plan.annualRate() / MONTHS_PER_YEAR;
        // The Student interest on 6.00 falls just below 0.015 in binary floating point.
        // Keep the resulting 0.01 credit for compatibility until the rounding policy changes.
        return new BigDecimal(interest).setScale(INTEREST_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Plan-specific inputs to the common monthly calculation.
     *
     * @param annualRate annual rate as a fraction
     * @param eligibleAfterWaitingPeriod plan-specific check on the elapsed day count
     */
    private record PlanRule(double annualRate, IntPredicate eligibleAfterWaitingPeriod) {
    }
}
