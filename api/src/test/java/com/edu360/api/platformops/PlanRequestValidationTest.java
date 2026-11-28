package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.edu360.api.platformops.dto.CreatePlanRequest;
import com.edu360.api.platformops.dto.UpdatePlanRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class PlanRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void createPlanAcceptsOnlySupportedBillingCycles() {
        for (Integer months : Set.of(1, 3, 6, 12)) {
            assertThat(validator.validate(createRequest(months, new BigDecimal("0.10"), new BigDecimal("4.50"))))
                    .as("billing cycle %s should be accepted", months)
                    .isEmpty();
        }
    }

    @Test
    void createPlanRejectsUnsupportedBillingCyclesAndNegativeUnitAmounts() {
        for (Integer months : Set.of(0, 2, 4, 13)) {
            assertThat(validator.validate(createRequest(months, BigDecimal.ZERO, BigDecimal.ZERO)))
                    .as("billing cycle %s should be rejected", months)
                    .isNotEmpty();
        }
        assertThat(validator.validate(createRequest(12, new BigDecimal("-0.01"), BigDecimal.ZERO))).isNotEmpty();
        assertThat(validator.validate(createRequest(12, BigDecimal.ZERO, new BigDecimal("-0.01")))).isNotEmpty();
    }

    @Test
    void updatePlanAcceptsOnlySupportedBillingCycles() {
        for (Integer months : Set.of(1, 3, 6, 12)) {
            assertThat(validator.validate(updateRequest(months, BigDecimal.ZERO, BigDecimal.ZERO)))
                    .as("billing cycle %s should be accepted", months)
                    .isEmpty();
        }
    }

    @Test
    void updatePlanRejectsUnsupportedBillingCyclesAndNegativeUnitAmounts() {
        for (Integer months : Set.of(0, 2, 4, 13)) {
            assertThat(validator.validate(updateRequest(months, BigDecimal.ZERO, BigDecimal.ZERO)))
                    .as("billing cycle %s should be rejected", months)
                    .isNotEmpty();
        }
        assertThat(validator.validate(updateRequest(12, new BigDecimal("-0.01"), BigDecimal.ZERO))).isNotEmpty();
        assertThat(validator.validate(updateRequest(12, BigDecimal.ZERO, new BigDecimal("-0.01")))).isNotEmpty();
    }

    private CreatePlanRequest createRequest(Integer billingCycleMonths, BigDecimal smsUnitAmount,
            BigDecimal storageUnitAmountPerGb) {
        return new CreatePlanRequest(
                "Validation Plan", "PER_STUDENT_YEAR", BigDecimal.ZERO, "INR",
                100, 10, 100, 100, 1,
                false, false, billingCycleMonths, smsUnitAmount, storageUnitAmountPerGb
        );
    }

    private UpdatePlanRequest updateRequest(Integer billingCycleMonths, BigDecimal smsUnitAmount,
            BigDecimal storageUnitAmountPerGb) {
        return new UpdatePlanRequest(
                null, null, null, null, null, null, null, null, null,
                false, false, billingCycleMonths, smsUnitAmount, storageUnitAmountPerGb, null
        );
    }
}
