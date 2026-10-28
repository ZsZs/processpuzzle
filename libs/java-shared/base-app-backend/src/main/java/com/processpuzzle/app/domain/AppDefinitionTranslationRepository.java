package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppDefinitionTranslationRepository extends JpaRepository<AppDefinitionTranslation, AppDefinitionTranslationKey> {

    List<AppDefinitionTranslation> findByOrgKeyAndAppIdAndChannel(String orgKey, String appId, Channel channel);

    Optional<AppDefinitionTranslation> findByOrgKeyAndAppIdAndChannelAndLocale(String orgKey, String appId,
                                                                               Channel channel, String locale);

    List<AppDefinitionTranslation> findByOrgKeyAndChannel(String orgKey, Channel channel);

    List<AppDefinitionTranslation> findByOrgKeyAndAppId(String orgKey, String appId);

    /** Every row of a tenant, in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from AppDefinitionTranslation t where t.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
