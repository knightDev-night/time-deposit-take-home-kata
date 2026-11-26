package org.ikigaidigital.domain;

import java.math.BigDecimal;

/**
 * Immutable snapshot of a persisted deposit, shared by the repository and application service.
 * A decimal principal preserves the value read from the database. The mutable legacy deposit
 * is used separately when calculating interest.
 *
 * @param id deposit primary key
 * @param planType case-sensitive plan key
 * @param days stored elapsed day count used for eligibility
 * @param balance current principal, which may include fractional cents
 */
public record StoredDeposit(int id, String planType, int days, BigDecimal balance) {
}
