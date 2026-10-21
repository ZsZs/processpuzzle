import { InjectionToken, Type } from '@angular/core';
import type { BaseEntityDescriptor } from '../../base-entity/base-entity.descriptor';

/**
 * What renders a {@link FormControlType.CALCULATED} attribute, and which attributes of an entity are one.
 *
 * base-entity knows that some attribute *holds* a derived value; it cannot know the calculation behind it —
 * that is base-calculation's, and base-entity does not depend on it. So the feature that owns the calculations
 * registers this, the way base-state registers {@link ENTITY_STATE_CONTROL}, and nothing on either side names
 * the other. Without a registration a CALCULATED attribute is shown read-only as a label.
 */
export interface EntityCalculatedControl {
  /** A `BaseFormControlComponent` subclass. Typed loosely: a stricter type would close an import cycle. */
  component: Type<unknown>;
  /**
   * The names of the attributes of `descriptor`'s objects that a calculation writes — usually none.
   * {@link EntityScreenResolver} asks this once per resolved entity and turns each into a CALCULATED control,
   * so an entity authored before the calculation existed needs no descriptor change.
   */
  calculatedAttributesOf(descriptor: BaseEntityDescriptor): Promise<string[]> | string[];
}

export const ENTITY_CALCULATED_CONTROL = new InjectionToken<EntityCalculatedControl>('ENTITY_CALCULATED_CONTROL');
