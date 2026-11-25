package org.ikigaidigital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.IntPredicate;

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

    private TimeDepositInterest() {
    }

    /**
     * Calculates one month's interest on the current balance, rounded to two decimal places.
     * Leaves the deposit untouched.
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

    private record PlanRule(double annualRate, IntPredicate eligibleAfterWaitingPeriod) {
    }
}
