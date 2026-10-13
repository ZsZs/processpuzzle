import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { AbstractAttrDescriptor, BaseEntity, BaseEntityAttrDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { provideLogger } from 'ngx-logging-kit';
import { describe, expect, it, vi } from 'vitest';
import { PropsSchema } from '../widget-definition/widget-definition';
import { CARDS_GRID_WIDGET_REGISTRATION } from '../mat-cards-grid/cards-grid.widget';
import { WIDGET_REGISTRY, WidgetRegistration } from '../widget-registry/widget-registry.token';
import { createWidgetPropsAttrDescriptor, toProps, WidgetPropsControlComponent } from './widget-props-control.component';

@Component({ selector: 'pp-props-test-widget', template: '' })
class PropsTestWidgetComponent {
  readonly label = input('');
  readonly count = input(0);
}

const LABELLED: PropsSchema = { type: 'object', properties: { label: { type: 'string' }, count: { type: 'integer' } }, required: ['label'] };

const REGISTRY: ReadonlyMap<string, WidgetRegistration> = new Map([
  ['labelled', { type: 'labelled', component: PropsTestWidgetComponent, definition: { name: 'Labelled', propsSchema: LABELLED } }],
  ['bare', { type: 'bare', component: PropsTestWidgetComponent, definition: { name: 'Bare', propsSchema: { type: 'object', properties: {} } } }],
  ['undescribed', { type: 'undescribed', component: PropsTestWidgetComponent, definition: { name: 'Undescribed' } }],
]);

/**
 * The form builder, reduced to what the control relies on: one form control per descriptor, seeded from the
 * entity it is handed. The real controls are base-entity's to test; this spec is about which descriptors the
 * control asks for, into which group, and what it writes back.
 */
function fakeFormBuilder() {
  return {
    buildForm: vi.fn((_host: unknown, group: FormGroup, _store: unknown, descriptors: AbstractAttrDescriptor[], entity: () => BaseEntity) => {
      for (const descriptor of descriptors) group.addControl(descriptor.attrName, new FormControl(Reflect.get(entity(), descriptor.attrName)));
    }),
  };
}

describe('WidgetPropsControlComponent', () => {
  function render(type: string, props: Record<string, unknown> = {}) {
    TestBed.configureTestingModule({
      imports: [WidgetPropsControlComponent],
      providers: [provideRouter([]), provideLogger({ level: 7 }), provideTranslocoTesting({ translations: {} }), { provide: WIDGET_REGISTRY, useValue: REGISTRY }],
    });
    const formGroup = new FormGroup({ type: new FormControl(type), props: new FormControl<Record<string, unknown>>(props) });
    const formBuilder = fakeFormBuilder();
    const fixture = TestBed.createComponent(WidgetPropsControlComponent);
    fixture.componentRef.setInput('config', createWidgetPropsAttrDescriptor());
    fixture.componentRef.setInput('entity', { id: 'w1' });
    fixture.componentRef.setInput('entityName', 'App Widget');
    fixture.componentRef.setInput('value', props);
    const control = fixture.componentInstance as unknown as { formGroup: FormGroup; formBuilder: unknown; store: unknown };
    control.formGroup = formGroup;
    control.formBuilder = formBuilder;
    control.store = {};
    fixture.detectChanges();
    return { fixture, formGroup, formBuilder };
  }

  const builtDescriptors = (formBuilder: ReturnType<typeof fakeFormBuilder>) => formBuilder.buildForm.mock.lastCall?.[3] ?? [];
  const builtGroup = (formBuilder: ReturnType<typeof fakeFormBuilder>) => formBuilder.buildForm.mock.lastCall?.[1] as FormGroup;

  it('builds one control per property of the type schema, into a nested form seeded from the props', () => {
    const { formBuilder, formGroup } = render('labelled', { label: 'Hello' });

    expect(builtDescriptors(formBuilder).map((descriptor) => descriptor.attrName)).toEqual(['label', 'count']);
    expect(builtGroup(formBuilder)).not.toBe(formGroup);
    expect(builtGroup(formBuilder).get('label')?.value).toBe('Hello');
  });

  it('writes the nested form back as props: described ones only, empty ones omitted, numbers as numbers', () => {
    const { formBuilder, formGroup } = render('labelled', { label: 'Hello' });

    builtGroup(formBuilder).patchValue({ label: 'Bye', count: '3' });

    expect(formGroup.get('props')?.value).toEqual({ label: 'Bye', count: 3 });
    expect(formGroup.get('props')?.dirty).toBe(true);
  });

  it('makes the props invalid while the nested form is', () => {
    const { formBuilder, formGroup } = render('labelled', { label: 'Hello' });
    builtGroup(formBuilder).get('label')?.setErrors({ required: true });
    formGroup.get('props')?.updateValueAndValidity();

    expect(formGroup.get('props')?.valid).toBe(false);
  });

  it('rebuilds for the new type when the type changes', () => {
    const { fixture, formBuilder, formGroup } = render('labelled');

    formGroup.get('type')?.setValue('bare');
    fixture.detectChanges();

    // Nothing to build for a type that declares no props — only the note saying so.
    expect(formBuilder.buildForm).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('[data-testid="widget-props-none"]')).not.toBeNull();
  });

  it.each(['undescribed', 'unregistered'])('falls back to the open editor on the props control itself for an %s type', (type) => {
    const { formBuilder, formGroup } = render(type, { anything: 'goes' });

    expect(builtGroup(formBuilder)).toBe(formGroup);
    expect(builtDescriptors(formBuilder).map((descriptor) => [descriptor.attrName, descriptor.formControlType])).toEqual([['props', FormControlType.ADDITIONAL_PROPERTIES]]);
  });

  it('builds full nested descriptors for cards-grid widget', () => {
    const registryWithCardsGrid = new Map([
      ...REGISTRY.entries(),
      [CARDS_GRID_WIDGET_REGISTRATION.type, CARDS_GRID_WIDGET_REGISTRATION],
    ]);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [WidgetPropsControlComponent],
      providers: [
        provideRouter([]),
        provideLogger({ level: 7 }),
        provideTranslocoTesting({ translations: {} }),
        { provide: WIDGET_REGISTRY, useValue: registryWithCardsGrid },
      ],
    });

    const initialCards = [
      {
        title: 'Feature 1',
        subtitle: 'Sub 1',
        icon: 'star',
        content: ['Intro text', 'Item 1'],
        actions: [{ caption: 'Click here', link: '/go', buttonType: 'filled' }],
      },
    ];

    const formGroup = new FormGroup({
      type: new FormControl('cards-grid'),
      props: new FormControl<Record<string, unknown>>({ cards: initialCards }),
    });
    const formBuilder = fakeFormBuilder();
    const fixture = TestBed.createComponent(WidgetPropsControlComponent);
    fixture.componentRef.setInput('config', createWidgetPropsAttrDescriptor());
    fixture.componentRef.setInput('entity', { id: 'w-cards' });
    fixture.componentRef.setInput('entityName', 'App Widget');
    fixture.componentRef.setInput('value', { cards: initialCards });
    const control = fixture.componentInstance as unknown as { formGroup: FormGroup; formBuilder: unknown; store: unknown };
    control.formGroup = formGroup;
    control.formBuilder = formBuilder;
    control.store = {};
    fixture.detectChanges();

    const descriptors = builtDescriptors(formBuilder);
    expect(descriptors).toHaveLength(1);
    const cardsDesc = descriptors[0] as BaseEntityAttrDescriptor;
    expect(cardsDesc.attrName).toBe('cards');
    expect(cardsDesc.formControlType).toBe(FormControlType.EMBEDDED_LIST);
    expect(cardsDesc.rowLabelAttr).toBe('title');
    expect(cardsDesc.childDescriptors?.map((d) => d.attrName)).toEqual([
      'title',
      'subtitle',
      'icon',
      'content',
      'actions',
      'menuItems',
      'translocoPrefix',
    ]);

    const contentDesc = cardsDesc.childDescriptors?.find((d) => d.attrName === 'content') as BaseEntityAttrDescriptor;
    expect(contentDesc.formControlType).toBe(FormControlType.TAGS);

    const actionsDesc = cardsDesc.childDescriptors?.find((d) => d.attrName === 'actions') as BaseEntityAttrDescriptor;
    expect(actionsDesc.formControlType).toBe(FormControlType.EMBEDDED_LIST);
    expect(actionsDesc.rowLabelAttr).toBe('caption');
    expect(actionsDesc.childDescriptors?.map((d) => d.attrName)).toEqual(['caption', 'link', 'buttonType']);
  });
});

