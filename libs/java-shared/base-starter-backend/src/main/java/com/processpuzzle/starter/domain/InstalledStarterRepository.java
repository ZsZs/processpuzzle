package com.processpuzzle.starter.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstalledStarterRepository extends JpaRepository<InstalledStarter, UUID> {

    Optional<InstalledStarter> findByOrgKeyAndStarterId(String orgKey, String starterId);

    List<InstalledStarter> findByOrgKeyOrderByStarterId(String orgKey);

    void deleteByOrgKey(String orgKey);
}
