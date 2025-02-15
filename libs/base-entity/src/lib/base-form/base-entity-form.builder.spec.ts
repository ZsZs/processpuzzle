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
import { TestEntityService } from '../test-entity.service';
import { mockService } from '../../test-setup';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';

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

  const descriptors: AbstractAttrDescriptor[] = [
    new FlexboxDescriptor(
      [
        new BaseEntityAttrDescriptor('id', FormControlType.FOREIGN_KEY),
        new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX),
        new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA),
        new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX),
        new BaseEntityAttrDescriptor('date', FormControlType.DATE),
        new BaseEntityAttrDescriptor('selectable', FormControlType.RADIO),
        new BaseEntityAttrDescriptor('enumValue', FormControlType.DROPDOWN),
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
    TestBed.configureTestingModule({
      imports: [BaseFormHostDirective, MockFormContainerComponent],
      providers: [BaseEntityFormBuilder, provideHttpClient(), provideRouter([]), TestEntityStore, { provide: TestEntityService, useValue: mockService }],
    }).compileComponents();

    fixture = TestBed.createComponent(MockFormContainerComponent);
    component = fixture.componentInstance;
    formBuilder = TestBed.inject(BaseEntityFormBuilder);
    store = TestBed.inject(TestEntityStore);

    formBuilder.buildForm(component.formHost.viewContainerRef, component.form, store, descriptors, testEntity);
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
  });

  it('buildForm() adds created controls to the FormGroup', () => {
    expect(component.form.controls['id']).toBeTruthy();
    expect(component.form.controls['name']).toBeTruthy();
    expect(component.form.controls['description']).toBeTruthy();
    expect(component.form.controls['boolean']).toBeTruthy();
    expect(component.form.controls['date']).toBeTruthy();
    expect(component.form.controls['selectable']).toBeTruthy();
    expect(component.form.controls['enumValue']).toBeTruthy();
  });

  it('buildForm() throws Error if a descriptor class is unknown.', () => {
    class DummySubclass extends AbstractAttrDescriptor {
      constructor() {
        super('', FormControlType.DROPDOWN);
      }
    }

    expect(() => formBuilder.buildForm(component.formHost.viewContainerRef, component.form, store, [new DummySubclass()], testEntity)).toThrowError(
      new Error('Undefined subclass of AbstractAttrDescriptor'),
    );
  });

  it('buildForm() throws Error if a FormControlType is unknown.', () => {
    class DummySubclass extends AbstractAttrDescriptor {
      constructor() {
        super('', 'DummyType' as FormControlType);
      }
    }

    expect(() => formBuilder.buildForm(component.formHost.viewContainerRef, component.form, store, [new DummySubclass()], testEntity)).toThrowError(new Error('Undefined form control type'));
  });
});
