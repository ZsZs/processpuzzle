import { TestBed } from '@angular/core/testing';
import { FormGroup, FormControl } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { NgxLoggerLevel, provideLogger } from 'ngx-logging-kit';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';
import { TestEntity } from '../../test-entity';
import { EntityComponentRefComponent, type ComponentNameAttr } from './entity-component-ref.component';

const LOGGING_CONFIGURATION = {
  level: NgxLoggerLevel.OFF,
  serverLogLevel: NgxLoggerLevel.OFF,
  disableConsoleLogging: true,
};

interface SetupOptions {
  entity?: TestEntity & { components?: unknown };
  component?: TestEntity;
  componentNameAttr?: ComponentNameAttr;
  disabled?: boolean;
  formGroup?: FormGroup;
  linkedEntityType?: string;
}

async function setupRef(options: SetupOptions = {}) {
  await TestBed.configureTestingModule({
    imports: [EntityComponentRefComponent],
    providers: [provideRouter([]), provideLogger(LOGGING_CONFIGURATION)],
  }).compileComponents();

  const fixture = TestBed.createComponent(EntityComponentRefComponent<TestEntity, TestEntity>);

  const childComponent = options.component ?? new TestEntity('child-1', 'Child Name');
  const entity = options.entity ?? (Object.assign(new TestEntity('parent-1', 'Parent'), { components: [childComponent] }) as TestEntity & { components?: unknown });
  const componentNameAttr = options.componentNameAttr ?? { attrName: 'name', name: 'components' };
  const formGroup = options.formGroup ?? new FormGroup({ components: new FormControl<TestEntity[]>([childComponent]) });

  fixture.componentRef.setInput('entity', entity);
  fixture.componentRef.setInput('component', childComponent);
  fixture.componentRef.setInput('componentNameAttr', componentNameAttr);
  fixture.componentRef.setInput('disabled', options.disabled ?? false);
  fixture.componentRef.setInput('formGroup', formGroup);
  fixture.componentRef.setInput('linkedEntityType', options.linkedEntityType ?? 'LinkedEntity');
  fixture.detectChanges();

  return { fixture, component: fixture.componentInstance, entity, childComponent, formGroup };
}

describe('EntityComponentRefComponent', () => {
  describe('componentName()', () => {
    it('returns the attribute value when present on the referenced component', async () => {
      const { component } = await setupRef();

      expect(component.componentName()).toBe('Child Name');
    });

    it('falls back to the id when the attrName is empty', async () => {
      const { component } = await setupRef({ componentNameAttr: { attrName: '', name: 'components' } });

      expect(component.componentName()).toBe('child-1');
    });

    it('falls back to the id when the named attribute is undefined on the component', async () => {
      const child = new TestEntity('child-9');
      Reflect.set(child, 'name', undefined);
      const { component } = await setupRef({ component: child });

      expect(component.componentName()).toBe('child-9');
    });

    it('falls back to the id when the named attribute is null on the component', async () => {
      const child = new TestEntity('child-7');
      Reflect.set(child, 'name', null);
      const { component } = await setupRef({ component: child });

      expect(component.componentName()).toBe('child-7');
    });

    it('coerces non-string values to a string', async () => {
      const child = new TestEntity('child-1');
      Reflect.set(child, 'name', 42);
      const { component } = await setupRef({ component: child });

      expect(component.componentName()).toBe('42');
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

  describe('removeComponent()', () => {
    it('removes the referenced component from the parent entity and updates the form control', async () => {
      const childA = new TestEntity('a', 'A');
      const childB = new TestEntity('b', 'B');
      const entity = Object.assign(new TestEntity('parent', 'Parent'), { components: [childA, childB] }) as TestEntity & { components?: unknown };
      const formGroup = new FormGroup({ components: new FormControl<TestEntity[]>([childA, childB]) });
      const { component } = await setupRef({ entity, component: childA, formGroup });

      component.removeComponent();

      const control = formGroup.get('components');
      expect(control?.value).toEqual([childB]);
      expect(Reflect.get(entity, 'components')).toEqual([childB]);
      expect(control?.dirty).toBe(true);
      expect(control?.touched).toBe(true);
    });

    it('does nothing when the component is disabled', async () => {
      const childA = new TestEntity('a', 'A');
      const formGroup = new FormGroup({ components: new FormControl<TestEntity[]>([childA]) });
      const entity = Object.assign(new TestEntity('parent'), { components: [childA] }) as TestEntity & { components?: unknown };
      const { component } = await setupRef({ entity, component: childA, formGroup, disabled: true });

      component.removeComponent();

      expect(formGroup.get('components')?.value).toEqual([childA]);
      expect(Reflect.get(entity, 'components')).toEqual([childA]);
    });

    it('does nothing when the parent entity attribute is not an array', async () => {
      const childA = new TestEntity('a', 'A');
      const entity = Object.assign(new TestEntity('parent'), { components: 'not-an-array' }) as TestEntity & { components?: unknown };
      const formGroup = new FormGroup({ components: new FormControl<unknown>('not-an-array') });
      const { component } = await setupRef({ entity, component: childA, formGroup });

      component.removeComponent();

      expect(Reflect.get(entity, 'components')).toBe('not-an-array');
      expect(formGroup.get('components')?.value).toBe('not-an-array');
      expect(formGroup.get('components')?.dirty).toBe(false);
    });

    it('tolerates a missing form control on the form group', async () => {
      const childA = new TestEntity('a', 'A');
      const childB = new TestEntity('b', 'B');
      const entity = Object.assign(new TestEntity('parent'), { components: [childA, childB] }) as TestEntity & { components?: unknown };
      const formGroup = new FormGroup({});
      const { component } = await setupRef({ entity, component: childA, formGroup });

      expect(() => component.removeComponent()).not.toThrow();
      expect(Reflect.get(entity, 'components')).toEqual([childB]);
    });
  });

  describe('template', () => {
    it('renders the delete button when enabled', async () => {
      const { fixture } = await setupRef();
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('a')?.textContent?.trim()).toBe('Child Name');
      expect(host.querySelector('button[aria-label="Delete component reference"]')).not.toBeNull();
    });

    it('hides the delete button when disabled', async () => {
      const { fixture } = await setupRef({ disabled: true });
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('button[aria-label="Delete component reference"]')).toBeNull();
    });
  });
});
