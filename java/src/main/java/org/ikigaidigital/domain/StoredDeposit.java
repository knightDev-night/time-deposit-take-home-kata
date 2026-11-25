package org.ikigaidigital.domain;

import java.math.BigDecimal;

public record StoredDeposit(int id, String planType, int days, BigDecimal balance) {
}
