package com.edu360.api.platformops.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.edu360.api.platformops.subscription.CommercialCatalogItem;

public record CommercialCatalogItemResponse(
        UUID catalogItemId,
        String itemCode,
        String itemName,
        String category,
        String unitCode,
        String pricingBasis,
        BigDecimal unitAmount,
        Integer unitsPerQuantity,
        Integer billingIntervalMonths,
        String moduleCode,
        String description,
        boolean available,
        Integer rowVersion
) {
    public static CommercialCatalogItemResponse from(CommercialCatalogItem item) {
        return new CommercialCatalogItemResponse(item.getCatalogItemId(), item.getItemCode(), item.getItemName(),
                item.getCategory(), item.getUnitCode(), item.getPricingBasis(), item.getUnitAmount(),
                item.getUnitsPerQuantity(), item.getBillingIntervalMonths(), item.getModuleCode(),
                item.getDescription(), item.isAvailable(), item.getRowVersion());
    }
}
