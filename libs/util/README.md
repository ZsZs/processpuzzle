# @processpuzzle/util
![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-util.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_util&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_util)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Futil?style=flat)](https://www.npmjs.com/package/@processpuzzle/util)

Dieses Bibliothek enthelt algemein nützliches Funktionen oder Modulen.

## wildcardTextMatcher()

Diese Funktion vergleicht ein Text mit ein Vergleicher Text, welch wildcars beinhalten kann.

```typescript
matchTextWith( textToSearchIn: string, textToMatchWidth: string ): boolean;
```

- *textToSearchIn*: ist der Gegenstand string
- *textToMachWidth*: ist der Vergleicher string welche wildchars '*' oder '.' beinhalten kann.

```typescript
const result = matchTextWith( 'Hello World', 'Hello*' ); // true
const result = matchTextWith( 'Hello World', '*World' ); // true
const result = matchTextWith( 'Hello World', 'He..o World' ); // true
const result = matchTextWith( 'Hello World', 'Hello .ld' ); // false
```

## getEnvironment()

Diese Funktion, abhängig von welche URL wurde die Applikation gestartet, liefert die (BRZ) Kürzel für die Umgebung.

- http://localhost* => 'local';

## Stack
Werwendung des Stacks
```typescript
const stack = new Stack<number>();

// Elemente zum Stack hinzufügen
stack.push(10);
stack.push(20);
stack.push(30);

// Stack-Größe ermitteln
console.log('Stackgröße:', stack.size()); // Output: 3

// Oberstes Element ansehen
console.log('Oberstes Element:', stack.peek()); // Output: 30

// Element entfernen
console.log('Vom Stack entfernt:', stack.pop()); // Output: 30

// Nach dem Entfernen das oberste Element ansehen
console.log('Oberstes Element:', stack.peek()); // Output: 20

// Prüfen, ob der Stack leer ist
console.log('Ist der Stack leer?', stack.isEmpty()); // Output: false

// Stack leeren
stack.clear();
console.log('Ist der Stack leer nach dem Leeren?', stack.isEmpty()); // Output: true
```

## SubstringPipe
Angular pipe to use in templates. Usefull to manipulate displayed text.
```html
<div matListItemTitle>&nbsp;{{ item.title | substring: 0: 10 }}</div>
```
The first parameter is the starting positon in text, the second is the ending position.

## RuntimeConfiguration

Das Modul erlaubt mehrere Konfigurationsdateien zu spezifizieren, die dann bei Applikation-Start (im Browser) wird geladen,
zusammengefügt und steht als DI zur Verfügung. Der Entwickler spezifiziert die Konfiguration Dateien als Wert für
CONFIGURATION_OPTIONS injection token:

```typescript
{
  provide: CONFIGURATION_OPTIONS, useValue:
  {
    urlFactory: () => {
      const env = getEnvironment();
      return [ 'environments/config.common.json', `run-time-conf/config.${env}.json` ]
    }, 
    log: true
  }
}
```

In dieses Beispiel wir die Datei ``environment/config.common.json`` erst gelesen, dann ``run-time-config/config.${env}.json``
und zusammengefügt. D.h der letztere Wert in Datei überschreibt die früheren Werte. Wenn es eine neue Eingenschaft (property)
mitbringt, dann wird es Teil der Konfiguration.
Der Entwickler kann die Konfiguration Eigenschaften mit einer Klasse selber definieren. Dazu ist es erforderlich
ein factory für die Klasse zu definieren:

```typescript
{
  provide: RuntimeConfiguration, useFactory: ( configurationService: ConfigurationService<RuntimeConfiguration> ) => {
    return configurationService.configuration, deps: [ ConfigurationService ]
  }
}
```

Die mitgelieferte AppInitializer sort für die Konfiguration zu initialisieren, und andere funktionen durchzuführen.
Siehe [AppInitializer]().

```typescript
{
  provide: APP_INITIALIZER, multi:true, useFactory: ( initializer: AppInitializer ) => {
    return async() => await initializer.init().then()
  },
  deps: [ AppInitializer ]
}
```

## AppInitializer

AppInitializer ist eine spezielle Implementierung für APP_INITIALIZER wodurch der RuntimeConfiguration automatisch
konfiguriert wird. Zusätzlich kann der AppInitializer die Methoden die mit CONFIGURATION_APP_INITIALIZER injection token
definiert sind, auch ausführen. diese Methode sollen ein Promise<unknown> zurückgeben.

```typescript
{ provide: CONFIGURATION_APP_INITIALIZER, useValue: [() => Promise.resolve('anything'), () => Promise.resolve('something')] }
```

Typische weise wird in app.config.ts konfiguriert, wie [oben](#runtimeconfiguration) dargestellt.
