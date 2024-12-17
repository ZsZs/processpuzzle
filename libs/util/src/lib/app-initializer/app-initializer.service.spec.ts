import { AppInitializer } from './app-initializer.service';
import { CONFIGURATION_APP_INITIALIZER, CONFIGURATION_OPTIONS } from '../runtime-configuration/configuration.injection-tokens';
import { ConfigurationService } from '../runtime-configuration/configuration.service';
import { TestBed } from '@angular/core/testing';

describe('AppInitializerService', () => {
  let appInitializer: AppInitializer;
  const initializerOne = jest.fn(() => {
    console.log('Initializer one, is done.');
    Promise.resolve(true);
  });
  const initializerTwo = jest.fn(() => Promise.resolve('success'));
  const configServiceMock = {
    init: jest.fn(() => Promise.resolve()),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [],
      providers: [
        AppInitializer,
        { provide: CONFIGURATION_APP_INITIALIZER, useValue: [initializerOne, initializerTwo, () => Promise.resolve(true)] },
        { provide: CONFIGURATION_OPTIONS, useValue: { urlFactory: () => ['environments/config.common.json', 'environments/config.t1.json'], log: true } },
        { provide: ConfigurationService, useValue: configServiceMock },
      ],
    });
  });

  afterEach(() => {
    configServiceMock.init.mockClear();
    initializerOne.mockClear();
    initializerTwo.mockClear();
  });

  it('should create', () => {
    appInitializer = TestBed.inject(AppInitializer);
    expect(appInitializer).toBeTruthy();
  });

  it('init(), if CONFIGURATION_OPTIONS defined, should call configurationService.init()', async () => {
    appInitializer = TestBed.inject(AppInitializer);

    await appInitializer.init();
    expect(configServiceMock.init.mock.calls).toHaveLength(1);
  });

  it('init(), if CONFIGURATION_OPTIONS is undefined, does not call configurationService.init()', async () => {
    // SETUP
    TestBed.overrideProvider(CONFIGURATION_OPTIONS, { useValue: { urlFactory: undefined, log: false } });
    appInitializer = TestBed.inject(AppInitializer);

    // EXERCISE
    await appInitializer.init();

    // VERIFY
    expect(configServiceMock.init.mock.calls).toHaveLength(0);
  });

  it('init(), if CONFIGURATION_APP_INITIALIZER defines functions, executes them.', async () => {
    appInitializer = TestBed.inject(AppInitializer);

    await appInitializer.init();
    expect(initializerOne.mock.calls).toHaveLength(1);
    expect(initializerOne.mock.calls).toHaveLength(1);
  });
});
