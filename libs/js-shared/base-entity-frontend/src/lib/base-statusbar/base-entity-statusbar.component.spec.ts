import { InputSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { UnsavedChangesRegistry } from '../base-form/unsaved-changes/unsaved-changes.registry';
import { RuleViolationsSingletonStore } from '../rule-engine/rule-violations.store';
import { DummyComponent, setupContainerComponentTest } from '../../test-setup';
import { BaseEntityStatusbarComponent } from './base-entity-statusbar.component';
import { WorkingContextService } from '../working-context/working-context.service';
import { vi } from 'vitest';

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

  it('shows an indicator while any form holds unsaved changes', async () => {
    const { fixture, store } = await setupContainerComponentTest(BaseEntityStatusbarComponent);
    store.setCurrentEntity('1');
    const unsaved = signal(false);
    TestBed.inject(UnsavedChangesRegistry).register(unsaved);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="unsaved-changes-indicator"]')).toBeNull();

    unsaved.set(true);
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="unsaved-changes-indicator"]')).not.toBeNull();
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

  it.each([
    { name: '', expected: 'embedded_1_1' },
    { name: '0', expected: '0' },
  ])('resolves a breadcrumb title of "$name" as "$expected"', async ({ name, expected }) => {
    const { fixture, router } = await setupContainerComponentTest(BaseEntityStatusbarComponent, {}, EMBEDDED_FACADE_PROVIDERS);
    TestBed.inject(EmbeddedComponentFacadeStub).store.entities.set([{ id: 'embedded_1_1', name }]);
    await router.navigateByUrl('/test-entity/1/details/embedded-component/embedded_1_1/details');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.crumb-current')?.textContent?.trim()).toBe(expected);
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

  describe('working context', () => {
    const raceDescriptor = new BaseEntityDescriptor({
      attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true), new BaseEntityAttrDescriptor('status', FormControlType.STATE, 'Status')],
      entityName: 'Race',
    });
    const activeRace = {
      name: 'race',
      id: 'r1',
      definition: { name: 'race', entityName: 'Race', display: { label: '{{name}}', badge: 'state', extra: 'round {{round}}' } },
      descriptor: raceDescriptor,
      entity: { id: 'r1', name: 'Spring Cup', status: 'Running', round: 3 },
      url: '/ctx/race/r1',
    };
    const workingContextStub = { active: signal([activeRace]), clear: vi.fn(async () => true), statusbarHostedByShell: signal(false), stateAttributeOf: () => undefined, stateOf: () => undefined, firing: signal(false) };

    it('renders the active context as the root crumb, with its badge and extra field, even with no entity selected', async () => {
      const { fixture } = await setupContainerComponentTest(BaseEntityStatusbarComponent, {}, [{ provide: WorkingContextService, useValue: workingContextStub }]);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      const crumb = host.querySelector('[data-testid="race-context-crumb"]');
      expect(crumb?.textContent).toContain('Spring Cup');
      expect(crumb?.textContent).toContain('round 3');
      expect(host.querySelector('[data-testid="race-context-crumb-badge"]')?.textContent?.trim()).toBe('Running');
    });

    it('does not repeat, as a crumb of the breadcrumb, the working context the URL starts with', async () => {
      const { fixture, router } = await setupContainerComponentTest(BaseEntityStatusbarComponent, {}, [...EMBEDDED_FACADE_PROVIDERS, { provide: WorkingContextService, useValue: workingContextStub }]);
      TestBed.inject(Router).resetConfig([
        {
          path: 'ctx/warehouse/:contextId',
          data: { workingContext: 'warehouse', entityName: 'TestEntity' },
          children: [{ path: 'embedded-component', data: { entityName: 'Embedded Component', embeddedEntity: true }, children: [{ path: ':entityId/details', component: DummyComponent }] }],
        },
      ]);
      await router.navigateByUrl('/ctx/warehouse/W1/embedded-component/embedded_1_1/details');
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      // The context crumb in front already says which warehouse; the breadcrumb continues with the row alone.
      expect(host.querySelector('[data-testid="race-context-crumb"]')).not.toBeNull();
      expect(Array.from(host.querySelectorAll('.crumb')).map((crumb) => crumb.textContent?.trim())).toEqual(['first part']);
    });

    it('leaves the contexts to the shell when it shows them in a status bar of its own', async () => {
      const hosted = { ...workingContextStub, statusbarHostedByShell: signal(true) };
      const { fixture } = await setupContainerComponentTest(BaseEntityStatusbarComponent, {}, [{ provide: WorkingContextService, useValue: hosted }]);
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="race-context-crumb"]')).toBeNull();
    });

    it('shows the state machine\'s name of the state as the badge, and offers its transitions in the menu', async () => {
      const fire = vi.fn(async () => true);
      const stateful = {
        ...workingContextStub,
        stateOf: () => ({ currentKey: 'RUNNING', currentLabel: 'Running now', transitions: [{ key: 'finish', label: 'Finish race', enabled: true }, { key: 'pause', label: 'Pause', enabled: false, blockedReason: 'Not yet' }] }),
        fire,
      };
      const { fixture } = await setupContainerComponentTest(BaseEntityStatusbarComponent, {}, [{ provide: WorkingContextService, useValue: stateful }]);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;
      expect(host.querySelector('[data-testid="race-context-crumb-badge"]')?.textContent?.trim()).toBe('Running now');

      (host.querySelector('[data-testid="race-context-crumb-menu"]') as HTMLButtonElement).click();
      fixture.detectChanges();
      const finish = document.querySelector('[data-testid="race-context-crumb-menu-transition-finish"]') as HTMLButtonElement;
      const pause = document.querySelector('[data-testid="race-context-crumb-menu-transition-pause"]') as HTMLButtonElement;
      expect(pause.disabled).toBe(true);
      finish.click();

      expect(fire).toHaveBeenCalledWith('race', 'finish');
    });

    it('clears the context from its menu', async () => {
      const { fixture } = await setupContainerComponentTest(BaseEntityStatusbarComponent, {}, [{ provide: WorkingContextService, useValue: workingContextStub }]);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      (host.querySelector('[data-testid="race-context-crumb-menu"]') as HTMLButtonElement).click();
      fixture.detectChanges();
      (document.querySelector('[data-testid="race-context-crumb-menu-clear"]') as HTMLButtonElement).click();

      expect(workingContextStub.clear).toHaveBeenCalledWith('race');
    });
  });
});
