import { Component, computed, effect, inject, input, signal, untracked, viewChild, ViewContainerRef } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { type BaseEntity, BaseEntityFormBuilder, DynamicEntityRegistry, type ResolvedDynamicEntity } from '@processpuzzle/base-entity';

/**
 * The generated form of one object, in place — an order on an invoice, a customer on a letter. It is the same
 * form the entity's own Details screen builds from its descriptor, minus the card, the actions and the
 * navigation: the widget shows the object it is given and never saves it, so there is no Save, Cancel or Delete.
 *
 * `entityName` picks the descriptor; `entity` is the object, usually bound to an `ENTITY_REF` port of the
 * hosting document. `readOnly` disables every control, which is what a printed or signed page wants.
 */
@Component({
  selector: 'pp-entity-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  providers: [{ provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } }],
  template: `
    @if (unresolved()) {
      <p class="pp-entity-form__hint" data-testid="entity-form-unresolved">No entity type '{{ entityName() }}' is registered.</p>
    }
    <form [formGroup]="form" class="pp-entity-form" data-testid="entity-form">
      <ng-container #host />
    </form>
  `,
  styles: `
    :host {
      display: block;
    }
    .pp-entity-form__hint {
      font-style: italic;
      opacity: 0.7;
    }
  `,
})
export class EntityFormComponent {
  readonly entityName = input<string | undefined>(undefined);
  readonly readOnly = input(true);
  readonly entity = input<Record<string, unknown> | undefined>(undefined);

  private readonly registry = inject(DynamicEntityRegistry);
  private readonly formBuilder = inject(BaseEntityFormBuilder<BaseEntity>);
  private readonly host = viewChild.required('host', { read: ViewContainerRef });

  protected readonly form: FormGroup = inject(FormBuilder).group({});
  protected readonly unresolved = signal(false);
  private readonly resolved = signal<ResolvedDynamicEntity | undefined>(undefined);
  private readonly object = computed(() => this.entity() as BaseEntity | undefined);

  constructor() {
    effect(() => {
      const name = this.entityName();
      untracked(() => void this.resolve(name));
    });
    effect(() => {
      const resolved = this.resolved();
      const object = this.object();
      const readOnly = this.readOnly();
      if (!resolved || !object) return;
      untracked(() => this.build(resolved, object, readOnly));
    });
  }

  private async resolve(name: string | undefined): Promise<void> {
    const resolved = (await this.registry.resolve(name)) ?? undefined;
    this.resolved.set(resolved);
    this.unresolved.set(name !== undefined && resolved === undefined);
  }

  private build(resolved: ResolvedDynamicEntity, object: BaseEntity, readOnly: boolean): void {
    for (const name of Object.keys(this.form.controls)) this.form.removeControl(name);
    const store = resolved.descriptor.store as Parameters<BaseEntityFormBuilder<BaseEntity>['buildForm']>[2];
    this.formBuilder.buildForm(this.host(), this.form, store, resolved.descriptor.attrDescriptors, signal(object), resolved.entityName);
    if (readOnly) this.form.disable({ emitEvent: false });
  }
}
