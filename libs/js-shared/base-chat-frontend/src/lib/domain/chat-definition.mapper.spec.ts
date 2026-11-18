import { describe, expect, it } from 'vitest';
import { ChatDefinition, ChatLifecycleKind, ChatMultiplicity } from './chat-definition';
import { ChatDefinitionMapper } from './chat-definition.mapper';

describe('ChatDefinitionMapper', () => {
  const mapper = new ChatDefinitionMapper();

  const dto = {
    key: 'order-crew',
    name: 'Crew chat',
    description: 'For the crew',
    subjectType: 'ENTITY',
    subjectDefinition: 'order',
    multiplicity: 'MANY',
    lifecycle: 'STATE_BOUND',
    writableStates: ['DRAFT'],
    readRoles: ['sailor'],
    writeRoles: ['captain'],
    createRoles: ['judge'],
    includeSubjectOwner: true,
    retentionDays: 30,
    version: 3,
    releaseState: 'CHANGED',
  };

  it('holds the contract key as the entity id', () => {
    const entity = mapper.fromDto(dto);

    expect(entity.id).toBe('order-crew');
    expect(entity.multiplicity).toBe(ChatMultiplicity.MANY);
    expect(entity.lifecycle).toBe(ChatLifecycleKind.STATE_BOUND);
    expect(entity.writableStates).toEqual(['DRAFT']);
    expect(entity.readRoles).toEqual(['sailor']);
    expect(entity.retentionDays).toBe(30);
    expect(entity.releaseState).toBe('CHANGED');
  });

  it('reads a bare definition with the defaults', () => {
    const entity = mapper.fromDto({ key: 'bare' });

    expect(entity).toEqual(new ChatDefinition({ id: 'bare' }));
    expect(entity.lifecycle).toBe(ChatLifecycleKind.ON_DEMAND);
    expect(entity.multiplicity).toBe(ChatMultiplicity.SINGLE);
  });

  it('writes the key back and never the release state', () => {
    const out = mapper.toDto(mapper.fromDto(dto));

    expect(out.key).toBe('order-crew');
    expect(out).not.toHaveProperty('releaseState');
    expect(out.version).toBe(3);
    expect(out.writableStates).toEqual(['DRAFT']);
  });

  it('drops the writable states of a chat that is not state-bound', () => {
    const entity = new ChatDefinition({ id: 'k', lifecycle: ChatLifecycleKind.ON_DEMAND, writableStates: ['DRAFT'] });

    expect(mapper.toDto(entity).writableStates).toEqual([]);
  });

  it('always sends the role lists, so that removing the last role replaces it', () => {
    const out = mapper.toDto(new ChatDefinition({ id: 'k' }));

    expect(out.readRoles).toEqual([]);
    expect(out.writeRoles).toEqual([]);
    expect(out.createRoles).toEqual([]);
    expect(out.includeSubjectOwner).toBe(false);
  });

  it('sends blank text and a non-positive retention as absent, and a typed number as a number', () => {
    const blank = mapper.toDto(new ChatDefinition({ id: 'k', description: '  ', retentionDays: 0 }));
    const typed = mapper.toDto(new ChatDefinition({ id: 'k', retentionDays: '14' as unknown as number }));
    const cleared = mapper.toDto(new ChatDefinition({ id: 'k', retentionDays: '' as unknown as number }));

    expect(blank.description).toBeUndefined();
    expect(blank.retentionDays).toBeUndefined();
    expect(typed.retentionDays).toBe(14);
    expect(cleared.retentionDays).toBeUndefined();
  });

  it('reads a null retention as absent', () => {
    expect(mapper.fromDto({ key: 'k', retentionDays: null }).retentionDays).toBeUndefined();
  });
});
