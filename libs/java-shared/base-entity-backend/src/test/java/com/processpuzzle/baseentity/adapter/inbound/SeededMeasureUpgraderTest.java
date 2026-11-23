package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeededMeasureUpgraderTest {

    private static final String ORG = "processpuzzle-testbed";
    private static final String CODE = "order-line";

    private EntityDefinitionRepository repository;
    private SeededMeasureUpgrader upgrader;

    @BeforeEach
    void setUp() {
        repository = mock(EntityDefinitionRepository.class);
        upgrader = new SeededMeasureUpgrader(repository);
    }

    @Test
    void aPlainAttributeTakesTheSeedsMeasureOnTheDraftAndTheRelease() {
        BaseEntityDefinition draft = definition(Channel.DRAFT, plain("quantity"));
        BaseEntityDefinition released = definition(Channel.RELEASED, plain("quantity"));
        when(repository.findByOrgKeyAndCodeAndChannel(ORG, CODE, Channel.DRAFT)).thenReturn(Optional.of(draft));
        when(repository.findByOrgKeyAndCodeAndChannel(ORG, CODE, Channel.RELEASED)).thenReturn(Optional.of(released));

        int upgraded = upgrader.upgrade(ORG, seed(quantity()));

        assertThat(upgraded).isEqualTo(2);
        for (BaseEntityDefinition stored : List.of(draft, released)) {
            BaseEntityAttribute attribute = stored.getAttributes().getFirst();
            assertThat(attribute.getFormControlType()).isEqualTo(FormControlType.QUANTITY);
            assertThat(attribute.getValueKind()).isEqualTo(ValueKind.REFERENCE);
            assertThat(attribute.getQuantityKind()).isEqualTo("COUNT");
            assertThat(attribute.getAllowedUnits()).containsExactly("pc", "dz");
            assertThat(attribute.getDefaultUnit()).isEqualTo("pc");
            assertThat(attribute.getScale()).isZero();
        }
        verify(repository, times(2)).save(any(BaseEntityDefinition.class));
    }

    @Test
    void anAttributeThatIsAlreadyAMeasureIsLeftAsItsOwnerSetIt() {
        BaseEntityAttribute owned = plain("quantity");
        owned.setFormControlType(FormControlType.MONEY);
        owned.setAllowedCurrencies(List.of("USD"));
        BaseEntityDefinition draft = definition(Channel.DRAFT, owned);
        when(repository.findByOrgKeyAndCodeAndChannel(ORG, CODE, Channel.DRAFT)).thenReturn(Optional.of(draft));
        when(repository.findByOrgKeyAndCodeAndChannel(ORG, CODE, Channel.RELEASED)).thenReturn(Optional.empty());

        int upgraded = upgrader.upgrade(ORG, seed(quantity()));

        assertThat(upgraded).isZero();
        assertThat(owned.getFormControlType()).isEqualTo(FormControlType.MONEY);
        assertThat(owned.getAllowedCurrencies()).containsExactly("USD");
        verify(repository, never()).save(any());
    }

    @Test
    void aDefinitionThatIsNotThereIsSkipped() {
        when(repository.findByOrgKeyAndCodeAndChannel(anyString(), anyString(), any())).thenReturn(Optional.empty());

        assertThat(upgrader.upgrade(ORG, seed(quantity()))).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void aCalculatedAttributeTakesTheMoneySeedToo() {
        BaseEntityAttribute total = BaseEntityAttribute.builder()
                .code("total")
                .valueKind(ValueKind.NUMBER)
                .formControlType(FormControlType.CALCULATED)
                .build();
        BaseEntityDefinition draft = definition(Channel.DRAFT, total);
        when(repository.findByOrgKeyAndCodeAndChannel(ORG, CODE, Channel.DRAFT)).thenReturn(Optional.of(draft));
        when(repository.findByOrgKeyAndCodeAndChannel(ORG, CODE, Channel.RELEASED)).thenReturn(Optional.empty());
        BaseEntityAttribute money = BaseEntityAttribute.builder()
                .code("total")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(FormControlType.MONEY)
                .allowedCurrencies(List.of("EUR"))
                .defaultCurrency("EUR")
                .build();

        assertThat(upgrader.upgrade(ORG, seed(money))).isEqualTo(1);
        assertThat(total.getFormControlType()).isEqualTo(FormControlType.MONEY);
        assertThat(total.getDefaultCurrency()).isEqualTo("EUR");
    }

    @Test
    void aSeedWithNoMeasureChangesNothing() {
        BaseEntityDefinition draft = definition(Channel.DRAFT, plain("productName"));
        when(repository.findByOrgKeyAndCodeAndChannel(eq(ORG), eq(CODE), any())).thenReturn(Optional.of(draft));
        BaseEntityAttribute text = plain("productName");
        text.setFormControlType(FormControlType.TEXT_BOX);

        assertThat(upgrader.upgrade(ORG, seed(text))).isZero();
        verify(repository, never()).save(any());
    }

    private static BaseEntityAttribute plain(String code) {
        return BaseEntityAttribute.builder()
                .code(code)
                .valueKind(ValueKind.NUMBER)
                .formControlType(FormControlType.NUMBER)
                .build();
    }

    private static BaseEntityAttribute quantity() {
        return BaseEntityAttribute.builder()
                .code("quantity")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(FormControlType.QUANTITY)
                .quantityKind("COUNT")
                .allowedUnits(List.of("pc", "dz"))
                .defaultUnit("pc")
                .scale(0)
                .build();
    }

    private static BaseEntityDefinition definition(Channel channel, BaseEntityAttribute... attributes) {
        BaseEntityDefinition definition = BaseEntityDefinition.builder().orgKey(ORG).code(CODE).channel(channel).build();
        for (BaseEntityAttribute attribute : attributes) {
            definition.addAttribute(attribute);
        }
        return definition;
    }

    private static BaseEntityDefinition seed(BaseEntityAttribute... attributes) {
        return definition(Channel.DRAFT, attributes);
    }
}
