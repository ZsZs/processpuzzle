# @processpuzzle/util
Dieses Bibliothek enthelt algemein nützliches Funktionen oder Modulen.

## wildcardTextMatcher()
Diese Funktion vergleicht ein Text mit ein Vergleicher Text, welch wildcars beinhalten kann.

    matchTextWith( textToSearchIn: string, textToMatchWidth: string ): boolean;

- *textToSearchIn*: ist der Gegenstand string
- *textToMachWidth*: ist der Vergleicher string welche wildchars '*' oder '.' beinhalten kann.


    const result = matchTextWith( 'Hello World', 'Hello*' ); // true
    const result = matchTextWith( 'Hello World', '*World' ); // true
    const result = matchTextWith( 'Hello World', 'He..o World' ); // true
    const result = matchTextWith( 'Hello World', 'Hello .ld' ); // false

## getEnvironment()
Diese Funktion, abhängig von welche URL wurde die Applikation gestartet, liefert die (BRZ) Kürzel für die Umgebung.
- http://localhost* => 'local';
- https://t0-*-bmf-ef-test.bmf.a2.cp.cna.at => 't0';
- https://t1-*-bmf-ef-test.bmf.a2.cp.cna.at => 't1';
- https://t2-*-bmf-ef-test.bmf.a2.cp.cna.at => 't2';
- https://q-*-bmf-ef-qs.bmf.a2.cp.cna.at => 'q';
- https://p-*-bmf-ef-prod.bmf.a2.cp.cna.at => 'p';

## RuntimeConfiguration
Das Modul erlaubt mehrere Konfigurationsdateien zu spezifizieren, die dann bei Applikation-Start (im Browser) wird geladen,
zusammengefügt und steht als DI zur Verfügung. Der Entwickler spezifiziert die Konfiguration Dateien als Wert für
CONFIGURATION_OPTIONS injection token:

    { provide: CONFIGURATION_OPTIONS, useValue: {
      urlFactory: () => {
        const env = getEnvironment();
        return ['environments/config.common.json', `run-time-conf/config.${env}.json`]
      },
      log: true
    }},

In dieses Beispiel wir die Datei ``environment/config.common.json`` erst gelesen, dann ``run-time-config/config.${env}.json``
und zusammengefügt. D.h der letztere Wert in Datei überschreibt die früheren Werte. Wenn es eine neue Eingenschaft (property)
mitbringt, dann wird es Teil der Konfiguration.
Der Entwickler kann die Konfiguration Eigenschaften mit einer Klasse selber definieren. Dazu ist es erforderlich
ein factory für die Klasse zu definieren:

    { provide: RuntimeConfiguration, 
      useFactory: (configurationService: ConfigurationService<RuntimeConfiguration>) => {
        return configurationService.configuration, deps: [ConfigurationService]
      }
    },

Die mitgelieferte AppInitializer sort für die Konfiguration zu initialisieren, und andere funktionen durchzuführen.
Siehe [AppInitializer]().

    { provide: APP_INITIALIZER, multi: true, useFactory: (initializer: AppInitializer) => {
      return async () => await initializer.init().then()
    }, deps: [AppInitializer]},

## AppInitializer
AppInitializer ist eine spezielle Implementierung für APP_INITIALIZER wodurch der RuntimeConfiguration automatisch
konfiguriert wird. Zusätzlich kann der AppInitializer die Methoden die mit CONFIGURATION_APP_INITIALIZER injection token
definiert sind, auch ausführen. diese Methode sollen ein Promise<unknown> zurückgeben.

    { provide: CONFIGURATION_APP_INITIALIZER, useValue: [() => Promise.resolve('anything'), () => Promise.resolve('something')] }

Typische weise wird in app.config.ts konfiguriert, wie [oben](#runtimeconfiguration) dargestellt.
