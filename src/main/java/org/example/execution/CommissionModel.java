package org.example.execution;

import java.math.BigDecimal;

public interface CommissionModel {

    BigDecimal calculate(int quantity, BigDecimal price);
}