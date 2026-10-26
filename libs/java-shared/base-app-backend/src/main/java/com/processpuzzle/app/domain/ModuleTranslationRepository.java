package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModuleTranslationRepository extends JpaRepository<ModuleTranslation, ModuleTranslationKey> {

    List<ModuleTranslation> findByOrgKeyAndModuleKeyAndChannel(String orgKey, String moduleKey, Channel channel);

    Optional<ModuleTranslation> findByOrgKeyAndModuleKeyAndChannelAndLocale(String orgKey, String moduleKey,
                                                                            Channel channel, String locale);

    List<ModuleTranslation> findByOrgKeyAndChannel(String orgKey, Channel channel);

    /** A module's rows of one channel — what deleting a draft module, or a release that dropped it, removes. */
    void deleteByOrgKeyAndModuleKeyAndChannel(String orgKey, String moduleKey, Channel channel);

    /** Every row of a tenant, in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ModuleTranslation t where t.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
