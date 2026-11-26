package org.ikigaidigital;

/**
 * Mutable deposit model shared with legacy calculator callers. The public API exposes
 * the balance as a {@link Double} and lets {@link TimeDepositCalculator} update it in place.
 * The database-backed application keeps its decimal principal in a separate stored model.
 */
public class TimeDeposit {
    private int id;
    private String planType;
    private Double balance;
    private int days;

    /**
     * Creates a legacy deposit from the supplied values without normalization or validation.
     *
     * @param id deposit identifier
     * @param planType case-sensitive plan key
     * @param balance current principal in the legacy floating-point representation
     * @param days elapsed deposit days used to determine eligibility
     */
    public TimeDeposit(int id, String planType, Double balance, int days) {
        this.id = id;
        this.planType = planType;
        this.balance = balance;
        this.days = days;
    }

    /** {@return the identifier supplied by the caller} */
    public int getId() { return id; }

    /** {@return the original case-sensitive plan key} */
    public String getPlanType() {
        return planType;
    }

    /** {@return the current principal, including any interest applied in place} */
    public Double getBalance() {
        return balance;
    }

    /** {@return the elapsed day count supplied at construction} */
    public int getDays() {
        return days;
    }

    /**
     * Replaces the principal on this object without rounding.
     *
     * @param balance replacement principal in the legacy floating-point representation
     */
    public void setBalance(Double balance) {
        this.balance = balance;
    }
}
