import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor } from '@processpuzzle/base-entity';
import de from '../assets/i18n/base_ai/de.json';
import en from '../assets/i18n/base_ai/en.json';
import es from '../assets/i18n/base_ai/es.json';
import fr from '../assets/i18n/base_ai/fr.json';
import hu from '../assets/i18n/base_ai/hu.json';
import { BASE_AI_TRANSLOCO_SCOPE, ENTITY_ENROLLMENT_I18N_KEY, ENTITY_ENROLLMENT_I18N_SCOPE, RECOGNITION_CAMERA_I18N_SCOPE, RECOGNITION_PROFILE_I18N_SCOPE } from './base-ai.i18n';
import { createRecognitionProfileDescriptor } from './domain/profile/recognition-profile.descriptors';

function keysOf(bundle: object, prefix = ''): string[] {
  return Object.entries(bundle).flatMap(([key, value]) => (value && typeof value === 'object' ? keysOf(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

function attrNames(descriptors: AbstractAttrDescriptor[]): string[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? attrNames(descriptor.attrDescriptors) : descriptor instanceof BaseEntityAttrDescriptor ? [descriptor.attrName] : []));
}

describe('base-ai translations', () => {
  const english = keysOf(en).sort();

  it.each([
    ['de', de],
    ['es', es],
    ['fr', fr],
    ['hu', hu],
  ])('%s has exactly the keys English has', (_, bundle) => {
    expect(keysOf(bundle).sort()).toEqual(english);
  });

  it('labels every attribute of the Recognition Profile form', () => {
    const relative = RECOGNITION_PROFILE_I18N_SCOPE.replace(`${BASE_AI_TRANSLOCO_SCOPE}.`, '');
    const names = attrNames(createRecognitionProfileDescriptor().attrDescriptors);

    expect(names.length).toBeGreaterThan(5);
    for (const name of names) expect(english).toContain(`${relative}.${name}`);
    expect(english).toContain(`${relative}._self`);
  });

  it('has the Enrollment tab label the contributor names', () => {
    expect(english).toContain(ENTITY_ENROLLMENT_I18N_KEY.replace(`${BASE_AI_TRANSLOCO_SCOPE}.`, ''));
  });

  it('has every key the Enrollment tab template uses', () => {
    const relative = ENTITY_ENROLLMENT_I18N_SCOPE.replace(`${BASE_AI_TRANSLOCO_SCOPE}.`, '');
    const keys = ['loading', 'noProfile', 'registered', 'noIdentifier', 'intro', 'cancel', 'empty', 'photoAlt', 'observed', 'mismatch', 'noReading', 'synchronize', 'rebuild', 'confirmRebuild'];
    const statuses = ['NOT_ENROLLED', 'PROCESSING', 'READY', 'FAILED'].map((status) => `status.${status}`);
    const photoStatuses = ['PENDING', 'ENROLLED', 'NO_SUBJECT', 'AMBIGUOUS', 'FAILED'].map((status) => `photoStatus.${status}`);
    for (const key of [...keys, ...statuses, ...photoStatuses]) expect(english).toContain(`${relative}.${key}`);
    expect(en.entity_enrollment.intro).toContain('{{ attribute }}');
    expect(en.entity_enrollment.intro).toContain('{{ detectorClass }}');
  });

  it('has every key the recognition camera template uses', () => {
    const relative = RECOGNITION_CAMERA_I18N_SCOPE.replace(`${BASE_AI_TRANSLOCO_SCOPE}.`, '');
    // capturing / recognizing are the busy shot states, translated as `scope + '.' + shot()`.
    const keys = ['noCamera', 'takePhoto', 'shoot', 'noCandidates', 'capturing', 'recognizing', 'cropAlt', 'noSubject', 'automatic', 'chosen', 'overrule', 'whoIsIt', 'read'];
    for (const key of keys) expect(english).toContain(`${relative}.${key}`);
  });
});
