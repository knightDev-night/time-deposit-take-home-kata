package org.ikigaidigital.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable withdrawal from a deposit's history. The containing deposit supplies its
 * association, and the withdrawal serves as read data for the listing operation.
 *
 * @param id withdrawal primary key
 * @param amount decimal amount as stored in the database
 * @param date calendar date without a time or time zone
 */
public record Withdrawal(int id, BigDecimal amount, LocalDate date) {
}
