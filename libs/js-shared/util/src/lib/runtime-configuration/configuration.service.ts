import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class ConfigurationService<TEnvironmentVariable extends { PIPELINE_STAGE: 'dev' | 'ci' | 'stage' | 'prod'; CONFIGURATION_OVERRIDES?: string[] }, TConfiguration> {
  private config?: TConfiguration;
  private environment: TEnvironmentVariable | undefined;
  private currentConfigUrl: string | undefined;
  private readonly unusableUrls: string[] = [];

  // region public accessors and mutators
  public get configuration(): TConfiguration {
    if (this.config == null) throw new Error("Configuration hasn't been initialized");
    return this.config;
  }

  /**
   * The configuration URLs that answered with something that was not a configuration.
   *
   * Non-empty means the application is running on a *partial* configuration: it booted, and some
   * service root or authentication setting is whatever the files that did load happened to say.
   * That is deliberately not fatal — an absent `CONFIGURATION_OVERRIDES` entry is normal — but it
   * is also the shape of a misconfigured deployment, and a container healthcheck cannot see it.
   * Read it to surface the condition somewhere a human will look.
   */
  public get unusableConfigurationUrls(): readonly string[] {
    return this.unusableUrls;
  }

  public async init(environment: TEnvironmentVariable): Promise<TConfiguration> {
    this.environment = environment;
    try {
      await this.initInternal();
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`Runtime configuration:${this.currentConfigUrl} load failed - ${message}`, { cause: error });
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
    return configs.reduce<TConfiguration>((acc, cur) => this.deepMerge(acc, cur), {} as TConfiguration);
  }

  private deepMerge<T>(target: T, source: T): T {
    if (source === null || source === undefined) return target;
    if (typeof source !== 'object' || Array.isArray(source)) return source;
    const result: Record<string, unknown> = { ...(target as Record<string, unknown>) };
    for (const key of Object.keys(source as Record<string, unknown>)) {
      const sourceValue = (source as Record<string, unknown>)[key];
      const targetValue = (target as Record<string, unknown> | undefined)?.[key];
      const bothPlainObjects =
        sourceValue !== null && typeof sourceValue === 'object' && !Array.isArray(sourceValue) && targetValue !== null && typeof targetValue === 'object' && !Array.isArray(targetValue);
      result[key] = bothPlainObjects ? this.deepMerge(targetValue, sourceValue) : sourceValue;
    }
    return result as T;
  }

  /**
   * One configuration file, or `{}` when what came back was not one.
   *
   * The body is read as text and parsed here rather than through `response.json()` so that the two
   * ways a configuration file can be absent collapse into the same outcome. A 404 is the obvious
   * one. The other is an SPA fallback: behind `try_files $uri $uri/ /index.html` a file that was
   * never deployed answers `200` with `index.html`, so `response.ok` is true and the body is a
   * document. `response.json()` threw on that, `init()` wrapped it, `bootstrapApplication` never
   * ran and the application rendered nothing at all — while the container healthcheck, which asks
   * nginx for a route the same fallback answers, stayed green. That is the whole reason this method
   * is shaped the way it is: a file that is not there must not be able to take the application
   * down, however the server chooses to say so.
   *
   * A file that IS there and does not parse is the opposite case, and still rejects. `content-type`
   * is what separates them: a server claiming `application/json` and sending something else has a
   * genuinely broken configuration deployed, and booting on a silently incomplete configuration is
   * worse than failing loudly. An absent or non-JSON `content-type` is read as the fallback case,
   * because that is what SPA fallbacks and error pages actually send.
   */
  private async loadConfig(url: string): Promise<TConfiguration> {
    this.currentConfigUrl = url;
    try {
      const response = await fetch(url);
      if (!response.ok) return this.unusable(url, `answered HTTP ${response.status}`);

      const body = await response.text();
      if (body.trim() === '') return this.unusable(url, 'answered with an empty body');

      const contentType = response.headers?.get('content-type') ?? '';
      try {
        return JSON.parse(body) as TConfiguration;
      } catch (parseError) {
        if (contentType.includes('json')) throw parseError;
        const served = contentType === '' ? 'an unparseable body with no content-type' : contentType;
        return this.unusable(url, `answered ${served} rather than JSON`, body);
      }
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        console.log(`Error: ${error.message} while fetching from:  ${url}`);
      }
      throw error;
    }
  }

  /**
   * Records and reports a URL that yielded no configuration, and contributes nothing to the merge.
   *
   * One `console.warn` per URL, naming the file and what came back instead, because the two
   * failures this now covers used to report themselves in entirely different registers: a
   * `console.log` nobody reads for the 404, and an unhandled bootstrap rejection for the HTML.
   * Neither said which file was missing or what to do about it.
   */
  private unusable(url: string, reason: string, body?: string): TConfiguration {
    if (!this.unusableUrls.includes(url)) this.unusableUrls.push(url);

    const hint =
      body === undefined
        ? 'The application is running on whatever the other configuration files supplied.'
        : 'That is what a file which was never deployed looks like behind an SPA fallback: it answers 200 with index.html for any unknown path. Check that the file is present in the served output, and that PIPELINE_STAGE names one the application actually ships. Body began: ' +
          body.trim().slice(0, 80);
    console.warn(`Runtime configuration: ${url} ${reason} — continuing without it. ${hint}`);

    return {} as TConfiguration;
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
    return new URL(url, document.baseURI).href;
  }

  private getUrls(): string[] {
    const pipelineStage = this.environment?.PIPELINE_STAGE ?? 'ci';
    const defaultUrls = ['run-time-conf/config.common.json', `run-time-conf/config.${pipelineStage.toLocaleLowerCase()}.json`];
    const overrides = this.environment?.CONFIGURATION_OVERRIDES ?? [];
    return [...defaultUrls, ...overrides];
  }
  // endregion
}
