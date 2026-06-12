import { TestBed } from '@angular/core/testing';
import { ConfigurationService } from '../runtime-configuration/configuration.service';
import { TestConfiguration } from './test-configuration';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestEnvironmentVariables } from './test-environment-variables';

type MockResponse = object | { $status: number; $error: string } | (() => object);

export function mockFetchByUrl(routes: Record<string, MockResponse>): void {
  vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
    const url = input.toString();

    const match = Object.entries(routes).find(([pattern]) => url.includes(pattern));

    if (!match) {
      return Promise.resolve({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ error: `No mock for: ${url}` }),
      } as Response);
    }

    const [, response] = match;

    // Error response
    if (typeof response === 'object' && '$status' in response) {
      return Promise.resolve({
        ok: false,
        status: response.$status,
        json: () => Promise.resolve({ error: response.$error }),
      } as Response);
    }

    // Dynamic factory
    const data = typeof response === 'function' ? response() : response;
    return Promise.resolve({
      ok: true,
      json: () => Promise.resolve(data),
    } as Response);
  });
}

describe('ConfigurationService', () => {
  let configService: ConfigurationService<TestEnvironmentVariables, TestConfiguration>;
  const defaultEnvironmentVars: TestEnvironmentVariables = {
    PIPELINE_STAGE: 'dev',
    CONFIGURATION_OVERRIDES: ['environments/config.common.json', 'environments/config.dev.json'],
  };
  const noOverridesEnvironmentVars: TestEnvironmentVariables = {
    PIPELINE_STAGE: 'dev',
    CONFIGURATION_OVERRIDES: [],
  };
  const commonConfig: TestConfiguration = {
    LANGUAGE: 'de',
    ICS_BACKEND_ROOT: 'http://localhost:8080/services/ics',
    MESSAGE_SERVICE_ROOT: 'http://localhost:0080/services/message-service',
  };
  const devConfig: TestConfiguration = {
    ICS_BACKEND_ROOT: 'https://t1-ics.brz.gv.at/services/ics',
    MESSAGE_SERVICE_ROOT: 'https://t1-ms.brz.gv.at/services/message-service',
  };
  const mergedConfig: TestConfiguration = {
    LANGUAGE: 'de',
    ICS_BACKEND_ROOT: 'https://t1-ics.brz.gv.at/services/ics',
    MESSAGE_SERVICE_ROOT: 'https://t1-ms.brz.gv.at/services/message-service',
  };

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    TestBed.configureTestingModule({});
    configService = TestBed.inject(ConfigurationService<TestEnvironmentVariables, TestConfiguration>);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should create', (): void => {
    expect(configService).toBeTruthy();
  });

  it('init(), if no override URL defined, retrieves config.common.json and config.dev.json.', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': devConfig });

    const runtimeConfig = await configService.init(noOverridesEnvironmentVars);
    expect(runtimeConfig).toEqual(mergedConfig);
  });

  it('init() retrieves all configuration URLs and merges them.', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': devConfig });

    await configService.init(defaultEnvironmentVars);
    expect(configService.configuration).toEqual(mergedConfig);
  });

  it('init(), when configuration doesnt exist, logs it', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': {} });

    await configService.init(defaultEnvironmentVars);
    expect(configService.configuration).toEqual(commonConfig);
  });

  it('init(), if URL starts with / ignores it.', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': {} });

    const faultyOverrides = { ...noOverridesEnvironmentVars, ...{ CONFIGURATION_OVERRIDES: ['/assets/config.x.json'] } };
    await configService.init(faultyOverrides);
    expect(configService.configuration).toEqual(commonConfig);
  });

  it('init(), if URL starts with // or http:// or https:// uses it directly, without extending with host.', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': {} });

    const faultyOverrides = { ...noOverridesEnvironmentVars, ...{ CONFIGURATION_OVERRIDES: ['https://assets/config.x.json'] } };
    await configService.init(faultyOverrides);
    expect(configService.configuration).toEqual(commonConfig);
  });

  it.skip('init(), if URL doesnt exist, throws an error.', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': { $status: 503, $error: 'Configuration unavailable' } });

    await configService.init(noOverridesEnvironmentVars);
    expect(configService.configuration).toEqual(commonConfig);
  });
});
