import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { BeanRef, PropertyMap, State, StateMachineDefinition, Transition } from './state-machine-definition';

/**
 * A state as it arrives. One spelling only: the contract, base-state-backend's own `State` record, the seed
 * YAML json-server serves verbatim and the persisted JSON all name the flags `isFinal` / `isLocked`. This
 * interface used to admit a second, `terminal` / `locked` spelling for the contract; that split is gone.
 */
interface StateDto {
  key?: string;
  name?: string;
  description?: string;
  isFinal?: boolean;
  isLocked?: boolean;
  metadata?: PropertyMap;
}

interface BeanRefDto {
  beanName?: string;
  params?: PropertyMap;
}

interface TransitionDto {
  key?: string;
  name?: string;
  sourceStateKey?: string;
  targetStateKey?: string;
  triggerKey?: string;
  guards?: BeanRefDto[];
  actions?: BeanRefDto[];
}

interface StateMachineDefinitionDto {
  id?: string;
  entityName?: string;
  name?: string;
  description?: string;
  stateAttributeKey?: string;
  initialStateKey?: string;
  states?: StateDto[];
  transitions?: TransitionDto[];
  orgKey?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between the `StateMachineDefinition` DTO of `base-state-api.yaml` and the entity the
 * generated screens work with.
 *
 * Three things are worth knowing about it.
 *
 * `id` is a **mirror of `entityName`** in both directions. The contract addresses a definition by
 * `entityName` and gives it no key of its own, while the generic screens address a record by `id` — so
 * the mapper is where the two meet, rather than every caller. `toDto` sends both: `entityName` because
 * that is the contract's field, `id` because json-server keys a record by it and would otherwise invent
 * one on create. The Spring backend ignores the extra field (unknown properties are not an error there)
 * and treats the path segment as the source of truth, exactly as the contract says.
 *
 * The nested rows are **mapped element by element**, not passed through as `AppDefinition`'s `regions`
 * are. An embedded row is edited as the parsed JSON it arrived as, so a field the wire spells differently
 * from the model would leave its control empty and silently drop the value on the next save. Mapping each
 * row is what keeps that class of bug out of every descriptor, even now that no field is spelled two ways.
 *
 * `PUT /state-machines/{entityName}` is a **full replacement**, so `toDto` emits `states` and
 * `transitions` unconditionally — an absent list is an emptied machine, not an untouched one.
 */
@Injectable({ providedIn: 'root' })
export class StateMachineDefinitionMapper implements BaseEntityMapper<StateMachineDefinition> {
  fromDto(dto: unknown): StateMachineDefinition {
    const source = dto as StateMachineDefinitionDto;
    const entityName = source.entityName ?? source.id ?? '';
    return new StateMachineDefinition({
      id: entityName,
      entityName,
      name: source.name,
      description: source.description,
      stateAttributeKey: source.stateAttributeKey,
      initialStateKey: source.initialStateKey,
      states: (source.states ?? []).map(toState),
      transitions: (source.transitions ?? []).map(toTransition),
      orgKey: source.orgKey,
      version: source.version,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: StateMachineDefinition): StateMachineDefinitionDto {
    // Listed field by field rather than spread, so a control the form may gain later cannot leak into
    // the payload unnoticed.
    const entityName = entity.entityName || (entity.id ?? '');
    return {
      id: entityName,
      entityName,
      name: entity.name,
      description: entity.description,
      stateAttributeKey: entity.stateAttributeKey,
      initialStateKey: entity.initialStateKey,
      states: (entity.states ?? []).map(fromState),
      transitions: (entity.transitions ?? []).map(fromTransition),
      orgKey: entity.orgKey,
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}

// region private helper functions
function toState(dto: StateDto): State {
  return new State({
    key: dto.key,
    name: dto.name,
    description: dto.description,
    isFinal: dto.isFinal,
    isLocked: dto.isLocked,
    metadata: dto.metadata,
  });
}

/**
 * Both flags are written explicitly rather than left off when false: `PUT /state-machines/{entityName}` is a
 * full replacement, so an absent flag is an unset one, and the form's unticked checkbox has to say so.
 */
function fromState(state: State): StateDto {
  return {
    key: state.key,
    name: state.name,
    description: state.description,
    isFinal: state.isFinal ?? false,
    isLocked: state.isLocked ?? false,
    metadata: state.metadata,
  };
}

function toTransition(dto: TransitionDto): Transition {
  return new Transition({
    key: dto.key,
    name: dto.name,
    sourceStateKey: dto.sourceStateKey,
    targetStateKey: dto.targetStateKey,
    triggerKey: dto.triggerKey,
    guards: (dto.guards ?? []).map(toBeanRef),
    actions: (dto.actions ?? []).map(toBeanRef),
  });
}

function fromTransition(transition: Transition): TransitionDto {
  return {
    key: transition.key,
    name: transition.name,
    sourceStateKey: transition.sourceStateKey,
    targetStateKey: transition.targetStateKey,
    triggerKey: transition.triggerKey,
    guards: (transition.guards ?? []).map(fromBeanRef),
    actions: (transition.actions ?? []).map(fromBeanRef),
  };
}

function toBeanRef(dto: BeanRefDto): BeanRef {
  return new BeanRef({ beanName: dto.beanName, params: dto.params });
}

function fromBeanRef(beanRef: BeanRef): BeanRefDto {
  return { beanName: beanRef.beanName, params: beanRef.params };
}
// endregion
