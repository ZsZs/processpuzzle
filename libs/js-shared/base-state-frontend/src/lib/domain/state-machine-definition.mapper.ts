import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { BeanRef, PropertyMap, State, StateMachineDefinition, Transition } from './state-machine-definition';

/**
 * A state as it may arrive: the contract's `terminal` / `locked`, or the `isFinal` / `isLocked` of
 * base-state-backend's own `State` record — which is what the seed YAML spells and json-server therefore
 * serves. Both are optional, so neither reader may assume the other's spelling is absent.
 */
interface StateDto {
  key?: string;
  name?: string;
  description?: string;
  terminal?: boolean;
  locked?: boolean;
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
 * are. An embedded row is edited as the parsed JSON it arrived as, so a state whose flag is called
 * `isFinal` on the wire would leave the `terminal` control of the state form empty and silently lose the
 * flag on the next save. Normalizing here is what keeps the two spellings out of every descriptor.
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
    terminal: dto.terminal ?? dto.isFinal,
    locked: dto.locked ?? dto.isLocked,
    metadata: dto.metadata,
  });
}

/**
 * Writes the contract's spelling only. The aliases are a *read* concern — they exist because the seed
 * YAML is deserialized by a Java record whose components are named `isFinal` / `isLocked` — and a payload
 * that carried both would leave two fields to disagree the next time one of them is edited.
 */
function fromState(state: State): StateDto {
  return {
    key: state.key,
    name: state.name,
    description: state.description,
    terminal: state.terminal ?? false,
    locked: state.locked ?? false,
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
