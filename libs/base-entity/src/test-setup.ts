import { setupZoneTestEnv } from 'jest-preset-angular/setup-env/zone';
import { Spy } from 'jest-auto-spies/src/jest-auto-spies.types';
import { BaseEntityLoadResponse } from './lib/base-entity-service/base-entity-load-response';
import { BreakpointObserver } from '@angular/cdk/layout';
import { RouterTestingHarness } from '@angular/router/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { BaseEntityListComponent } from './lib/base-list/base-entity-list.component';
import { BaseUrlSegments } from './lib/base-form-navigator/base-url-segments';
import { BaseEntityStatusbarComponent } from './lib/base-statusbar/base-entity-statusbar.component';
import { BaseEntityToolbarComponent } from './lib/base-toolbar/base-entity-toolbar.component';
import { BaseEntityContainerComponent } from './lib/base-entity-container.component';
import { BaseEntityFormComponent } from './lib/base-form/base-entity-form.component';
import { BaseFormControlComponent } from './lib/base-form/base-form-control.component';
import { Component, ComponentRef, inject, input, InputSignal, OnInit, Signal, signal, Type, ViewChild } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BaseFormHostDirective } from './lib/base-form/base-form-host.directive';
import { TestEntity, TestEnum } from './lib/test-entity';
import { AbstractAttrDescriptor, FormControlType } from './lib/base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from './lib/base-entity/base-entity-attr.descriptor';
import { TestBed } from '@angular/core/testing';
import { BaseEntityDescriptor } from './lib/base-entity/base-entity.descriptor';
import { TestEntityStore } from './lib/test-entity.store';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, ROUTER_OUTLET_DATA } from '@angular/router';
import { TestEntityService } from './lib/base-entity-service/test-entity.service';
import { CONFIGURATION_OPTIONS, ConfigurationService, LayoutService, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { TestConfiguration } from './lib/test-configuration';
import { BaseEntityTabsComponent } from './lib/base-tabs/base-entity-tabs.component';
import { createSpyFromClass } from 'jest-auto-spies';
import { of } from 'rxjs';
import { MockBreakpointObserver } from '@processpuzzle/test-util';
import { FlexboxDescriptor } from './lib/base-entity/flexboxDescriptor';

// @ts-expect-error - configure test environment
setupZoneTestEnv({ testEnvironment: '@happy-dom/jest-environment' });

@Component({
  selector: 'mock-control-container',
  template: `
    <form [formGroup]="baseEntityForm" (ngSubmit)="onSubmit()">
      <ng-template baseFormHost></ng-template>
    </form>
  `,
  standalone: true,
  imports: [BaseFormHostDirective, ReactiveFormsModule],
})
export class MockControlContainerComponent<C extends BaseFormControlComponent<TestEntity>> implements OnInit {
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) componentHost!: BaseFormHostDirective;
  componentRef?: ComponentRef<C>;
  componentType: Signal<Type<C>> = input.required();
  config: Signal<BaseEntityAttrDescriptor> = input.required();
  entity: Signal<TestEntity> = input.required<TestEntity>();
  protected baseEntityForm!: FormGroup;
  protected formBuilder = inject(FormBuilder);

  // region Angular lifacycle hooks
  ngOnInit(): void {
    this.baseEntityForm = this.formBuilder.group({});
    this.createFromControl();
  }

  // endregion

  // region event handler methods
  onSubmit() {
    console.log(this.baseEntityForm.value);
  }

  // endregion

  // region, protected, private helper methods
  private createFromControl() {
    const currentAttrValue = Reflect.get(this.entity(), this.config().attrName);
    this.componentHost.viewContainerRef.clear();
    this.componentRef = this.componentHost.viewContainerRef.createComponent<C>(this.componentType());
    this.componentRef.instance.formGroup = this.baseEntityForm;
    this.componentRef.instance.config = this.config;
    this.componentRef.instance.entity = this.entity;
    this.componentRef.instance.value = signal(currentAttrValue);
    const formControl = new FormControl({ value: currentAttrValue, disabled: this.config().disabled }, Validators.required);
    this.baseEntityForm.addControl(this.config().attrName, formControl);
  }

  // endregion
}

@Component({
  selector: 'dummy-component',
  template: ` <div></div>`,
  standalone: true,
})
export class DummyComponent {}

