import { TestBed } from '@angular/core/testing';
import { FormGroup, FormControl } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { NgxLoggerLevel, provideLogger } from 'ngx-logging-kit';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';
import { TestEntity } from '../../test-entity';
import { RelatedEntityRefComponent, type RelatedEntityNameAttr } from './related-entity-ref.component';

const LOGGING_CONFIGURATION = {
  level: NgxLoggerLevel.OFF,
  serverLogLevel: NgxLoggerLevel.OFF,
  disableConsoleLogging: true,
};

interface SetupOptions {
  entity?: TestEntity & { relatedItems?: unknown };
  relatedEntity?: TestEntity;
  relatedEntityNameAttr?: RelatedEntityNameAttr;
  disabled?: boolean;
  formGroup?: FormGroup;
  linkedEntityType?: string;
  referenceIdField?: string;
}

async function setupRef(options: SetupOptions = {}) {
  await TestBed.configureTestingModule({
    imports: [RelatedEntityRefComponent],
    providers: [provideRouter([]), provideLogger(LOGGING_CONFIGURATION)],
  }).compileComponents();

  const fixture = TestBed.createComponent(RelatedEntityRefComponent<TestEntity, TestEntity>);

  const childEntity = options.relatedEntity ?? new TestEntity('child-1', 'Child Name');
  const entity = options.entity ?? (Object.assign(new TestEntity('parent-1', 'Parent'), { relatedItems: [childEntity] }) as TestEntity & { relatedItems?: unknown });
  const relatedEntityNameAttr = options.relatedEntityNameAttr ?? { attrName: 'name', name: 'relatedItems' };
  const formGroup = options.formGroup ?? new FormGroup({ relatedItems: new FormControl<TestEntity[]>([childEntity]) });

  fixture.componentRef.setInput('entity', entity);
  fixture.componentRef.setInput('relatedEntity', childEntity);
  fixture.componentRef.setInput('relatedEntityNameAttr', relatedEntityNameAttr);
  fixture.componentRef.setInput('disabled', options.disabled ?? false);
  fixture.componentRef.setInput('referenceIdField', options.referenceIdField);
  fixture.componentRef.setInput('formGroup', formGroup);
  fixture.componentRef.setInput('linkedEntityType', options.linkedEntityType ?? 'LinkedEntity');
  fixture.detectChanges();

  return { fixture, component: fixture.componentInstance, entity, childEntity, formGroup };
}

