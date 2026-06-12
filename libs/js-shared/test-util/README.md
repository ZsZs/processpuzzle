# @processpuzzle/test-util
![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-test-util.yml/badge.svg)
[![Quality gate](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_test_util&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=processpuzzle_test_util&branch=develop)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Ftest-util?style=flat)](https://www.npmjs.com/package/@processpuzzle/test-util)

## Overview
The `@processpuzzle/test-util` library provides a collection of utilities to simplify testing in Angular applications. It includes tools for testing responsive layouts, internationalization, and more.

## Installation

```bash
npm install @processpuzzle/test-util --save-dev
```

## Features

### MockBreakpointObserver

A mock implementation of Angular's BreakpointObserver for testing responsive layouts. It allows you to simulate different screen sizes and device types in your tests.

#### Usage

```typescript
import { MockBreakpointObserver, DeviceTypes } from '@processpuzzle/test-util';

describe('MyResponsiveComponent', () => {
  let mockBreakpointObserver: MockBreakpointObserver;

  beforeEach(() => {
    mockBreakpointObserver = new MockBreakpointObserver();

    TestBed.configureTestingModule({
      declarations: [MyResponsiveComponent],
      providers: [
        { provide: BreakpointObserver, useValue: mockBreakpointObserver }
      ]
    });
  });

  it('should display mobile layout on small screens', () => {
    // Simulate a mobile device with width 400px
    mockBreakpointObserver.resize(400, DeviceTypes.HANDSET);

    // Test your component's behavior
  });

  it('should display desktop layout on large screens', () => {
    // Simulate a desktop with width 1600px
    mockBreakpointObserver.resize(1600, DeviceTypes.WEB);

    // Test your component's behavior
  });
});
```

### Transloco Testing Utilities

Utilities for testing with the Transloco internationalization library.

#### Usage

```typescript
import { getTranslocoModule } from '@processpuzzle/test-util';

describe('MyI18nComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [MyI18nComponent],
      imports: [
        getTranslocoModule({
          en: { 'MY_KEY': 'My translated text' },
          de: { 'MY_KEY': 'Mein übersetzter Text' }
        })
      ]
    });
  });

  it('should display translated text', () => {
    // Test your component's behavior with translations
  });
});
```

### Translation Assets

The library includes sample translation assets for testing internationalization features:

- Language translations for English, German, French, Hungarian, and Spanish
- Located in the `assets/i18n/widgets` directory

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
