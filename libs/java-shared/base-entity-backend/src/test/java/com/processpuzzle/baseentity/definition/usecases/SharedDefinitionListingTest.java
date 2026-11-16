package com.processpuzzle.baseentity.definition.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.baseentity.common.JpaAuditingConfig;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.common.SecurityAuditorAware;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.SharingVisibility;
import com.processpuzzle.baseentity.definition.domain.SharingWrite;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindAllEntityDefinitionsUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindEntityDefinitionByCodeUseCase;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/** A member lists and reads the types its common shares, as released, next to its own — over a real repository. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:shared-listing;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.baseentity.definition.domain")
@EnableJpaRepositories("com.processpuzzle.baseentity.definition.domain")
@Import(JpaAuditingConfig.class)
class SharedDefinitionListingTest {

    private static final String COMMON = "starter-common";
    private static final String MEMBER = "club";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
        @Bean
        SecurityAuditorAware securityAuditorAware() {
            return new SecurityAuditorAware();
        }
    }

    @Autowired
    private EntityDefinitionRepository repository;

    private FindAllEntityDefinitionsUseCase findAll;
    private FindEntityDefinitionByCodeUseCase findByCode;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        DataSpaceDirectory directory = new DataSpaceDirectory() {
            @Override
            public Optional<String> commonOf(String orgKey) {
                return MEMBER.equals(orgKey) ? Optional.of(COMMON) : Optional.empty();
            }

            @Override
            public boolean isCommon(String orgKey) {
                return COMMON.equals(orgKey);
            }
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<DataSpaceDirectory> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(directory);
        findAll = new FindAllEntityDefinitionsUseCase(repository, provider);
        findByCode = new FindEntityDefinitionByCodeUseCase(repository, provider);

        save(MEMBER, "profile", Channel.DRAFT, false);
        save(MEMBER, "profile", Channel.RELEASED, false);
        save(COMMON, "asset", Channel.RELEASED, true);
        save(COMMON, "asset", Channel.DRAFT, true);
        save(COMMON, "internal", Channel.RELEASED, false);
    }

    private void save(String orgKey, String code, Channel channel, boolean shared) {
        repository.save(BaseEntityDefinition.builder().orgKey(orgKey).code(code).name(code).channel(channel)
            .sharingWrite(shared ? SharingWrite.COMMON_ADMIN : null)
            .sharingVisibility(shared ? SharingVisibility.PUBLIC : null).build());
    }

    @Test
    void aMemberListsItsDraftsAndTheCommonsReleasedSharedTypes() {
        var page = findAll.findAll(MEMBER, Channel.DRAFT, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(BaseEntityDefinition::getOrgKey, BaseEntityDefinition::getCode, d -> d.getChannel())
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(MEMBER, "profile", Channel.DRAFT),
                org.assertj.core.groups.Tuple.tuple(COMMON, "asset", Channel.RELEASED));
    }

    @Test
    void theReleasedListHasEachSharedTypeOnce() {
        var page = findAll.findAll(MEMBER, Channel.RELEASED, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(BaseEntityDefinition::getCode).containsExactlyInAnyOrder("profile", "asset");
    }

    @Test
    void aCommonListsItsOwnTypesOnly() {
        var page = findAll.findAll(COMMON, Channel.DRAFT, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(BaseEntityDefinition::getCode).containsExactlyInAnyOrder("asset");
    }

    @Test
    void aMemberReadsASharedTypeByCode_butNotACommonsOwnType() {
        assertThat(findByCode.findByCode(MEMBER, "asset", Channel.DRAFT).getOrgKey()).isEqualTo(COMMON);
        assertThatThrownBy(() -> findByCode.findByCode(MEMBER, "internal", Channel.DRAFT)).isInstanceOf(NotFoundException.class);
    }
}
