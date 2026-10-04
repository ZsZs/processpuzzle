import { describe, expect, it } from 'vitest';
import { MATCHING_DEFAULTS, RecognitionProfile } from './recognition-profile';
import { RecognitionProfileMapper } from './recognition-profile.mapper';

describe('RecognitionProfileMapper', () => {
  const mapper = new RecognitionProfileMapper();

  it('flattens the matching settings and mirrors entityName into id', () => {
    const profile = mapper.fromDto({
      entityName: 'boat',
      name: 'Sailboat by sail number',
      detectorClass: 'boat',
      identifierAttributeKey: 'sailNumber',
      matching: { identifierWeight: 0.7, acceptScore: 0.8, acceptMargin: 0.15, sampleFps: 2 },
      version: 3,
    });

    expect(profile.id).toBe('boat');
    expect(profile.identifierWeight).toBe(0.7);
    expect(profile.sampleFps).toBe(2);
    expect(profile.version).toBe(3);
  });

  it('falls back to the contract defaults when matching is absent', () => {
    const profile = mapper.fromDto({ entityName: 'boat' });

    expect(profile.acceptScore).toBe(MATCHING_DEFAULTS.acceptScore);
    expect(profile.acceptMargin).toBe(MATCHING_DEFAULTS.acceptMargin);
  });

  it('nests the matching settings again, converting the strings a text box hands back', () => {
    const profile = new RecognitionProfile({ entityName: 'boat', name: 'n', detectorClass: 'boat' });
    Object.assign(profile, { acceptScore: '0,9', sampleFps: '', identifierPattern: '  ' });

    const dto = mapper.toDto(profile);

    expect(dto.id).toBe('boat');
    expect(dto.matching).toEqual({ identifierWeight: 0.6, acceptScore: 0.9, acceptMargin: 0.1, sampleFps: MATCHING_DEFAULTS.sampleFps });
    expect(dto.identifierPattern).toBeUndefined();
  });

  it('uses the legacy id when entityName is missing, and defaults an empty DTO', () => {
    expect(mapper.fromDto({ id: 'boat' })).toMatchObject({ id: 'boat', entityName: 'boat' });
    expect(mapper.fromDto({})).toEqual(new RecognitionProfile());
    expect(mapper.toDto(new RecognitionProfile({ id: 'boat' }))).toMatchObject({ id: 'boat', entityName: 'boat' });
    const profile = new RecognitionProfile();
    Object.assign(profile, { id: undefined });
    expect(mapper.toDto(profile)).toMatchObject({ id: '', entityName: '' });
  });

  it('preserves all editable fields and server metadata through a round trip', () => {
    const dto = {
      id: 'boat',
      entityName: 'boat',
      name: 'Sailboats',
      description: ' Identify sailboats ',
      detectorClass: 'boat',
      identifierAttributeKey: 'sailNumber',
      identifierPattern: '^[A-Z]{3}[0-9]+$',
      matching: { identifierWeight: 0, acceptScore: 0.8, acceptMargin: 0.2, sampleFps: 1 },
      orgKey: 'testbed',
      version: 4,
      createdAt: '2026-10-01T12:00:00Z',
      updatedAt: '2026-10-02T12:00:00Z',
    };

    expect(mapper.toDto(mapper.fromDto(dto))).toEqual(dto);
  });

  it.each([null, undefined, '', 'invalid', Number.NaN, Number.POSITIVE_INFINITY, 'Infinity', {}, [], true])('defaults invalid matching input %j without coercing objects or booleans', (value) => {
    const profile = new RecognitionProfile();
    Object.assign(profile, { identifierWeight: value, acceptScore: value, acceptMargin: value, sampleFps: value });

    expect(mapper.toDto(profile).matching).toEqual(MATCHING_DEFAULTS);
  });

  it('does not stringify an arbitrary object supplied by a form', () => {
    const profile = new RecognitionProfile();
    Object.assign(profile, {
      sampleFps: {
        toString() {
          throw new Error('must not stringify');
        },
      },
    });

    expect(mapper.toDto(profile).matching?.sampleFps).toBe(MATCHING_DEFAULTS.sampleFps);
  });
});
