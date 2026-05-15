import { Component, inject, signal, Signal, ViewChild } from '@angular/core';
import { BaseFormHostDirective } from './base-form-host.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BaseEntityFormBuilder } from './base-entity-form.builder';
import { TestEntity } from '../test-entity';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { TestEntityStore } from '../test-entity.store';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TestEntityService } from '../base-entity-service/test-entity.service';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';
import { setupMockService } from '../../test-setup';
import { beforeEach, describe, expect, it } from 'vitest';
import { provideLogger } from 'ngx-logging-kit';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { provideNativeDateAdapter } from '@angular/material/core';

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

  const componentDescriptor = new BaseEntityAttrDescriptor('components', FormControlType.COMPONENTS);
  componentDescriptor.linkedEntityType = new BaseEntityDescriptor({
    attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, undefined, undefined, true)],
    entityName: 'TestEntityComponent',
  });

  const foreignKeyDescriptor = new BaseEntityAttrDescriptor('id', FormControlType.FOREIGN_KEY);
  foreignKeyDescriptor.linkedEntityType = new BaseEntityDescriptor({ attrDescriptors: [], entityName: 'TestEntityComponent' });

  const lookupDescriptor = new BaseEntityAttrDescriptor('lookupValue', FormControlType.LOOKUP);
  lookupDescriptor.linkedEntityType = new BaseEntityDescriptor({ attrDescriptors: [], entityName: 'TestEntityLookup' });

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
      ],
      FlexDirection.CONTAINER,
    ),
  ];
  const testEntity: Signal<TestEntity> = signal(new TestEntity());
  let store: any;
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
        TestEntityStore,
        { provide: TestEntityService, useValue: mockService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MockFormContainerComponent);
    component = fixture.componentInstance;
    formBuilder = TestBed.inject(BaseEntityFormBuilder) as unknown as BaseEntityFormBuilder<TestEntity>;
    store = TestBed.inject(TestEntityStore);

    formBuilder.buildForm(component.formHost.viewContainerRef, component.form, store, descriptors, testEntity);
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
    expect(fixture.debugElement.query(By.css('flex-box app-component-list')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('flex-box app-component-list button[title="Add TestEntityComponent"]')).nativeElement).toBeTruthy();
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
  });

  it('buildForm() throws Error if a descriptor class is unknown.', () => {
    class DummySubclass extends AbstractAttrDescriptor {
      constructor() {
        super('', FormControlType.DROPDOWN);
      }
    }

    expect(() => formBuilder.buildForm(component.formHost.viewContainerRef, component.form, store, [new DummySubclass()], testEntity)).toThrow(
      new Error('Undefined subclass of AbstractAttrDescriptor'),
    );
  });

  it('buildForm() throws Error if a FormControlType is unknown.', () => {
    class DummySubclass extends AbstractAttrDescriptor {
      constructor() {
        super('', 'DummyType' as FormControlType);
      }
    }

    expect(() => formBuilder.buildForm(component.formHost.viewContainerRef, component.form, store, [new DummySubclass()], testEntity)).toThrow(new Error('Undefined form control type'));
  });
});
