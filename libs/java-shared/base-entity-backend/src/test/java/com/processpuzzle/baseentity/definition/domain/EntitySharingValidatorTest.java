package com.processpuzzle.baseentity.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class EntitySharingValidatorTest {

    private static final String COMMON = "starter-common";
    private static final String MEMBER = "club";

    @Mock
    private EntityDefinitionRepository repository;
    @Mock
    private ObjectProvider<DataSpaceDirectory> provider;
    @Mock
    private DataSpaceDirectory directory;

    private EntitySharingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EntitySharingValidator(repository, provider);
        when(provider.getIfAvailable()).thenReturn(directory);
    }

    private static BaseEntityDefinition type(String orgKey, String code, SharingWrite write, SharingVisibility visibility) {
        return BaseEntityDefinition.builder().orgKey(orgKey).code(code).name(code)
            .sharingWrite(write).sharingVisibility(visibility).build();
    }

    private static void refusedWith(Throwable thrown, String text) {
        assertThat(thrown).isInstanceOf(ValidationException.class);
        assertThat(((ValidationException) thrown).getViolations()).extracting(Violation::message).anyMatch(m -> m.contains(text));
    }

    @Test
    void sharedTypeOfACommon_isAccepted() {
        when(directory.isCommon(COMMON)).thenReturn(true);

        assertThatCode(() -> validator.validate(COMMON, type(COMMON, "asset", SharingWrite.COMMON_ADMIN, SharingVisibility.PUBLIC)))
            .doesNotThrowAnyException();
    }

    @Test
    void sharedTypeOutsideACommon_isRefused() {
        when(directory.isCommon(MEMBER)).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(MEMBER, type(MEMBER, "asset", SharingWrite.COMMON_ADMIN, SharingVisibility.PUBLIC)))
            .satisfies(e -> refusedWith(e, "not a common"));
    }

    @Test
    void sharedTypeWithoutADirectory_isRefused() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> validator.validate(COMMON, type(COMMON, "asset", SharingWrite.COMMON_ADMIN, SharingVisibility.PUBLIC)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void sharingNeedsBothHalves() {
        assertThatThrownBy(() -> validator.validate(COMMON, type(COMMON, "asset", SharingWrite.COMMON_ADMIN, null)))
            .satisfies(e -> refusedWith(e, "both"));
    }

    @Test
    void embeddedSharedType_isRefused() {
        when(directory.isCommon(COMMON)).thenReturn(true);
        BaseEntityDefinition embedded = type(COMMON, "line", SharingWrite.COMMON_ADMIN, SharingVisibility.PUBLIC);
        embedded.setEmbedded(true);

        assertThatThrownBy(() -> validator.validate(COMMON, embedded))
            .satisfies(e -> refusedWith(e, "embedded"));
    }

    @Test
    void policiesNotSupportedYet_areRefused() {
        when(directory.isCommon(COMMON)).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(COMMON, type(COMMON, "asset", SharingWrite.OWNER_ORG, SharingVisibility.PUBLIC)))
            .satisfies(e -> refusedWith(e, "supported"));
        assertThatThrownBy(() -> validator.validate(COMMON, type(COMMON, "asset", SharingWrite.COMMON_ADMIN, SharingVisibility.OWNER_ONLY)))
            .satisfies(e -> refusedWith(e, "supported"));
    }

    @Test
    void memberType_underASharedCode_isRefused() {
        when(directory.commonOf(MEMBER)).thenReturn(Optional.of(COMMON));
        when(repository.findByOrgKeyAndCodeAndChannel(COMMON, "asset", Channel.RELEASED))
            .thenReturn(Optional.of(type(COMMON, "asset", SharingWrite.COMMON_ADMIN, SharingVisibility.PUBLIC)));

        assertThatThrownBy(() -> validator.validate(MEMBER, type(MEMBER, "asset", null, null)))
            .satisfies(e -> refusedWith(e, "shared by"));
    }

    @Test
    void memberType_underAnotherCode_isAccepted() {
        when(directory.commonOf(MEMBER)).thenReturn(Optional.of(COMMON));
        when(repository.findByOrgKeyAndCodeAndChannel(COMMON, "profile", Channel.RELEASED)).thenReturn(Optional.empty());
        when(repository.findByOrgKeyAndCodeAndChannel(COMMON, "profile", Channel.DRAFT)).thenReturn(Optional.empty());

        assertThatCode(() -> validator.validate(MEMBER, type(MEMBER, "profile", null, null))).doesNotThrowAnyException();
    }

    @Test
    void organizationWithoutACommon_isUnrestricted() {
        when(directory.commonOf(MEMBER)).thenReturn(Optional.empty());

        assertThatCode(() -> validator.validate(MEMBER, type(MEMBER, "asset", null, null))).doesNotThrowAnyException();
    }
}
