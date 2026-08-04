import { Component, inject, InjectionToken, signal, Signal, ViewChild } from '@angular/core';
import { BaseFormHostDirective } from './base-form-host.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BaseEntityFormBuilder } from './base-entity-form.builder';
import { TestEntity } from '../test-entity';
import { FormControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { TestEntityStore } from '../test-entity.store';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TestEntityService } from '../base-entity-service/test-entity.service';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';
import { setupMockService } from '../../test-setup';
import { beforeEach, describe, expect, it } from 'vitest';
import { provideLogger } from 'ngx-logging-kit';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideTranslocoTesting } from '@processpuzzle/test-util';

describe('BaseEntityFormBuilder', () => {
  @Component({
    selector: 'mock-container',
    imports: [BaseFormHostDirective, FormsModule, ReactiveFormsModule],
    template: `
      <form [formGroup]="form">
        <ng-template baseFormHost></ng-template>
      </form>
    `,
  })
  class MockFormContainerComponent {
    form!: FormGroup;
    private formBuilder = inject(FormBuilder);
    @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) formHost!: BaseFormHostDirective;

    constructor() {
      this.form = this.formBuilder.group({});
    }
  }

  const componentDescriptor = new BaseEntityAttrDescriptor('components', FormControlType.RELATED_ENTITIES);
  componentDescriptor.linkedEntityType = 'TestEntityComponent';

  const ownedComponentDescriptor = new BaseEntityAttrDescriptor('ownedComponents', FormControlType.COMPONENTS);
  ownedComponentDescriptor.linkedEntityType = 'TestEntityComponent';

  // `COMPONENTS` resolves the child's descriptor at first render and refuses to draw a containment list for
  // an entity that does not declare this one as its `componentParent`.
  const componentEntityFacade = new InjectionToken<unknown>('COMPONENT_ENTITY_FACADE');
  const componentEntityDescriptor = new BaseEntityDescriptor({
    attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, undefined, undefined, true)],
    entityName: 'TestEntityComponent',
    componentParent: 'Test Entity',
  });

  const embeddedComponentDescriptor = new BaseEntityAttrDescriptor('embeddedComponents', FormControlType.EMBEDDED_COMPONENTS);
  embeddedComponentDescriptor.linkedEntityType = 'EmbeddedComponent';

  // An embedded child is registered through a facade like any other; its store is what its own list and
  // form run on, and it reads the rows out of the containing entity's payload.
  const embeddedEntityFacade = new InjectionToken<unknown>('EMBEDDED_ENTITY_FACADE');
  const embeddedEntityStore = { entities: signal([]), load: () => undefined };
  const embeddedEntityDescriptor = new BaseEntityDescriptor({
    attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, undefined, undefined, true)],
    entityName: 'EmbeddedComponent',
    componentParent: 'Test Entity',
    isEmbedded: true,
  });

  const foreignKeyDescriptor = new BaseEntityAttrDescriptor('id', FormControlType.FOREIGN_KEY);
  foreignKeyDescriptor.linkedEntityType = 'TestEntityComponent';

  const lookupDescriptor = new BaseEntityAttrDescriptor('lookupValue', FormControlType.LOOKUP);
  lookupDescriptor.linkedEntityType = 'TestEntityLookup';

  const descriptors: AbstractAttrDescriptor[] = [
    new FlexboxDescriptor(
      [
        foreignKeyDescriptor,
        new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX),
        new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA),
        new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX),
        new BaseEntityAttrDescriptor('date', FormControlType.DATE),
        new BaseEntityAttrDescriptor('selectable', FormControlType.RADIO),
        new BaseEntityAttrDescriptor('enumValue', FormControlType.DROPDOWN),
        lookupDescriptor,
        componentDescriptor,
        ownedComponentDescriptor,
        embeddedComponentDescriptor,
      ],
      FlexDirection.CONTAINER,
    ),
  ];
  const testEntity: Signal<TestEntity> = signal(new TestEntity());
  let store: InstanceType<typeof TestEntityStore>;
  let component: MockFormContainerComponent;
  let fixture: ComponentFixture<MockFormContainerComponent>;
  let formBuilder: BaseEntityFormBuilder<TestEntity>;

  beforeEach(() => {
    const mockService = setupMockService();
    TestBed.configureTestingModule({
      imports: [BaseFormHostDirective, MockFormContainerComponent],
      providers: [
        BaseEntityFormBuilder,
        provideHttpClient(),
        provideLogger({ level: 7 }),
        provideNativeDateAdapter(),
        provideRouter([]),
        provideTranslocoTesting({ translations: {} }),
        TestEntityStore,
        { provide: TestEntityService, useValue: mockService },
        { provide: componentEntityFacade, useValue: { descriptor: componentEntityDescriptor } },
        { provide: embeddedEntityFacade, useValue: { descriptor: embeddedEntityDescriptor, store: embeddedEntityStore } },
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { TestEntityComponent: componentEntityFacade, EmbeddedComponent: embeddedEntityFacade } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MockFormContainerComponent);
    component = fixture.componentInstance;
    formBuilder = TestBed.inject(BaseEntityFormBuilder) as unknown as BaseEntityFormBuilder<TestEntity>;
    store = TestBed.inject(TestEntityStore);

    formBuilder.buildForm(component.formHost.viewContainerRef, component.form, store, descriptors, testEntity, 'Test Entity');
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(formBuilder).toBeTruthy();
  });

  it('buildForm() instantiates from controls according to the descriptors.', () => {
    expect(fixture.debugElement.query(By.css('form > flex-box')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box base-textbox')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box base-textarea')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box base-checkbox')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box base-datepicker')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box base-radio')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box base-dropdown')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box lookup-control')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box app-related-entities-list')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box app-related-entities-list button[title="Add TestEntityComponent"]')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box app-components-list')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box app-embedded-components-list')).nativeElement).toBeTruthy();
  });

  it('buildForm() adds created controls to the FormGroup', () => {
    expect(component.form.controls['id']).toBeTruthy();
    expect(component.form.controls['name']).toBeTruthy();
    expect(component.form.controls['description']).toBeTruthy();
    expect(component.form.controls['boolean']).toBeTruthy();
    expect(component.form.controls['date']).toBeTruthy();
    expect(component.form.controls['selectable']).toBeTruthy();
    expect(component.form.controls['enumValue']).toBeTruthy();
    expect(component.form.controls['lookupValue']).toBeTruthy();
    expect(component.form.controls['components']).toBeTruthy();
    expect(component.form.controls['ownedComponents']).toBeTruthy();
  });

  // The rows are edited on the child's own form now, not inline, so the attribute is an ordinary control
  // holding the array — the same shape COMPONENTS uses.
  it('buildForm() backs an EMBEDDED_COMPONENTS attribute with a plain FormControl', () => {
    expect(component.form.controls['embeddedComponents']).toBeInstanceOf(FormControl);
  });

  it('buildForm() throws Error if a descriptor class is unknown.', () => {
    class DummySubclass extends AbstractAttrDescriptor {
      constructor() {
        super('', FormControlType.DROPDOWN);
      }
    }

    expect(() => formBuilder.buildForm(component.formHost.viewContainerRef, component.form, store, [new DummySubclass()], testEntity, 'Test Entity')).toThrow(
      new Error('Undefined subclass of AbstractAttrDescriptor'),
    );
  });

  it('buildForm() throws Error if a FormControlType is unknown.', () => {
    class DummySubclass extends AbstractAttrDescriptor {
      constructor() {
        super('', 'DummyType' as FormControlType);
      }
    }

    expect(() => formBuilder.buildForm(component.formHost.viewContainerRef, component.form, store, [new DummySubclass()], testEntity, 'Test Entity')).toThrow(new Error('Undefined form control type'));
  });
});
