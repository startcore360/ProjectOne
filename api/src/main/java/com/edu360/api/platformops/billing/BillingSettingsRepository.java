package com.edu360.api.platformops.billing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface BillingSettingsRepository extends JpaRepository<BillingSettings,Integer> {}
