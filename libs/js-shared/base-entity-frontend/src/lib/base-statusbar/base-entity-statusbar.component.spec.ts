import { InputSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { RuleViolationsSingletonStore } from '../rule-engine/rule-violations.store';
import { setupContainerComponentTest } from '../../test-setup';
import { BaseEntityStatusbarComponent } from './base-entity-statusbar.component';

/** Stands in for the facade of an embedded level: the descriptor and the store the crumb is resolved from. */
class EmbeddedComponentFacadeStub {
  static readonly rows = [{ id: 'embedded_1_1', name: 'first part' }];
  readonly store = { entities: signal(EmbeddedComponentFacadeStub.rows), currentEntity: signal(undefined) };
  readonly descriptor = new BaseEntityDescriptor({
    attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true)],
    entityName: 'Embedded Component',
    componentParent: 'TestEntity',
    isEmbedded: true,
    store: this.store,
  });
}

const EMBEDDED_FACADE_PROVIDERS = [EmbeddedComponentFacadeStub, { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { 'Embedded Component': EmbeddedComponentFacadeStub } }];

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
    if (!toolbar) throw new Error('mat-toolbar was not rendered');
    expect(toolbar.textContent).toContain('Test Entity');
  });

  it('renders severity chips summarising violations for the current entity', async () => {
    const { fixture, component, store } = await setupContainerComponentTest(BaseEntityStatusbarComponent);
    const statusbar = component as BaseEntityStatusbarComponent;
    store.setCurrentEntity('1');
    const violationsStore = TestBed.inject(RuleViolationsSingletonStore);
    const descriptor = statusbar.entityDescriptor();
    if (!descriptor) throw new Error('Expected entityDescriptor to be defined');
    violationsStore.setViolations(descriptor.entityName, [
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

  /**
   * `.../test-entity/1/details/embedded-component/embedded_1_1/details` — an embedded level's screen replaces
   * its owner's, so this is the only place the hierarchy is shown.
   */
  it('renders one crumb per level of the current URL, the deepest as plain text and the ones above it as links', async () => {
    const { fixture, router } = await setupContainerComponentTest(BaseEntityStatusbarComponent, {}, EMBEDDED_FACADE_PROVIDERS);
    await router.navigateByUrl('/test-entity/1/details/embedded-component/embedded_1_1/details');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(Array.from(host.querySelectorAll('.crumb')).map((crumb) => crumb.textContent?.trim())).toEqual(['Test Entity', 'first part']);
    expect(host.querySelector('.crumb-current')?.textContent?.trim()).toEqual('first part');
    expect(host.querySelectorAll('button.crumb-link')).toHaveLength(1);
  });

  it('walks back up to the level whose crumb is clicked', async () => {
    const { fixture, router } = await setupContainerComponentTest(BaseEntityStatusbarComponent, {}, EMBEDDED_FACADE_PROVIDERS);
    await router.navigateByUrl('/test-entity/1/details/embedded-component/embedded_1_1/details');
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.crumb-link')?.click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toEqual('/test-entity/1/details');
    // No `returnTo`: the owner's form falls back to its own list, not back down to the level just left.
    expect(TestBed.inject(BaseFormNavigatorSingletonStore).returnTo()).toEqual('');
  });

  it('summarises the violations of the level the user is on, not only those of the outermost entity', async () => {
    const { fixture, router } = await setupContainerComponentTest(BaseEntityStatusbarComponent, {}, EMBEDDED_FACADE_PROVIDERS);
    await router.navigateByUrl('/test-entity/1/details/embedded-component/embedded_1_1/details');
    TestBed.inject(RuleViolationsSingletonStore).setViolations('Embedded Component', [{ ruleId: 'e1', passed: false, severity: 'ERROR' }]);
    TestBed.flushEffects();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.chip.severity-error')?.textContent).toContain('1');
  });

  it('evaluates entity titles supplied as functions', async () => {
    const { component, store } = await setupContainerComponentTest(BaseEntityStatusbarComponent);
    const statusbar = component as BaseEntityStatusbarComponent;
    const descriptor = new BaseEntityDescriptor({
      attrDescriptors: [],
      entityName: 'TestEntity',
      entityTitle: () => 'Computed Title',
    });
    const currentDescriptor = statusbar.entityDescriptor();
    if (!currentDescriptor) throw new Error('Expected entityDescriptor to be defined');
    descriptor.store = currentDescriptor.store;
    (statusbar.entityDescriptor as unknown as InputSignal<BaseEntityDescriptor>) = signal(descriptor) as unknown as InputSignal<BaseEntityDescriptor>;
    store.setCurrentEntity('1');

    expect(statusbar.entityTitle()).toBe('Computed Title');
  });
});
