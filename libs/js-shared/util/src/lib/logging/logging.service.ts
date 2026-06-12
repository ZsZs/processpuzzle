export interface LoggingConfiguration {
  level: 'none' | 'trace' | 'debug' | 'info' | 'log' | 'warn' | 'error' | 'fatal';
  serverLogLevel: 'none' | 'trace' | 'debug' | 'info' | 'log' | 'warn' | 'error' | 'fatal';
  serverLoggingUrl?: string;
}
