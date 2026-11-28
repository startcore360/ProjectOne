package com.edu360.api.platformops.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialCatalogItemRepository extends JpaRepository<CommercialCatalogItem, UUID> {
    List<CommercialCatalogItem> findByOrderByItemNameAsc();
    List<CommercialCatalogItem> findByAvailableTrueOrderByItemNameAsc();
    Optional<CommercialCatalogItem> findByItemCode(String itemCode);
}
