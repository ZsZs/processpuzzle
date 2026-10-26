package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModuleAssetRepository extends JpaRepository<ModuleAsset, ModuleAssetKey> {

    List<ModuleAsset> findByOrgKeyAndModuleKeyAndChannelOrderByPath(String orgKey, String moduleKey, Channel channel);

    Optional<ModuleAsset> findByOrgKeyAndModuleKeyAndChannelAndPath(String orgKey, String moduleKey, Channel channel,
                                                                    String path);

    List<ModuleAsset> findByOrgKeyAndChannel(String orgKey, Channel channel);

    long countByOrgKeyAndModuleKeyAndChannel(String orgKey, String moduleKey, Channel channel);

    /** Whether any row of the organization, in any module or channel, still names this content. */
    boolean existsByOrgKeyAndSha256(String orgKey, String sha256);

    void deleteByOrgKeyAndModuleKeyAndChannel(String orgKey, String moduleKey, Channel channel);

    /** Every row of a tenant, in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ModuleAsset a where a.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
