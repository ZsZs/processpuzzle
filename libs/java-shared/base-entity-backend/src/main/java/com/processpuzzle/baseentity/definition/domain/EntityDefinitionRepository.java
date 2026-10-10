package com.processpuzzle.baseentity.definition.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntityDefinitionRepository
    extends JpaRepository<BaseEntityDefinition, UUID>, JpaSpecificationExecutor<BaseEntityDefinition> {

    Optional<BaseEntityDefinition> findByOrgKeyAndCode(String orgKey, String code);

    boolean existsByOrgKeyAndCode(String orgKey, String code);

    List<BaseEntityDefinition> findAllByOrgKey(String orgKey);

    /**
     * Which attributes of which of a tenant's definitions use {@code controlType}, as codes alone —
     * erasing a tenant needs its ARTIFACT attributes to find the files its objects refer to, and
     * nothing else of the definitions.
     */
    @Query("select d.code as definitionCode, a.code as attributeCode from BaseEntityAttribute a "
        + "join a.entityDefinition d where d.orgKey = :orgKey and a.formControlType = :controlType")
    List<AttributeCodes> findAttributeCodesByOrgKeyAndFormControlType(
        @Param("orgKey") String orgKey, @Param("controlType") FormControlType controlType);

    /**
     * The attributes of every definition of a tenant, which have to go before the definitions: bulk
     * deletes do not cascade, and the attribute table has no {@code org_key} of its own, hence the
     * subselect on the owner.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from BaseEntityAttribute a where a.entityDefinition.id in "
        + "(select d.id from BaseEntityDefinition d where d.orgKey = :orgKey)")
    int eraseAttributesByOrgKey(@Param("orgKey") String orgKey);

    /**
     * Every definition of a tenant in one statement, for erasing the tenant. Run
     * {@link #eraseAttributesByOrgKey} first.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from BaseEntityDefinition d where d.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);

    /** An attribute's code with the code of the definition it belongs to. */
    interface AttributeCodes {

        String getDefinitionCode();

        String getAttributeCode();
    }
}
