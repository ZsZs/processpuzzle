package com.processpuzzle.platformadmin.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface InvoiceRepository
        extends JpaRepository<Invoice, String>, JpaSpecificationExecutor<Invoice> {

    /** A tenant's invoices, newest period first — the order the billing screen renders them in. */
    List<Invoice> findByOrgKeyOrderByPeriodStartDesc(String orgKey);
}
