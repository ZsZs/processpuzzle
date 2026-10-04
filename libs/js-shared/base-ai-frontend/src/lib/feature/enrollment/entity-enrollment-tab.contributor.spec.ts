import { TestBed } from '@angular/core/testing';
import { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RecognitionProfile } from '../../domain/profile/recognition-profile';
import { ProfiledEntityRegistry } from '../../domain/profile/profiled-entity.registry';
import { ENTITY_ENROLLMENT_TAB } from './entity-enrollment-tab';
import { EntityEnrollmentTabContributor } from './entity-enrollment-tab.contributor';

describe('EntityEnrollmentTabContributor', () => {
  const profileFor = vi.fn();
  let contributor: EntityEnrollmentTabContributor;

  beforeEach(() => {
    profileFor.mockReset();
    TestBed.configureTestingModule({ providers: [{ provide: ProfiledEntityRegistry, useValue: { profileFor } }] });
    contributor = TestBed.inject(EntityEnrollmentTabContributor);
  });

  it('offers the Enrollment tab to an entity type that has a recognition profile', async () => {
    profileFor.mockResolvedValue(new RecognitionProfile({ entityName: 'boat' }));
    expect(await contributor.tabsFor(new BaseEntityDescriptor({ entityName: 'Boat', attrDescriptors: [] }))).toEqual([ENTITY_ENROLLMENT_TAB]);
    expect(profileFor).toHaveBeenCalledWith('Boat');
  });

  it('offers nothing to any other entity', async () => {
    profileFor.mockResolvedValue(undefined);
    expect(await contributor.tabsFor(new BaseEntityDescriptor({ entityName: 'Order', attrDescriptors: [] }))).toEqual([]);
  });
});
