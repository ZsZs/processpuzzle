import { TestBed } from '@angular/core/testing';
import { BaseEntityAttrDescriptor, BaseEntityDescriptor, ENTITY_STATE_CONTROL, FormControlType } from '@processpuzzle/base-entity';
import { describe, expect, it, vi } from 'vitest';
import { GovernedEntityRegistry } from '../../domain/definition/governed-entity.registry';
import { StateMachineDefinitionMapper } from '../../domain/definition/state-machine-definition.mapper';
import { STATE_MACHINE_DEFINITION_DTO } from '../../domain/definition/test-state-machine-definition';
import { EntityStateControlComponent } from './entity-state-control.component';
import { BaseStateEntityStateControl, provideEntityStateControl } from './entity-state-control.provider';

function descriptorOf(entityName: string): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName, attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true)] });
}

describe('BaseStateEntityStateControl', () => {
  const machine = new StateMachineDefinitionMapper().fromDto(STATE_MACHINE_DEFINITION_DTO);

  function setup(machineFor: (entityName: string | undefined) => Promise<unknown>) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: GovernedEntityRegistry, useValue: { machineFor: vi.fn(machineFor) } }, ...provideEntityStateControl()],
    });
    return TestBed.inject(BaseStateEntityStateControl);
  }

  it("names the machine's state attribute for a governed entity", async () => {
    const stateControl = setup(async (name) => (name === 'Order' ? machine : undefined));

    expect(await stateControl.stateAttributeOf(descriptorOf('Order'))).toBe('status');
  });

  it('answers undefined for an entity no machine governs — the usual answer', async () => {
    const stateControl = setup(async () => undefined);

    expect(await stateControl.stateAttributeOf(descriptorOf('Order Line'))).toBeUndefined();
  });

  it('answers undefined for a machine that names no state attribute', async () => {
    const stateControl = setup(async () => ({ ...machine, stateAttributeKey: '' }));

    expect(await stateControl.stateAttributeOf(descriptorOf('Order'))).toBeUndefined();
  });

  it('renders the attribute with EntityStateControlComponent', () => {
    expect(setup(async () => undefined).component).toBe(EntityStateControlComponent);
  });

  it('registers itself as ENTITY_STATE_CONTROL', () => {
    setup(async () => undefined);

    expect(TestBed.inject(ENTITY_STATE_CONTROL)).toBe(TestBed.inject(BaseStateEntityStateControl));
  });
});