function createEntityDescriptor(attrDescriptors: AbstractAttrDescriptor[]) {
  const entityDescriptor: BaseEntityDescriptor = {
    store: TestEntityStore,
    attrDescriptors: attrDescriptors,
    entityName: 'TestEntity',
    entityTitle: 'Test Entity',
  };
  return entityDescriptor;
}

export const testEntity_1 = new TestEntity('1', 'hello', 'anything', false, 100, new Date('2024-01-18T20:02:27.000Z'), TestEnum.VALUE_ONE);
export const testEntity_2 = new TestEntity('2', 'bella', 'something', true, 200, new Date('2023-02-18T20:02:27.000Z'), TestEnum.VALUE_TWO);
export const newTestEntity = new TestEntity('3', 'new', 'new description', true, 300, new Date('2024-02-18T20:02:27.000Z'), TestEnum.VALUE_THREE);
export const MOCK_API_RESPONSE: TestEntity[] = [testEntity_1, testEntity_2];
export const MOCK_PAGED_RESPONSE: BaseEntityLoadResponse<TestEntity> = { page: 33, pageSize: 2, totalPageCount: 333, content: MOCK_API_RESPONSE };
export let mockService: Spy<TestEntityService>;

export function setupMockService({ isApiFailed = false, payload = MOCK_API_RESPONSE }: { isApiFailed?: boolean; payload?: TestEntity[] | BaseEntityLoadResponse<TestEntity> } = {}) {
  mockService = createSpyFromClass<TestEntityService>(TestEntityService);
  if (isApiFailed) {
    mockService.add.throwWith({ message: 'API Failed' });
    mockService.delete.throwWith({ message: 'API Failed' });
    mockService.deleteAll.throwWith({ message: 'API Failed' });
    mockService.findByQuery.throwWith({ message: 'API Failed' });
    mockService.update.throwWith({ message: 'API Failed' });
  } else {
    mockService.add.mockReturnValue(of(newTestEntity));
    mockService.delete.mockReturnValue(of(undefined));
    mockService.deleteAll.mockReturnValue(of(undefined));
    if (payload) mockService.findByQuery.mockReturnValue(of(payload));
    else mockService.findByQuery.mockReturnValue(of(MOCK_API_RESPONSE));
    Reflect.set(testEntity_1, 'name', 'changed');
    mockService.update.mockReturnValue(of(testEntity_1));
  }
}

export async function setupListComponentTest(attrDescriptors: BaseEntityAttrDescriptor[], entities: TestEntity[]) {
  const entityDescriptor = createEntityDescriptor(attrDescriptors);
  const mockService = createSpyFromClass(TestEntityService);
  mockService.findByQuery.mockReturnValue(of(entities));

  await TestBed.configureTestingModule({
    imports: [BaseEntityListComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([
        { path: 'home', component: DummyComponent },
        { path: 'test-entity/:id/details', component: DummyComponent },
        { path: 'test-entity/list', component: DummyComponent },
        { path: 'test-entity-component/:id/details', component: DummyComponent },
        { path: 'test-entity-component/list', component: DummyComponent },
      ]),
      { provide: TestEntityService, useValue: mockService },
      { provide: 'BASE_ENTITY_SERVICE', useValue: TestEntityService },
      { provide: CONFIGURATION_OPTIONS, useValue: { urlFactory: () => ['environments/config.common.json'], log: true } },
      {
        provide: ROUTER_OUTLET_DATA,
        useFactory: () => {
          entityDescriptor.store = inject(TestEntityStore);
          return signal(entityDescriptor);
        },
        deps: [TestEntityStore],
      },
      {
        provide: RUNTIME_CONFIGURATION,
        useFactory: async (configurationService: ConfigurationService<TestConfiguration>) => {
          await configurationService.init();
          return configurationService.configuration;
        },
        deps: [ConfigurationService],
      },
    ],
  }).compileComponents();

  await RouterTestingHarness.create('test-entity/list');
  const fixture = TestBed.createComponent(BaseEntityListComponent<TestEntity>);
  const component = fixture.componentInstance;
  const store = entityDescriptor.store;
  jest.spyOn(store, 'load');
  fixture.detectChanges();
  TestBed.flushEffects();

  return { fixture, component, store };
}

