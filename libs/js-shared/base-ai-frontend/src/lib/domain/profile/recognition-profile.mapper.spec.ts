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
});
