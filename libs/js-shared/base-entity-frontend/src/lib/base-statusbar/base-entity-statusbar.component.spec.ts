import { InputSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { RuleViolationsSingletonStore } from '../rule-engine/rule-violations.store';
import { setupContainerComponentTest } from '../../test-setup';
import { BaseEntityStatusbarComponent } from './base-entity-statusbar.component';

describe('BaseStatusbarComponent', () => {
  it('should create', async () => {
    const { component } = await setupContainerComponentTest(BaseEntityStatusbarComponent);
    expect(component).toBeTruthy();
  });

  it('remains hidden until an entity is selected or current', async () => {
    const { fixture } = await setupContainerComponentTest(BaseEntityStatusbarComponent);

    expect((fixture.nativeElement as HTMLElement).querySelector('mat-toolbar')).toBeNull();
  });

  it('renders the entity title once an entity becomes current', async () => {
    const { fixture, store } = await setupContainerComponentTest(BaseEntityStatusbarComponent);
    store.setCurrentEntity('1');
    fixture.detectChanges();

    const toolbar = (fixture.nativeElement as HTMLElement).querySelector('mat-toolbar');
    expect(toolbar).not.toBeNull();
    expect(toolbar!.textContent).toContain('Test Entity');
  });

  it('renders severity chips summarising violations for the current entity', async () => {
    const { fixture, component, store } = await setupContainerComponentTest(BaseEntityStatusbarComponent);
    const statusbar = component as BaseEntityStatusbarComponent;
    store.setCurrentEntity('1');
    const violationsStore = TestBed.inject(RuleViolationsSingletonStore);
    violationsStore.setViolations(statusbar.entityDescriptor()!.entityName, [
      { ruleId: 'e1', passed: false, severity: 'ERROR' },
      { ruleId: 'w1', passed: false, severity: 'WARNING' },
      { ruleId: 'w2', passed: false, severity: 'WARNING' },
      { ruleId: 'i1', passed: false, severity: 'INFO' },
    ]);
    TestBed.flushEffects();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.chip.severity-error')?.textContent).toContain('1');
    expect(host.querySelector('.chip.severity-warning')?.textContent).toContain('2');
    expect(host.querySelector('.chip.severity-info')?.textContent).toContain('1');
  });

  it('evaluates entity titles supplied as functions', async () => {
    const { component, store } = await setupContainerComponentTest(BaseEntityStatusbarComponent);
    const statusbar = component as BaseEntityStatusbarComponent;
    const descriptor = new BaseEntityDescriptor({
      attrDescriptors: [],
      entityName: 'TestEntity',
      entityTitle: () => 'Computed Title',
    });
    descriptor.store = statusbar.entityDescriptor()!.store;
    (statusbar.entityDescriptor as unknown as InputSignal<BaseEntityDescriptor>) = signal(descriptor) as unknown as InputSignal<BaseEntityDescriptor>;
    store.setCurrentEntity('1');

    expect(statusbar.entityTitle()).toBe('Computed Title');
  });
});
