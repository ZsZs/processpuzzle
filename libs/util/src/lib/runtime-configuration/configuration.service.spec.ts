import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { CONFIGURATION_OPTIONS, CONFIGURATION_TYPE, RUNTIME_CONFIGURATION } from '../runtime-configuration/configuration.injection-tokens';
import { ConfigurationService } from '../runtime-configuration/configuration.service';
import { ConfigurationOptions } from '../runtime-configuration/configuration.options';
import { TestConfiguration } from './test-configuration';

describe('ConfigurationService', () => {
  let configService: ConfigurationService<TestConfiguration>;
  let httpTestingController: HttpTestingController;
  const commonConfig: TestConfiguration = {
    LANGUAGE: 'de',
    ICS_BACKEND_ROOT: 'http://localhost:8080/services/ics',
    MESSAGE_SERVICE_ROOT: 'http://localhost:0080/services/message-service',
  };
  const t1Config: TestConfiguration = {
    ICS_BACKEND_ROOT: 'https://t1-ics.brz.gv.at/services/ics',
    MESSAGE_SERVICE_ROOT: 'https://t1-ms.brz.gv.at/services/message-service',
  };
  const undefinedConfig: TestConfiguration = {};
  const expectedConfig: TestConfiguration = {
    LANGUAGE: 'de',
    ICS_BACKEND_ROOT: 'https://t1-ics.brz.gv.at/services/ics',
    MESSAGE_SERVICE_ROOT: 'https://t1-ms.brz.gv.at/services/message-service',
  };

  const setUpConfigurationOptions = (options: ConfigurationOptions) => {
    TestBed.overrideProvider(CONFIGURATION_OPTIONS, { useValue: options });
    configService = TestBed.inject(ConfigurationService);
    httpTestingController = TestBed.inject(HttpTestingController);
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [],
      providers: [
        ConfigurationService,
        { provide: CONFIGURATION_TYPE, useValue: TestConfiguration },
        { provide: CONFIGURATION_OPTIONS, useValue: { urlFactory: () => ['environments/config.common.json', 'environments/config.t1.json'], log: true } },
        { provide: RUNTIME_CONFIGURATION, useFactory: (configurationService: ConfigurationService<TestConfiguration>) => configurationService.configuration, deps: [ConfigurationService] },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should create', (): void => {
    configService = TestBed.inject(ConfigurationService);
    httpTestingController = TestBed.inject(HttpTestingController);
    expect(configService).toBeTruthy();
  });

  it('init(), if no URL defined, retrieves config.common.json.', () => {
    // SETUP
    setUpConfigurationOptions({ urlFactory: undefined, log: false });

    // EXERCISE
    configService.init().then(() => expect(configService.configuration).toEqual(commonConfig));

    // VERIFY
    const mockRequest: TestRequest = httpTestingController.expectOne({ method: 'GET', url: 'http://localhost/config.common.json' });
    mockRequest.flush(commonConfig);
  });

  it('init() retrieves the configurations from the URLs in factory.', () => {
    // SETUP
    setUpConfigurationOptions({ urlFactory: () => ['environments/config.common.json'], log: false });

    // EXERCISE
    configService.init().then(() => expect(configService.configuration).toEqual(commonConfig));

    // VERIFY
    const mockRequest: TestRequest = httpTestingController.expectOne({ method: 'GET', url: 'http://localhost/environments/config.common.json' });
    mockRequest.flush(commonConfig);
  });

  it('init() retrieves all configuration URLs and merges them.', () => {
    // SETUP
    configService = TestBed.inject(ConfigurationService);
    httpTestingController = TestBed.inject(HttpTestingController);

    // EXERCISE
    configService.init().then(() => expect(configService.configuration).toEqual(expectedConfig));

    // VERIFY
    const mockRequests1: TestRequest = httpTestingController.expectOne('http://localhost/environments/config.common.json');
    const mockRequests2: TestRequest = httpTestingController.expectOne('http://localhost/environments/config.t1.json');
    mockRequests1.flush(commonConfig);
    mockRequests2.flush(t1Config);
  });

  it('init(), when configuration doesnt exist, logs it', () => {
    // SETUP
    setUpConfigurationOptions({ urlFactory: () => ['environments/config.common.json', 'undefined.json'], log: true });

    // EXERCISE
    configService.init().then(() => expect(configService.configuration).toEqual(commonConfig));

    // VERIFY
    const mockRequests1: TestRequest = httpTestingController.expectOne('http://localhost/environments/config.common.json');
    const mockRequests2: TestRequest = httpTestingController.expectOne('http://localhost/undefined.json');
    mockRequests1.flush(commonConfig);
    mockRequests2.flush(undefinedConfig);
  });

  it('init(), if URL starts with / ignores it.', () => {
    // SETUP
    setUpConfigurationOptions({ urlFactory: () => ['/environments/config.common.json'], log: false });

    // EXERCISE
    configService.init().then(() => expect(configService.configuration).toEqual(commonConfig));

    // VERIFY
    const mockRequest: TestRequest = httpTestingController.expectOne({ method: 'GET', url: 'http://localhost/environments/config.common.json' });
    mockRequest.flush(commonConfig);
  });

  it('init(), if URL starts with // or http:// or https:// uses it directly, without extending with host.', () => {
    // SETUP
    setUpConfigurationOptions({ urlFactory: () => ['https://config.common.json'], log: false });

    // EXERCISE
    configService.init().then(() => expect(configService.configuration).toEqual(commonConfig));

    // VERIFY
    const mockRequest: TestRequest = httpTestingController.expectOne({ method: 'GET', url: 'https://config.common.json' });
    mockRequest.flush(commonConfig);
  });

  it('init(), if URL doesnt exist, throws an error.', () => {
    // SETUP
    setUpConfigurationOptions({ urlFactory: () => Promise.resolve(''), log: true });

    // EXERCISE, VERIFY
    configService.init().catch((error: Error) => expect(error.message).toContain('Runtime configuration:undefined load failed'));
  });
});
