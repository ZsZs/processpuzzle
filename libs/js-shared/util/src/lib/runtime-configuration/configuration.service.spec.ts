import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ConfigurationService } from '../runtime-configuration/configuration.service';
import { TestConfiguration } from './test-configuration';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestEnvironmentVariables } from './test-environment-variables';

type MockResponse = object | { $status: number; $error: string } | { $body: string; $contentType?: string } | (() => object);

// `text` and `headers` as well as `json`, because that is what the service reads: it parses the body
// itself so that an SPA fallback serving index.html is distinguishable from a deployed file that
// does not parse. A mock without them would be a mock of a server that cannot exist.
function mockResponse(ok: boolean, status: number, body: string, contentType: string): Response {
  return {
    ok,
    status,
    headers: { get: (name: string) => (name.toLowerCase() === 'content-type' ? contentType : null) } as unknown as Headers,
    text: () => Promise.resolve(body),
    json: () => Promise.resolve(JSON.parse(body)),
  } as Response;
}

export function mockFetchByUrl(routes: Record<string, MockResponse>): void {
  vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
    const url = input.toString();

    const match = Object.entries(routes).find(([pattern]) => url.includes(pattern));

    if (!match) {
      return Promise.resolve(mockResponse(false, 404, JSON.stringify({ error: `No mock for: ${url}` }), 'application/json'));
    }

    const [, response] = match;

    // Error response
    if (typeof response === 'object' && '$status' in response) {
      return Promise.resolve(mockResponse(false, response.$status, JSON.stringify({ error: response.$error }), 'application/json'));
    }

    // Raw body — an SPA fallback, an error page, or a malformed configuration file
    if (typeof response === 'object' && '$body' in response) {
      return Promise.resolve(mockResponse(true, 200, response.$body, response.$contentType ?? 'text/html'));
    }

    // Dynamic factory
    const data = typeof response === 'function' ? response() : response;
    return Promise.resolve(mockResponse(true, 200, JSON.stringify(data), 'application/json'));
  });
}

const SPA_FALLBACK_BODY = '<!doctype html><html><head><title>ProcessPuzzle</title></head><body><app-root></app-root></body></html>';

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
    // The console spies below would otherwise outlive their test and swallow the next one's output.
    vi.restoreAllMocks();
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

  it('init(), when a configuration URL fails, boots on the ones that did load and records the failure', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': { $status: 503, $error: 'Configuration unavailable' } });
    const consoleWarn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    await configService.init(noOverridesEnvironmentVars);

    expect(configService.configuration).toEqual(commonConfig);
    expect(configService.unusableConfigurationUrls).toEqual([expect.stringContaining('config.dev.json')]);
    expect(consoleWarn).toHaveBeenCalledWith(expect.stringContaining('answered HTTP 503'));
  });

  // The stage incident of 2026-09-16: `PIPELINE_STAGE` named a configuration the application did not
  // ship, nginx's `try_files $uri $uri/ /index.html` answered it 200 with index.html, and the
  // `response.json()` of the day threw — so `init()` rejected, `bootstrapApplication` never ran and
  // the deployed site rendered nothing, with the container healthcheck still green.
  it('init(), when an SPA fallback answers a missing configuration with index.html, does not reject', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': { $body: SPA_FALLBACK_BODY } });
    const consoleWarn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    await configService.init(noOverridesEnvironmentVars);

    expect(configService.configuration).toEqual(commonConfig);
    expect(configService.unusableConfigurationUrls).toEqual([expect.stringContaining('config.dev.json')]);
    expect(consoleWarn).toHaveBeenCalledWith(expect.stringContaining('answered text/html rather than JSON'));
  });

  it('init(), when a body does not parse and no content-type explains it, treats it as absent', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': { $body: 'not json', $contentType: '' } });
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    await configService.init(noOverridesEnvironmentVars);

    expect(configService.configuration).toEqual(commonConfig);
  });

  it('init(), when an empty body is served, treats it as absent', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': { $body: '', $contentType: 'application/json' } });
    const consoleWarn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    await configService.init(noOverridesEnvironmentVars);

    expect(configService.configuration).toEqual(commonConfig);
    expect(consoleWarn).toHaveBeenCalledWith(expect.stringContaining('empty body'));
  });

  // The opposite case, and the reason the content-type is consulted at all: this file IS deployed and
  // IS broken. Booting on a silently incomplete configuration would hide it.
  it('init(), when a file served as JSON does not parse, rejects', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': { $body: '{ "LANGUAGE": "de",, }', $contentType: 'application/json' } });

    await expect(configService.init(noOverridesEnvironmentVars)).rejects.toMatchObject({
      message: expect.stringContaining('config.dev.json'),
    });
    expect(configService.unusableConfigurationUrls).toEqual([]);
  });

  it('init() leaves unusableConfigurationUrls empty when every configuration loads', async () => {
    mockFetchByUrl({ 'run-time-conf/config.common.json': commonConfig, 'run-time-conf/config.dev.json': devConfig });

    await configService.init(noOverridesEnvironmentVars);

    expect(configService.unusableConfigurationUrls).toEqual([]);
  });

  it('init() wraps HttpErrorResponse rejections into a descriptive Error with the original as cause', async () => {
    const httpError = new HttpErrorResponse({ status: 500, statusText: 'Server Error', url: '/config' });
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = input.toString();
      if (url.includes('config.dev.json')) {
        return Promise.reject(httpError);
      }
      return Promise.resolve(mockResponse(true, 200, JSON.stringify(commonConfig), 'application/json'));
    });
    const consoleLog = vi.spyOn(console, 'log').mockImplementation(() => undefined);

    await expect(configService.init(noOverridesEnvironmentVars)).rejects.toMatchObject({
      message: expect.stringContaining('Runtime configuration:'),
      cause: httpError,
    });
    expect(consoleLog).toHaveBeenCalledWith(expect.stringContaining('while fetching from:'));
  });

  it('init() wraps generic rejections with the underlying error message', async () => {
    const failure = new Error('network unreachable');
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = input.toString();
      if (url.includes('config.dev.json')) {
        return Promise.reject(failure);
      }
      return Promise.resolve(mockResponse(true, 200, JSON.stringify(commonConfig), 'application/json'));
    });

    await expect(configService.init(noOverridesEnvironmentVars)).rejects.toMatchObject({
      message: expect.stringContaining('network unreachable'),
      cause: failure,
    });
  });
});