describe('RelatedEntityRefComponent', () => {
  describe('relatedEntityName()', () => {
    it('returns the attribute value when present on the referenced entity', async () => {
      const { component } = await setupRef();

      expect(component.relatedEntityName()).toBe('Child Name');
    });

    it('falls back to the id when the attrName is empty', async () => {
      const { component } = await setupRef({ relatedEntityNameAttr: { attrName: '', name: 'relatedItems' } });

      expect(component.relatedEntityName()).toBe('child-1');
    });

    it('falls back to the id when the named attribute is undefined on the related entity', async () => {
      const child = new TestEntity('child-9');
      Reflect.set(child, 'name', undefined);
      const { component } = await setupRef({ relatedEntity: child });

      expect(component.relatedEntityName()).toBe('child-9');
    });

    it('falls back to the id when the named attribute is null on the related entity', async () => {
      const child = new TestEntity('child-7');
      Reflect.set(child, 'name', null);
      const { component } = await setupRef({ relatedEntity: child });

      expect(component.relatedEntityName()).toBe('child-7');
    });

    it('coerces non-string values to a string', async () => {
      const child = new TestEntity('child-1');
      Reflect.set(child, 'name', 42);
      const { component } = await setupRef({ relatedEntity: child });

      expect(component.relatedEntityName()).toBe('42');
    });
  });

  describe('navigateToRelated()', () => {
    it('prevents default and forwards to the form navigator with the linked entity, id, and current url', async () => {
      const { component } = await setupRef({ linkedEntityType: 'OrderLine' });
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      vi.spyOn(formNavigator, 'determineCurrentUrl').mockReturnValue('/orders/1/details');
      vi.spyOn(formNavigator, 'navigateToRelated').mockResolvedValue(undefined);
      const preventDefault = vi.fn();

      component.navigateToRelated({ preventDefault } as unknown as Event);

      expect(preventDefault).toHaveBeenCalled();
      expect(formNavigator.navigateToRelated).toHaveBeenCalledWith('OrderLine', 'child-1', '/orders/1/details');
    });
  });

  describe('removeRelatedEntity()', () => {
    it('removes the reference from the parent entity and updates the form control', async () => {
      const childA = new TestEntity('a', 'A');
      const childB = new TestEntity('b', 'B');
      const entity = Object.assign(new TestEntity('parent', 'Parent'), { relatedItems: [childA, childB] }) as TestEntity & { relatedItems?: unknown };
      const formGroup = new FormGroup({ relatedItems: new FormControl<TestEntity[]>([childA, childB]) });
      const { component } = await setupRef({ entity, relatedEntity: childA, formGroup });

      component.removeRelatedEntity();

      const control = formGroup.get('relatedItems');
      expect(control?.value).toEqual([childB]);
      expect(Reflect.get(entity, 'relatedItems')).toEqual([childB]);
      expect(control?.dirty).toBe(true);
      expect(control?.touched).toBe(true);
    });

    it('does nothing when the control is disabled', async () => {
      const childA = new TestEntity('a', 'A');
      const formGroup = new FormGroup({ relatedItems: new FormControl<TestEntity[]>([childA]) });
      const entity = Object.assign(new TestEntity('parent'), { relatedItems: [childA] }) as TestEntity & { relatedItems?: unknown };
      const { component } = await setupRef({ entity, relatedEntity: childA, formGroup, disabled: true });

      component.removeRelatedEntity();

      expect(formGroup.get('relatedItems')?.value).toEqual([childA]);
      expect(Reflect.get(entity, 'relatedItems')).toEqual([childA]);
    });

    it('does nothing when the parent entity attribute is not an array', async () => {
      const childA = new TestEntity('a', 'A');
      const entity = Object.assign(new TestEntity('parent'), { relatedItems: 'not-an-array' }) as TestEntity & { relatedItems?: unknown };
      const formGroup = new FormGroup({ relatedItems: new FormControl<unknown>('not-an-array') });
      const { component } = await setupRef({ entity, relatedEntity: childA, formGroup });

      component.removeRelatedEntity();

      expect(Reflect.get(entity, 'relatedItems')).toBe('not-an-array');
      expect(formGroup.get('relatedItems')?.value).toBe('not-an-array');
      expect(formGroup.get('relatedItems')?.dirty).toBe(false);
    });

    // The regression this exists for. An attribute the contract declares as `string[]` — base-workflow's
    // `responsibleFor`, `performedByRoles`, `inputs`, `outputs`, `authorizedRoles` — holds bare ids, and
    // every bare id's `.id` is `undefined`. Filtering on `.id` therefore matched nothing, so the row was
    // rendered, its delete button worked, and nothing was removed: a reference could be added but never
    // detached. The ids have to stay ids afterwards, too, or the next save sends the wrong shape.
    it('removes a bare id from an attribute that holds ids rather than entities', async () => {
      const entity = Object.assign(new TestEntity('parent', 'Parent'), { relatedItems: ['a', 'b'] }) as TestEntity & { relatedItems?: unknown };
      const formGroup = new FormGroup({ relatedItems: new FormControl<unknown>(['a', 'b']) });
      const { component } = await setupRef({ entity, relatedEntity: new TestEntity('a', 'A'), formGroup });

      component.removeRelatedEntity();

      expect(formGroup.get('relatedItems')?.value).toEqual(['b']);
      expect(Reflect.get(entity, 'relatedItems')).toEqual(['b']);
    });

    // `App Region` has no `id` at all and is keyed by `type`; the removal path has to resolve the id the
    // same way the rendering did, or the same silent no-op returns.
    it('removes a row keyed by the configured referenceIdField', async () => {
      const rows = [{ code: 'a', label: 'A' }, { code: 'b', label: 'B' }];
      const entity = Object.assign(new TestEntity('parent', 'Parent'), { relatedItems: rows }) as TestEntity & { relatedItems?: unknown };
      const formGroup = new FormGroup({ relatedItems: new FormControl<unknown>(rows) });
      const { component } = await setupRef({ entity, relatedEntity: new TestEntity('a', 'A'), formGroup, referenceIdField: 'code' });

      component.removeRelatedEntity();

      expect(formGroup.get('relatedItems')?.value).toEqual([{ code: 'b', label: 'B' }]);
    });

    it('tolerates a missing form control on the form group', async () => {
      const childA = new TestEntity('a', 'A');
      const childB = new TestEntity('b', 'B');
      const entity = Object.assign(new TestEntity('parent'), { relatedItems: [childA, childB] }) as TestEntity & { relatedItems?: unknown };
      const formGroup = new FormGroup({});
      const { component } = await setupRef({ entity, relatedEntity: childA, formGroup });

      expect(() => component.removeRelatedEntity()).not.toThrow();
      expect(Reflect.get(entity, 'relatedItems')).toEqual([childB]);
    });
  });

  describe('template', () => {
    it('renders the delete button when enabled', async () => {
      const { fixture } = await setupRef();
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('a')?.textContent?.trim()).toBe('Child Name');
      expect(host.querySelector('button[aria-label="Delete related entity reference"]')).not.toBeNull();
    });

    it('hides the delete button when disabled', async () => {
      const { fixture } = await setupRef({ disabled: true });
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('button[aria-label="Delete related entity reference"]')).toBeNull();
    });
  });
});
