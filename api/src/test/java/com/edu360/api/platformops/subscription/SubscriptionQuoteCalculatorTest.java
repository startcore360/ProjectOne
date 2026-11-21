package com.edu360.api.platformops.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edu360.api.platform.Plan;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteRequest;
import com.edu360.api.platformops.subscription.dto.CustomerQuoteResponse;

class SubscriptionQuoteCalculatorTest {
    private final SubscriptionQuoteCalculator calculator = new SubscriptionQuoteCalculator();

    @Test
    void quoteReturnsLowestCostAndBestFitRecommendationsSeparately() {
        Plan exactFit = plan("Exact fit", "1200.00");
        exactFit.setMaxColleges(2);
        exactFit.setMaxInstitutions(2);
        exactFit.setMaxStudents(100);
        exactFit.setMaxStaff(10);
        Plan lowerCost = plan("Lower cost", "1000.00");
        lowerCost.setMaxColleges(20);
        lowerCost.setMaxInstitutions(20);
        lowerCost.setMaxStudents(10000);
        lowerCost.setMaxStaff(1000);
        CustomerQuoteRequest request = new CustomerQuoteRequest(
                "UNIVERSITY_GROUP", 2, 0, 100, 10, List.of(), 1, false);

        CustomerQuoteResponse response = calculator.quote(request, List.of(exactFit, lowerCost),
                Map.of(), List.of(), Map.of());

        assertThat(response.lowestCostPlanId()).isEqualTo(lowerCost.getPlanId());
        assertThat(response.bestFitPlanId()).isEqualTo(exactFit.getPlanId());
        assertThat(response.plans()).filteredOn(CustomerQuoteResponse.PlanQuote::eligible).hasSize(2);
        assertThat(response.plans()).filteredOn(q -> q.planId().equals(lowerCost.getPlanId()))
                .singleElement().extracting(CustomerQuoteResponse.PlanQuote::recommendation)
                .isEqualTo("LOWEST_COST");
        assertThat(response.plans()).filteredOn(q -> q.planId().equals(exactFit.getPlanId()))
                .singleElement().extracting(CustomerQuoteResponse.PlanQuote::recommendation)
                .isEqualTo("BEST_FIT");
    }

    @Test
    void quoteProratesStudentYearComponentsAndAppliesDiscountToSetupAndPlan() {
        Plan plan = plan("Custom package", "100.00");
        plan.setPricingType("CUSTOM");
        plan.setBillingCycleMonths(6);
        plan.setSetupFeeAmount(new BigDecimal("100.00"));
        plan.setDiscountType("PERCENT");
        plan.setDiscountValue(new BigDecimal("10.00"));
        CommercialCatalogItem item = CommercialCatalogItem.builder()
                .catalogItemId(UUID.randomUUID()).itemCode("STUDENT_SERVICE")
                .itemName("Student service").category("SERVICE").unitCode("SERVICE")
                .pricingBasis("PER_STUDENT_YEAR").unitAmount(new BigDecimal("120.00"))
                .unitsPerQuantity(1).billingIntervalMonths(12).available(true)
                .createdAt(Instant.now()).createdBy(UUID.randomUUID())
                .updatedAt(Instant.now()).updatedBy(UUID.randomUUID()).rowVersion(1).build();
        PlanComponent component = PlanComponent.builder()
                .planComponentId(UUID.randomUUID()).planId(plan.getPlanId())
                .catalogItemId(item.getCatalogItemId()).includedQuantity(BigDecimal.ONE)
                .enabled(true).createdAt(Instant.now()).createdBy(UUID.randomUUID())
                .updatedAt(Instant.now()).updatedBy(UUID.randomUUID()).rowVersion(1).build();
        CustomerQuoteRequest request = new CustomerQuoteRequest(
                "STANDALONE_SCHOOL", 0, 1, 10, 2, List.of(), 6, true);

        CustomerQuoteResponse.PlanQuote quote = calculator.quote(request, List.of(plan),
                Map.of(plan.getPlanId(), List.of(component)), List.of(item), Map.of()).plans().get(0);

        assertThat(quote.lines()).extracting(CustomerQuoteResponse.QuoteLine::amount)
                .containsExactly(new BigDecimal("600.00"), new BigDecimal("100.00"), new BigDecimal("600.00"));
        assertThat(quote.subtotal()).isEqualByComparingTo("1300.00");
        assertThat(quote.discount()).isEqualByComparingTo("130.00");
        assertThat(quote.total()).isEqualByComparingTo("1170.00");
    }

    private static Plan plan(String name, String price) {
        return Plan.builder().planId(UUID.randomUUID()).planName(name).pricingType("FLAT")
                .priceAmount(new BigDecimal(price)).currencyCode("INR").billingCycleMonths(1)
                .pricingEngineVersion("V2").status("ACTIVE").build();
    }
}
