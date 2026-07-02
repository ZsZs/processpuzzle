import { BaseEntity } from '../base-entity/base-entity';
import { Component, computed, effect, inject, input, InputSignal, OnDestroy, OnInit, signal, Signal, untracked, ViewChild, WritableSignal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseFormHostDirective } from './base-form-host.directive';
import { MatCard, MatCardActions, MatCardContent } from '@angular/material/card';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { BaseEntityFormBuilder } from './base-entity-form.builder';
import { NGXLogger } from 'ngx-logging-kit';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { EvaluatableRule, RULE_ENGINE, RuleEvaluationResult } from '../rule-engine/rule-engine';
import { RuleViolationsSingletonStore } from '../rule-engine/rule-violations.store';
import { Subject, Subscription } from 'rxjs';

@Component({
  selector: 'base-form',
  standalone: true,
  templateUrl: 'base-entity-form.component.html',
  styleUrls: ['./base-entity-form.css'],
  imports: [BaseFormHostDirective, NgTemplateOutlet, ReactiveFormsModule, MatCard, MatCardContent, MatCardActions, MatButton, MatIcon],
  providers: [{ provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } }],
})
export class BaseEntityFormComponent<Entity extends BaseEntity> implements OnInit, OnDestroy {
  baseEntityForm!: FormGroup;
  entityDescriptor = inject(ROUTER_OUTLET_DATA) as Signal<BaseEntityDescriptor>;
  entity: Signal<Entity> = computed(() => (this.isNewObject() ? this.store().createEntity() : (this.store().loadById(this.entityId()) as Entity)));
  entityId: InputSignal<string> = input.required<string>();
  isAbstract = computed(() => this.entityDescriptor().isAbstract);
  isNewObject = computed(() => this.entityId() === BaseUrlSegments.NewEntity);
  store: Signal<BaseEntityStoreApi<Entity>> = computed(() => this.entityDescriptor().store as BaseEntityStoreApi<Entity>);
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) componentHost!: BaseFormHostDirective;

  violations: WritableSignal<RuleEvaluationResult[]> = signal([]);
  hasErrorViolations = computed(() => this.violations().some((v) => v.severity === 'ERROR'));

  private readonly entityFormBuilder = inject(BaseEntityFormBuilder<Entity>);
  private readonly formBuilder = inject(FormBuilder);
  protected readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly logger = inject(NGXLogger);
  private readonly ruleEngine = inject(RULE_ENGINE, { optional: true });
  private readonly violationsStore = inject(RuleViolationsSingletonStore);
  private rules: EvaluatableRule[] = [];
  private readonly fieldsWithRuleErrors = new Set<string>();
  private readonly ruleLoad$ = new Subject<void>();
  private ruleSubscription: Subscription | undefined;

  constructor() {
    this.registerEffects();
  }

  // region angular lifecycle hooks
  ngOnInit(): void {
    this.formNavigator.setEntityName(this.entityDescriptor().entityName);
    this.formNavigator.determineActiveRouteSegment();
    this.baseEntityForm = this.formBuilder.group({});
    this.logger.info('BaseEntityFormComponent initialized with: ', { entityDescriptor: this.entityDescriptor() });
    this.loadRules();
  }

  ngOnDestroy(): void {
    this.ruleSubscription?.unsubscribe();
    this.ruleLoad$.complete();
    this.clearFieldRuleErrors();
    this.violationsStore.clearViolations();
  }
  // endregion

  // region event handlers
  async onCancel() {
    this.store().setCurrentEntity(undefined);
    await this.formNavigator.navigateBack();
  }

  async onDelete() {
    await this.store().delete(this.entityId());
    await this.formNavigator.navigateBack();
  }

  onFieldBlur(): void {
    this.evaluateRules();
  }

  async onSubmit() {
    const objectToSave = { ...this.entity(), ...this.baseEntityForm.value };
    this.evaluateRules(objectToSave);
    if (this.hasErrorViolations()) return;
    this.store().setCurrentEntity(undefined);
    if (this.isNewObject()) {
      await this.store().add(objectToSave);
    } else {
      await this.store().update(objectToSave);
    }
    await this.formNavigator.navigateBack();
  }
  // endregion

  // region protected, private helper methods
  private evaluateRules(candidate?: Entity | Record<string, unknown>): void {
    if (!this.ruleEngine || this.rules.length === 0) return;
    const target = candidate ?? { ...this.entity(), ...this.baseEntityForm.value };
    const results = this.rules.map((rule) => this.ruleEngine!.evaluate(target, rule));
    const failing = results.filter((r) => !r.passed);
    this.violations.set(failing);
    this.violationsStore.setViolations(this.entityDescriptor().entityName, failing);
    this.applyFieldRuleErrors(failing);
  }

  private applyFieldRuleErrors(violations: RuleEvaluationResult[]): void {
    this.clearFieldRuleErrors();
    const perField = new Map<string, RuleEvaluationResult[]>();
    for (const violation of violations) {
      for (const field of violation.fields ?? []) {
        const list = perField.get(field) ?? [];
        list.push(violation);
        perField.set(field, list);
      }
    }
    for (const [field, list] of perField) {
      const control = this.baseEntityForm.get(field);
      if (!control) continue;
      const message = list.map((v) => v.message ?? v.error ?? v.ruleId).join('\n');
      const severity = list.some((v) => v.severity === 'ERROR') ? 'ERROR' : list[0].severity;
      const existing = control.errors ?? {};
      control.setErrors({ ...existing, ruleViolation: { message, severity } });
      control.markAsTouched();
      this.fieldsWithRuleErrors.add(field);
    }
  }

  private clearFieldRuleErrors(): void {
    for (const field of this.fieldsWithRuleErrors) {
      const control = this.baseEntityForm.get(field);
      if (!control) continue;
      const errors = { ...(control.errors ?? {}) };
      delete errors['ruleViolation'];
      control.setErrors(Object.keys(errors).length ? errors : null);
    }
    this.fieldsWithRuleErrors.clear();
  }

  private loadRules(): void {
    if (!this.ruleEngine) return;
    const context = this.entityDescriptor().entityName;
    this.ruleSubscription = this.ruleEngine.loadRulesFor(context).subscribe({
      next: (rules) => {
        this.rules = rules;
        this.logger.info(`BaseEntityFormComponent loaded ${rules.length} rule(s) for context "${context}"`);
      },
      error: (err) => this.logger.warn(`BaseEntityFormComponent failed to load rules for "${context}"`, err),
    });
  }

  private registerEffects(): void {
    effect(() => {
      if (this.entityId() && this.entityDescriptor() && this.entity())
        untracked(() => {
          const snapshot = this.formNavigator.popFormSnapshot();
          this.entityFormBuilder.buildForm(this.componentHost.viewContainerRef, this.baseEntityForm, this.store(), this.entityDescriptor().attrDescriptors, this.entity, this.entityDescriptor().entityName, snapshot);
        });
    });

    effect(() => {
      if (this.entity()) this.store().setCurrentEntity(this.entity().id);
    });
  }
  // endregion
}