export async function setupFormComponentTest(attrDescriptors: AbstractAttrDescriptor[], entity = new TestEntity(), isEntityNew = false) {
  const entityDescriptor = createEntityDescriptor(attrDescriptors);

  await TestBed.configureTestingModule({
    imports: [BaseEntityFormComponent, BaseFormHostDirective],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      TestEntityStore,
      { provide: ROUTER_OUTLET_DATA, useValue: signal(entityDescriptor) },
      { provide: 'BASE_ENTITY_SERVICE', useValue: TestEntityService },
      { provide: CONFIGURATION_OPTIONS, useValue: { urlFactory: () => ['environments/config.common.json'], log: true } },
      {
        provide: RUNTIME_CONFIGURATION,
        useFactory: async (configurationService: ConfigurationService<TestConfiguration>) => {
          await configurationService.init();
          return configurationService.configuration;
        },
        deps: [ConfigurationService],
      },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(BaseEntityFormComponent<TestEntity>);
  const component = fixture.componentInstance;
  const store = TestBed.inject(TestEntityStore);
  entityDescriptor.store = store;
  const testEntity = isEntityNew ? new TestEntity() : entity;
  const id = isEntityNew ? BaseUrlSegments.NewEntity : testEntity.id;
  component.entity = signal<TestEntity>(testEntity);
  component.entityId = signal<string>(id);
  component.baseEntityListOptions = signal<BaseEntityDescriptor>(entityDescriptor);
  fixture.detectChanges();
  TestBed.flushEffects();

  return { fixture, component, store };
}

export async function setupFormControlTest<C extends BaseFormControlComponent<TestEntity>>(controlType: Type<C>, config: FlexboxDescriptor | BaseEntityAttrDescriptor, entity: TestEntity) {
  await TestBed.configureTestingModule({
    imports: [MockControlContainerComponent],
  }).compileComponents();

  const fixture = TestBed.createComponent(MockControlContainerComponent<C>);
  const containerComponent = fixture.componentInstance;
  containerComponent.componentType = signal(controlType);
  containerComponent.config = signal<BaseEntityAttrDescriptor>(config as BaseEntityAttrDescriptor);
  containerComponent.entity = signal<TestEntity>(entity);
  fixture.detectChanges();
  const component = containerComponent.componentRef?.instance;

  return { fixture, containerComponent, component };
}

export async function setupContainerComponentTest(componentType: Type<BaseEntityContainerComponent | BaseEntityTabsComponent | BaseEntityToolbarComponent<TestEntity> | BaseEntityStatusbarComponent>) {
  const checkboxConfig = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX);
  const labelConfig = new BaseEntityAttrDescriptor('description', FormControlType.LABEL);
  const entityDescriptor: BaseEntityDescriptor = {
    store: TestEntityStore,
    attrDescriptors: [checkboxConfig, labelConfig],
    entityName: 'TestEntity',
    entityTitle: 'Test Entity',
  };
  const runtimeConfigMock = { TEST_SERVICE_ROOT: 'http://localhost:4200/services/generic-message/api/v1' };

  setupMockService();

  await TestBed.configureTestingModule({
    imports: [componentType, NoopAnimationsModule],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([
        { path: 'test-entity/:id/details', component: DummyComponent },
        { path: 'test-entity/list', component: DummyComponent },
      ]),
      LayoutService,
      { provide: TestEntityStore, useClass: TestEntityStore, deps: [TestEntityService] },
      { provide: BreakpointObserver, useClass: MockBreakpointObserver },
      { provide: TestEntityService, useValue: mockService },
      { provide: RUNTIME_CONFIGURATION, useValue: runtimeConfigMock },
    ],
  }).compileComponents();

  const router = await RouterTestingHarness.create('test-entity/list');
  const fixture = TestBed.createComponent(componentType);
  const store = TestBed.inject(TestEntityStore);
  const breakpointObserver = TestBed.inject(BreakpointObserver) as unknown as MockBreakpointObserver;
  const component = fixture.componentInstance;
  entityDescriptor.store = store;
  store.load({});
  component.baseEntityListOptions = signal<BaseEntityDescriptor>(entityDescriptor) as unknown as InputSignal<BaseEntityDescriptor>;
  breakpointObserver.resize(1280);
  fixture.detectChanges();

  return { fixture, component, store, router, breakpointObserver };
}