describe('toProps', () => {
  it('drops what the schema does not describe', () => {
    expect(toProps({ label: 'x', stale: true }, LABELLED)).toEqual({ label: 'x' });
  });

  it('drops a number that does not parse rather than storing NaN', () => {
    expect(toProps({ label: 'x', count: 'many' }, LABELLED)).toEqual({ label: 'x' });
  });

  it('recursively trims and coerces nested objects and lists, dropping empty entries', () => {
    const SCHEMA: PropsSchema = {
      type: 'object',
      properties: {
        config: {
          type: 'object',
          properties: {
            title: { type: 'string' },
            limit: { type: 'integer' },
          },
        },
        cards: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              title: { type: 'string' },
              tags: {
                type: 'array',
                items: { type: 'string' },
              },
              actions: {
                type: 'array',
                items: {
                  type: 'object',
                  properties: {
                    caption: { type: 'string' },
                    link: { type: 'string' },
                  },
                },
              },
            },
          },
        },
      },
    };

    const input = {
      config: { title: '  Dashboard  ', limit: '25' },
      cards: [
        {
          title: 'Card 1',
          tags: ['alpha', ' beta ', ''],
          actions: [
            { caption: '  Save  ', link: '/save' },
            { caption: '', link: '' },
          ],
        },
        {
          title: '',
          actions: [],
        },
      ],
      extra: 'ignored',
    };

    expect(toProps(input, SCHEMA)).toEqual({
      config: { title: 'Dashboard', limit: 25 },
      cards: [
        {
          title: 'Card 1',
          tags: ['alpha', 'beta'],
          actions: [{ caption: 'Save', link: '/save' }],
        },
      ],
    });
  });
});
