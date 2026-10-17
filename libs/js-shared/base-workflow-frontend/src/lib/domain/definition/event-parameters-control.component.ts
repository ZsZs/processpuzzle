import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity, BaseEntityAttrDescriptor, BaseFormControlComponent, EntityLabelPipe, FormControlType } from '@processpuzzle/base-entity';
import { WORKFLOW_EVENT_USE_I18N_SCOPE } from '../../base-workflow.i18n';
import { EVENT_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

/** Key root of the control's own words; shared by the start event and the intermediate event forms. */
export const EVENT_PARAMETERS_I18N_SCOPE = `${WORKFLOW_EVENT_USE_I18N_SCOPE}.event_parameters`;

/** What the control needs of a catalog event, read structurally: base-workflow does not depend on base-event. */
interface CatalogEvent {
  id?: string;
  kind?: string;
  subjectType?: string;
  parameters?: { name: string; type: string; entityType?: string; required?: boolean }[];
}

interface CatalogEventStore {
  entities?: () => CatalogEvent[];
}

/** One row of the list: a parameter, and whether the form's payload mapping overrides it. */
export interface ShownParameter {
  name: string;
  type: string;
  required: boolean;
  /** The implicit `subject` of a SYSTEM event: it binds its artifact rather than landing by name. */
  implicit: boolean;
  mapped: boolean;
}

/**
 * The parameters of the event a start event or an intermediate event names, read-only, next to its payload
 * mapping (docs/event-parameters.md, phase 3). `eventAttrName` is the sibling attribute holding the event id —
 * `eventType` on a start event, `eventDefinitionId` on an event use. Disabled, so the form never saves it.
 */
export class EventParametersAttrDescriptor extends BaseEntityAttrDescriptor {
  constructor(
    readonly eventAttrName: string,
    readonly mappingAttrName = 'payloadMapping',
  ) {
    super('eventParameters', FormControlType.CUSTOM, 'Event Parameters');
    this.component = EventParametersControlComponent;
    this.linkedEntityType = EVENT_DEFINITION_ENTITY_NAME;
    this.disabled = true;
    this.hideInTable = true;
  }
}

/**
 * Lists the selected catalog event's parameters, each marked *by name* — filled without a mapping — or *mapped*
 * when the payload mapping names it. Follows the event and the mapping as they are edited.
 *
 * The catalog is read through `BaseEntityDescriptorRegistry` by entity name, as the event's own `FOREIGN_KEY`
 * reads it for its label, so the list fills in once base-event's store has loaded. Without base-event in the
 * application, or for an event not in the catalog, it says nothing rather than claiming there are no parameters.
 */
@Component({
  selector: 'pp-event-parameters-control',
  standalone: true,
  imports: [EntityLabelPipe, TranslocoPipe],
  template: `
    @if (event(); as catalogEvent) {
      <div class="base-entity-form-field pp-event-parameters">
        <span class="pp-event-parameters__label">{{ config().i18nKey() | ppLabel: config().label }}</span>
        @if (parameters().length === 0) {
          <p class="pp-event-parameters__none" data-testid="event-parameters-none">{{ scope + '.none' | transloco }}</p>
        } @else {
          <ul class="pp-event-parameters__list" data-testid="event-parameters-list">
            @for (parameter of parameters(); track parameter.name) {
              <li [attr.data-testid]="'event-parameter-' + parameter.name">
                <code>{{ parameter.name }}</code>
                <span class="pp-event-parameters__type">{{ parameter.type }}</span>
                @if (parameter.required) {
                  <span class="pp-event-parameters__required">{{ scope + '.required' | transloco }}</span>
                }
                <span class="pp-event-parameters__fill">{{ scope + '.' + fillOf(parameter) | transloco }}</span>
              </li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: [
    `
      .pp-event-parameters {
        border: 1px solid var(--mat-sys-outline-variant);
        border-radius: 4px;
        display: flex;
        flex-direction: column;
        gap: 4px;
        padding: 8px 12px;
      }
      .pp-event-parameters__label {
        font: var(--mat-sys-label-large);
      }
      .pp-event-parameters__list {
        margin: 0;
        padding-left: 16px;
      }
      .pp-event-parameters__list li {
        display: flex;
        gap: 8px;
        align-items: baseline;
      }
      .pp-event-parameters__type,
      .pp-event-parameters__fill {
        opacity: 0.7;
        font-size: 12px;
      }
      .pp-event-parameters__required {
        font-size: 12px;
        font-weight: 500;
      }
      .pp-event-parameters__none {
        margin: 0;
        font-style: italic;
        opacity: 0.7;
      }
    `,
  ],
})
export class EventParametersControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  protected readonly scope = EVENT_PARAMETERS_I18N_SCOPE;
  private readonly destroyRef = inject(DestroyRef);
  private readonly eventId = signal<string | undefined>(undefined);
  private readonly mapping = signal<Record<string, unknown>>({});

  protected readonly event = computed<CatalogEvent | undefined>(() => {
    const id = this.eventId();
    if (!id) return undefined;
    const store = this.descriptorRegistry.getStore<CatalogEventStore>(EVENT_DEFINITION_ENTITY_NAME);
    return store?.entities?.().find((event) => event.id === id);
  });

  readonly parameters = computed<ShownParameter[]>(() => {
    const event = this.event();
    if (!event) return [];
    const mapped = this.mapping();
    const declared = (event.parameters ?? []).map((parameter) => ({
      name: parameter.name,
      type: parameter.entityType ? `${parameter.type} ${parameter.entityType}` : parameter.type,
      required: !!parameter.required,
      implicit: false,
      mapped: Object.hasOwn(mapped, parameter.name),
    }));
    if (event.kind !== 'SYSTEM' || declared.some((parameter) => parameter.name === 'subject')) return declared;
    const subjectType = event.subjectType ? `ENTITY_REF ${event.subjectType}` : 'ENTITY_REF';
    return [{ name: 'subject', type: subjectType, required: true, implicit: true, mapped: false }, ...declared];
  });

  ngOnInit(): void {
    const descriptor = this.config() as EventParametersAttrDescriptor;
    this.follow(descriptor.eventAttrName, (value) => this.eventId.set(typeof value === 'string' && value !== '' ? value : undefined));
    this.follow(descriptor.mappingAttrName, (value) => this.mapping.set(value && typeof value === 'object' ? (value as Record<string, unknown>) : {}));
  }

  protected fillOf(parameter: ShownParameter): string {
    if (parameter.implicit) return 'binds';
    return parameter.mapped ? 'mapped' : 'by_name';
  }

  private follow(attrName: string, apply: (value: unknown) => void): void {
    const control = this.formGroup?.get(attrName);
    if (!control) return;
    apply(control.value);
    control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(apply);
  }
}
