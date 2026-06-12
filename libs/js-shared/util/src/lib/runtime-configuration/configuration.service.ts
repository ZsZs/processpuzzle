import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class ConfigurationService<TEnvironmentVariable extends { PIPELINE_STAGE: 'dev' | 'ci' | 'stage' | 'prod'; CONFIGURATION_OVERRIDES?: string[] }, TConfiguration> {
  private config?: TConfiguration;
  private environment: TEnvironmentVariable | undefined;
  private currentConfigUrl: string | undefined;

  // region public accessors and mutators
  public get configuration(): TConfiguration {
    if (this.config == null) throw new Error("Configuration hasn't been initialized");
    return this.config;
  }

  public async init(environment: TEnvironmentVariable): Promise<TConfiguration> {
    this.environment = environment;
    try {
      await this.initInternal();
    } catch (error: any) {
      throw new Error(`Runtime configuration:${this.currentConfigUrl} load failed - ${error.message}`, { cause: error });
    }
    return this.configuration;
  }
  // endregion

  // region protected, private helper methods
  private async initInternal(): Promise<void> {
    const externalUrls = this.determineConfigUrls();
    const configs = await this.loadConfigs(externalUrls);
    this.config = this.mergeConfigs(configs);

    console.log('Configuration loaded', this.config);
  }

  private mergeConfigs(configs: TConfiguration[]): TConfiguration {
    return { ...configs[0], ...configs[1] };
  }

  private async loadConfig(url: string): Promise<TConfiguration> {
    this.currentConfigUrl = url;
    let config: TConfiguration;
    try {
      const response = await fetch(url);
      if (!response.ok) console.log(`Failed to load configuration from ${url}`);
      config = (await response.json()) as unknown as TConfiguration;
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        console.log(`Error: ${error} while fetching from:  ${url}`);
      }
      throw error;
    }
    return config;
  }

  private async loadConfigs(configUrls: string[]): Promise<TConfiguration[]> {
    const configs$ = new Array<Promise<TConfiguration>>();
    configUrls.forEach((url) => {
      const config$ = this.loadConfig(url);
      configs$.push(config$);
    });
    const results: TConfiguration[] = [];
    for (const configPromise of configs$) {
      const config = await configPromise;
      results.push(config);
    }
    return results;
  }

  private determineConfigUrls() {
    const urls = this.getUrls();
    return urls.map((x) => this.ensureExternalUrl(x));
  }

  private ensureExternalUrl(url: string): string {
    if (url.startsWith('//') || url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }

    if (url.startsWith('/')) url = url.substring(1);

    const baseHref = globalThis.location.origin;
    return `${baseHref}/${url}`;
  }

  private getUrls(): string[] {
    const pipelineStage = this.environment?.PIPELINE_STAGE ?? 'ci';
    const defaultUrls = ['run-time-conf/config.common.json', `run-time-conf/config.${pipelineStage.toLocaleLowerCase()}.json`];
    const overrides = this.environment?.CONFIGURATION_OVERRIDES ?? [];
    return [...defaultUrls, ...overrides];
  }
  // endregion
}
