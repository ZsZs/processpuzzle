import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EntityStateSnapshot } from './entity-state-actions';
import { WorkingContextActionsComponent } from './working-context-actions.component';
import { WorkingContextService } from './working-context.service';

const snapshot: EntityStateSnapshot = {
  currentKey: 'PLANNED',
  currentLabel: 'Planned',
  transitions: [
    { key: 'start', label: 'Start race', enabled: true },
    { key: 'cancel', label: 'Cancel race', enabled: false, blockedReason: 'Entries are open' },
  ],
};

describe('WorkingContextActionsComponent', () => {
  const service = { stateOf: vi.fn(), fire: vi.fn(), firing: signal(false), definitionOf: () => ({ name: 'race', entityName: 'Race' }) };

  beforeEach(() => {
    service.stateOf.mockReset().mockReturnValue(snapshot);
    service.fire.mockReset().mockResolvedValue(true);
    service.firing.set(false);
    TestBed.configureTestingModule({ providers: [{ provide: WorkingContextService, useValue: service }] });
  });

  function render(inputs: { context: string; triggers?: string[] }) {
    const fixture = TestBed.createComponent(WorkingContextActionsComponent);
    fixture.componentRef.setInput('context', inputs.context);
    if (inputs.triggers) fixture.componentRef.setInput('triggers', inputs.triggers);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('offers every transition the entity can take, a refused one disabled with its reason', () => {
    const host = render({ context: 'race' });

    const buttons = host.querySelectorAll('button');
    expect(Array.from(buttons).map((button) => button.textContent?.trim())).toEqual(['Start race', 'Cancel race']);
    expect(buttons[1].disabled).toBe(true);
    expect(buttons[1].title).toBe('Entries are open');
  });

  it('offers only the named triggers, in the order named', () => {
    const host = render({ context: 'race', triggers: ['cancel', 'start', 'unknown'] });

    expect(Array.from(host.querySelectorAll('button')).map((button) => button.textContent?.trim())).toEqual(['Cancel race', 'Start race']);
  });

  it('fires the transition of the context when clicked', () => {
    const host = render({ context: 'race' });

    (host.querySelector('[data-testid="race-context-action-start"]') as HTMLButtonElement).click();

    expect(service.fire).toHaveBeenCalledWith('race', 'start');
  });

  it('shows nothing without an active context or a state machine', () => {
    service.stateOf.mockReturnValue(undefined);

    expect(render({ context: 'race' }).querySelectorAll('button')).toHaveLength(0);
  });

  it('disables the buttons while a transition is being fired', () => {
    service.firing.set(true);

    expect((render({ context: 'race' }).querySelector('button') as HTMLButtonElement).disabled).toBe(true);
  });
});
