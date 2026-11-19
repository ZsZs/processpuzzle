package com.processpuzzle.starter.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataSpaceRepository extends JpaRepository<DataSpace, String> {

    Optional<DataSpace> findByStarterId(String starterId);

    /** The organizations that installed the starter whose common is {@code commonKey}. */
    @Query("select i.orgKey from InstalledStarter i where i.starterId = "
        + "(select d.starterId from DataSpace d where d.commonKey = :commonKey) order by i.orgKey")
    java.util.List<String> findMemberKeysOf(@Param("commonKey") String commonKey);

    /** The commons of the starters {@code orgKey} installed that have one, by key. */
    @Query("select d.commonKey from DataSpace d where d.starterId in "
        + "(select i.starterId from InstalledStarter i where i.orgKey = :orgKey) order by d.commonKey")
    java.util.List<String> findCommonKeysOf(@Param("orgKey") String orgKey);
}
