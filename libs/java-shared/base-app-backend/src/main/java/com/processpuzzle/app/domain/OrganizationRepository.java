package com.processpuzzle.app.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrganizationRepository
        extends JpaRepository<Organization, String>, JpaSpecificationExecutor<Organization> {
}
