import { describe, expect, it } from 'vitest';
import { serviceRootOf } from './service-root';

describe('serviceRootOf', () => {
  const configuration = {
    BASE_CONFIGURATION: {
      APP_SERVICE_ROOT: 'http://localhost:8080/organizations/acme',
      ENTITY_SERVICE_ROOT: 'http://entities.acme/organizations/acme',
    },
  };

  it('returns the feature root when the configuration names one.', () => {
    expect(serviceRootOf(configuration, 'ENTITY_SERVICE_ROOT')).toBe('http://entities.acme/organizations/acme');
  });

  it('falls back to APP_SERVICE_ROOT for a feature root the configuration leaves out.', () => {
    expect(serviceRootOf(configuration, 'WIDGET_SERVICE_ROOT')).toBe('http://localhost:8080/organizations/acme');
  });

  it('returns an empty string when there is no configuration, so the caller can skip the request.', () => {
    expect(serviceRootOf(undefined, 'ENTITY_SERVICE_ROOT')).toBe('');
    expect(serviceRootOf(null, 'ENTITY_SERVICE_ROOT')).toBe('');
  });

  it('returns an empty string when the configuration carries no BASE_CONFIGURATION.', () => {
    expect(serviceRootOf({ LOGGING_CONFIGURATION: {} }, 'ENTITY_SERVICE_ROOT')).toBe('');
  });

  it('returns an empty string when neither the feature root nor APP_SERVICE_ROOT is set.', () => {
    expect(serviceRootOf({ BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: 'http://localhost:3000' } }, 'ENTITY_SERVICE_ROOT')).toBe('');
  });
});
