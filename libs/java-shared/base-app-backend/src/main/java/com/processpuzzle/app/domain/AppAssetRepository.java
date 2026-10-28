package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppAssetRepository extends JpaRepository<AppAsset, AppAssetKey> {

    List<AppAsset> findByOrgKeyAndAppIdAndChannelOrderByPath(String orgKey, String appId, Channel channel);

    Optional<AppAsset> findByOrgKeyAndAppIdAndChannelAndPath(String orgKey, String appId, Channel channel, String path);

    List<AppAsset> findByOrgKeyAndChannel(String orgKey, Channel channel);

    /** Every row of an app, in both channels, for deleting the app. */
    List<AppAsset> findByOrgKeyAndAppId(String orgKey, String appId);

    long countByOrgKeyAndAppIdAndChannel(String orgKey, String appId, Channel channel);

    /** Whether any row of the organization, in any app or channel, still names this content. */
    boolean existsByOrgKeyAndSha256(String orgKey, String sha256);

    /** Every row of a tenant, in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from AppAsset a where a.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
