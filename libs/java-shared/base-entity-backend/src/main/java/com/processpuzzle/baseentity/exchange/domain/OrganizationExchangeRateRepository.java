package com.processpuzzle.baseentity.exchange.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The exchange rates organizations entered, always read and written within one organization. */
public interface OrganizationExchangeRateRepository extends JpaRepository<OrganizationExchangeRate, UUID> {

    Optional<OrganizationExchangeRate> findByIdAndOrgKey(UUID id, String orgKey);

    Optional<OrganizationExchangeRate> findByOrgKeyAndFromCurrencyAndToCurrencyAndValidFrom(String orgKey, String fromCurrency, String toCurrency, LocalDate validFrom);

    List<OrganizationExchangeRate> findAllByOrgKey(String orgKey);

    Page<OrganizationExchangeRate> findAllByOrgKey(String orgKey, Pageable pageable);

    Page<OrganizationExchangeRate> findAllByOrgKeyAndFromCurrency(String orgKey, String fromCurrency, Pageable pageable);

    Page<OrganizationExchangeRate> findAllByOrgKeyAndToCurrency(String orgKey, String toCurrency, Pageable pageable);

    Page<OrganizationExchangeRate> findAllByOrgKeyAndFromCurrencyAndToCurrency(String orgKey, String fromCurrency, String toCurrency, Pageable pageable);

    /** Every rate of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from OrganizationExchangeRate r where r.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
