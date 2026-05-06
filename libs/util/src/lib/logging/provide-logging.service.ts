import { EnvironmentProviders } from '@angular/core';
import { type INGXLoggerConfig, provideLogger } from 'ngx-logging-kit';
import { LoggingConfiguration } from './logging.service';

type LoggingLevel = LoggingConfiguration['level'];

function mapLoggerLevel(level: LoggingLevel): INGXLoggerConfig['level'] {
  switch (level) {
    case 'none':
      return 7; // OFF
    case 'trace':
      return 0; // TRACE
    case 'debug':
      return 1; // DEBUG
    case 'info':
      return 2; // INFO
    case 'log':
      return 3; // LOG
    case 'warn':
      return 4; // WARN
    case 'error':
      return 5; // ERROR
    case 'fatal':
      return 6; // FATAL
  }
}

export function provideLoggingService(config: LoggingConfiguration): EnvironmentProviders {
  const sourceDetailsEnabled = config.level === 'trace' || config.serverLogLevel === 'trace';

  return provideLogger({
    level: mapLoggerLevel(config.level),
    disableConsoleLogging: false,
    disableFileDetails: false,
    enableSourceMaps: sourceDetailsEnabled,
    serverLogLevel: mapLoggerLevel(config.serverLogLevel),
    serverLoggingUrl: config.serverLogLevel !== 'none' ? config.serverLoggingUrl : undefined,
  });
}
