package com.processpuzzle.baseentity.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.List;
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
    void writeSelf_isReservedAndRefused() {
        when(directory.isCommon(COMMON)).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(COMMON, type(COMMON, "asset", SharingWrite.SELF, SharingVisibility.PUBLIC)))
            .satisfies(e -> refusedWith(e, "reserved"));
    }

    @Test
    void ownerWrittenAndOwnerOnlyTypes_areAccepted() {
        when(directory.isCommon(COMMON)).thenReturn(true);

        validator.validate(COMMON, type(COMMON, "club", SharingWrite.OWNER_ORG, SharingVisibility.PUBLIC));
        validator.validate(COMMON, type(COMMON, "sailor", SharingWrite.OWNER_ORG, SharingVisibility.OWNER_ONLY));
        validator.validate(COMMON, type(COMMON, "class", SharingWrite.COMMON_ADMIN, SharingVisibility.OWNER_ONLY));
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

    @Test
    void visibleWhen_limitsAPublicType() {
        when(directory.isCommon(COMMON)).thenReturn(true);
        BaseEntityDefinition race = type(COMMON, "race", SharingWrite.OWNER_ORG, SharingVisibility.PUBLIC);
        race.setSharingVisibleWhen(List.of("PUBLISHED"));

        validator.validate(COMMON, race);
    }

    @Test
    void visibleWhen_onAnOwnerOnlyTypeOrWithABlankState_isRefused() {
        when(directory.isCommon(COMMON)).thenReturn(true);
        BaseEntityDefinition ownerOnly = type(COMMON, "sailor", SharingWrite.OWNER_ORG, SharingVisibility.OWNER_ONLY);
        ownerOnly.setSharingVisibleWhen(List.of("PUBLISHED"));
        BaseEntityDefinition blank = type(COMMON, "race", SharingWrite.OWNER_ORG, SharingVisibility.PUBLIC);
        blank.setSharingVisibleWhen(List.of(" "));

        assertThatThrownBy(() -> validator.validate(COMMON, ownerOnly)).satisfies(e -> refusedWith(e, "visibleWhen"));
        assertThatThrownBy(() -> validator.validate(COMMON, blank)).satisfies(e -> refusedWith(e, "blank"));
    }

    private static BaseEntityDefinition entry(String from, ValueKind kind, String linkedType) {
        BaseEntityDefinition entry = type(COMMON, "entry", SharingWrite.OWNER_ORG, SharingVisibility.PUBLIC);
        entry.addAttribute(BaseEntityAttribute.builder().code("race").name("Race").valueKind(kind)
            .formControlType(FormControlType.LOOKUP).linkedEntityType(linkedType).build());
        entry.setSharingFrom(from);
        return entry;
    }

    @Test
    void from_namingAReferenceToASharedType_isAccepted() {
        when(directory.isCommon(COMMON)).thenReturn(true);
        when(repository.findByOrgKeyAndCodeAndChannel(COMMON, "race", Channel.DRAFT))
            .thenReturn(Optional.of(type(COMMON, "race", SharingWrite.OWNER_ORG, SharingVisibility.PUBLIC)));

        validator.validate(COMMON, entry("race", ValueKind.REFERENCE, "race"));
    }

    @Test
    void from_isAcceptedBeforeItsTargetIsDefined() {
        when(directory.isCommon(COMMON)).thenReturn(true);

        validator.validate(COMMON, entry("race", ValueKind.REFERENCE, "race"));
    }

    @Test
    void from_mustNameASingleReferenceAttribute() {
        when(directory.isCommon(COMMON)).thenReturn(true);
        BaseEntityDefinition notReference = entry("race", ValueKind.TEXT, null);
        BaseEntityDefinition unknown = entry("ghost", ValueKind.REFERENCE, "race");

        assertThatThrownBy(() -> validator.validate(COMMON, notReference)).satisfies(e -> refusedWith(e, "REFERENCE"));
        assertThatThrownBy(() -> validator.validate(COMMON, unknown)).satisfies(e -> refusedWith(e, "not an attribute"));
    }

    @Test
    void from_cannotFollowAChainOrATypeThatIsNotShared() {
        when(directory.isCommon(COMMON)).thenReturn(true);
        BaseEntityDefinition chained = type(COMMON, "race", SharingWrite.OWNER_ORG, SharingVisibility.PUBLIC);
        chained.setSharingFrom("event");
        when(repository.findByOrgKeyAndCodeAndChannel(COMMON, "race", Channel.DRAFT)).thenReturn(Optional.of(chained));
        BaseEntityDefinition entry = entry("race", ValueKind.REFERENCE, "race");

        assertThatThrownBy(() -> validator.validate(COMMON, entry)).satisfies(e -> refusedWith(e, "one level"));

        when(repository.findByOrgKeyAndCodeAndChannel(COMMON, "race", Channel.DRAFT))
            .thenReturn(Optional.of(BaseEntityDefinition.builder().orgKey(COMMON).code("race").name("Race").build()));
        assertThatThrownBy(() -> validator.validate(COMMON, entry)).satisfies(e -> refusedWith(e, "one level"));
    }

    @Test
    void from_takesItsPolicyFromTheReferencedRecord() {
        when(directory.isCommon(COMMON)).thenReturn(true);
        BaseEntityDefinition ownerOnly = entry("race", ValueKind.REFERENCE, "race");
        ownerOnly.setSharingVisibility(SharingVisibility.OWNER_ONLY);

        assertThatThrownBy(() -> validator.validate(COMMON, ownerOnly)).satisfies(e -> refusedWith(e, "takes its owner and visibility"));
    }
}
