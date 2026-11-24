package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.core.definition.Channel;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brings the measure attributes of an already-seeded default definition up to its seed.
 *
 * <p>{@link DefaultEntityLoader} creates a seeded definition only when it is missing and never updates one, so a
 * definition seeded before one of its attributes became a measure keeps the plain control it was seeded with. Such an
 * attribute is not recognised as a QUANTITY, MONEY or DIMENSIONS value: nothing derives its {@code baseAmount}, and a
 * calculation that reads it gets null. This copies the measure settings of the seed onto the draft and the release, and only
 * where the stored attribute is still a plain NUMBER, TEXT_BOX or CALCULATED control. An attribute already of any other
 * control is left as its owner set it.
 */
@Component
@Transactional
public class SeededMeasureUpgrader {

    private static final Set<FormControlType> MEASURES = EnumSet.of(FormControlType.QUANTITY, FormControlType.MONEY,
            FormControlType.DIMENSIONS);
    private static final Set<FormControlType> PLAIN = EnumSet.of(FormControlType.NUMBER, FormControlType.TEXT_BOX,
            FormControlType.CALCULATED);

    private final EntityDefinitionRepository definitionRepository;

    public SeededMeasureUpgrader(EntityDefinitionRepository definitionRepository) {
        this.definitionRepository = definitionRepository;
    }

    /**
     * @param orgKey the organization the definition was seeded into
     * @param seed   the definition as the seed file declares it
     * @return how many stored attributes were brought up to the seed, on the draft and the release together
     */
    public int upgrade(String orgKey, BaseEntityDefinition seed) {
        int upgraded = 0;
        for (Channel channel : List.of(Channel.DRAFT, Channel.RELEASED)) {
            Optional<BaseEntityDefinition> stored = definitionRepository.findByOrgKeyAndCodeAndChannel(orgKey, seed.getCode(), channel);
            if (stored.isEmpty()) {
                continue;
            }
            int changed = upgradeAttributes(stored.get(), seed);
            if (changed > 0) {
                definitionRepository.save(stored.get());
                upgraded += changed;
            }
        }
        return upgraded;
    }

    private static int upgradeAttributes(BaseEntityDefinition stored, BaseEntityDefinition seed) {
        int changed = 0;
        for (BaseEntityAttribute target : seed.getAttributes()) {
            if (!MEASURES.contains(target.getFormControlType())) {
                continue;
            }
            for (BaseEntityAttribute current : stored.getAttributes()) {
                if (target.getCode().equals(current.getCode()) && PLAIN.contains(current.getFormControlType())) {
                    copyMeasure(target, current);
                    changed++;
                }
            }
        }
        return changed;
    }

    private static void copyMeasure(BaseEntityAttribute from, BaseEntityAttribute to) {
        to.setValueKind(from.getValueKind());
        to.setFormControlType(from.getFormControlType());
        to.setQuantityKind(from.getQuantityKind());
        to.setAllowedUnits(from.getAllowedUnits() == null ? null : List.copyOf(from.getAllowedUnits()));
        to.setDefaultUnit(from.getDefaultUnit());
        to.setScale(from.getScale());
        to.setAllowedCurrencies(from.getAllowedCurrencies() == null ? null : List.copyOf(from.getAllowedCurrencies()));
        to.setDefaultCurrency(from.getDefaultCurrency());
        to.setDimensionCount(from.getDimensionCount());
    }
}
