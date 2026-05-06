import { TestBed } from '@angular/core/testing';
import { type INGXLoggerConfig, TOKEN_LOGGER_CONFIG } from 'ngx-logging-kit';
import { describe, expect, it } from 'vitest';
import { provideLoggingService } from './provide-logging.service';

describe('provideLoggingService', () => {
  it('enables source-map-backed source details for trace console logging', () => {
    TestBed.configureTestingModule({
      providers: [
        provideLoggingService({
          level: 'trace',
          serverLogLevel: 'none',
        }),
      ],
    });

    const loggerConfig = TestBed.inject(TOKEN_LOGGER_CONFIG) as INGXLoggerConfig;

    expect(loggerConfig.level).toBe(0);
    expect(loggerConfig.enableSourceMaps).toBe(true);
    expect(loggerConfig.disableFileDetails).toBe(false);
  });

  it('enables source-map-backed source details for trace server logging', () => {
    TestBed.configureTestingModule({
      providers: [
        provideLoggingService({
          level: 'info',
          serverLogLevel: 'trace',
          serverLoggingUrl: '/api/logs',
        }),
      ],
    });

    const loggerConfig = TestBed.inject(TOKEN_LOGGER_CONFIG) as INGXLoggerConfig;

    expect(loggerConfig.level).toBe(2);
    expect(loggerConfig.serverLogLevel).toBe(0);
    expect(loggerConfig.enableSourceMaps).toBe(true);
    expect(loggerConfig.disableFileDetails).toBe(false);
  });

  it('keeps source map lookup disabled below trace', () => {
    TestBed.configureTestingModule({
      providers: [
        provideLoggingService({
          level: 'debug',
          serverLogLevel: 'error',
          serverLoggingUrl: '/api/logs',
        }),
      ],
    });

    const loggerConfig = TestBed.inject(TOKEN_LOGGER_CONFIG) as INGXLoggerConfig;

    expect(loggerConfig.level).toBe(1);
    expect(loggerConfig.serverLogLevel).toBe(5);
    expect(loggerConfig.enableSourceMaps).toBe(false);
    expect(loggerConfig.disableFileDetails).toBe(false);
  });
});
