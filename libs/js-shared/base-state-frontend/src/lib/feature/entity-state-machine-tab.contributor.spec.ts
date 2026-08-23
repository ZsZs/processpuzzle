import { TestBed } from '@angular/core/testing';
import { BaseEntityAttrDescriptor, BaseEntityDescriptor, ENTITY_TAB_CONTRIBUTORS, FormControlType } from '@processpuzzle/base-entity';
import { describe, expect, it, vi } from 'vitest';
import { GovernedEntityRegistry } from '../domain/governed-entity.registry';
import { ENTITY_STATE_MACHINE_TAB, ENTITY_STATE_MACHINE_TAB_SEGMENT } from './entity-state-machine-tab';
import { EntityStateMachineTabComponent } from './entity-state-machine-tab.component';
import { EntityStateMachineTabContributor, provideEntityStateMachineTab } from './entity-state-machine-tab.contributor';

function descriptorOf(entityName: string): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName, attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true)] });
}

describe('EntityStateMachineTabContributor', () => {
  function setup(governed: string[]) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: GovernedEntityRegistry, useValue: { governs: vi.fn(async (name: string) => governed.includes(name)) } }, ...provideEntityStateMachineTab()],
    });
    return TestBed.inject(EntityStateMachineTabContributor);
  }

  it('offers the State Machine tab for a governed entity', async () => {
    expect(await setup(['Order']).tabsFor(descriptorOf('Order'))).toEqual([ENTITY_STATE_MACHINE_TAB]);
  });

  it('offers nothing for an entity no machine governs — the usual answer', async () => {
    expect(await setup(['Order']).tabsFor(descriptorOf('Order Line'))).toEqual([]);
  });

  it('mounts the read-only screen at a segment shared by the link and the route', () => {
    expect(ENTITY_STATE_MACHINE_TAB.segment).toBe(ENTITY_STATE_MACHINE_TAB_SEGMENT);
    expect(ENTITY_STATE_MACHINE_TAB.component).toBe(EntityStateMachineTabComponent);
    expect(ENTITY_STATE_MACHINE_TAB.i18nKey).toBe('base_state.entity_state_machine.tab');
  });

  /**
   * The token is `multi`, so a second feature contributing its own tab adds to this one rather than
   * replacing it — which is the whole point of registering through the token instead of a plain provider.
   */
  it('registers itself as one of the multi-provided contributors', () => {
    setup([]);
    const contributors = TestBed.inject(ENTITY_TAB_CONTRIBUTORS);

    expect(contributors).toHaveLength(1);
    expect(contributors[0]).toBeInstanceOf(EntityStateMachineTabContributor);
  });
});
