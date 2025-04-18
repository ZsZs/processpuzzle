# @processpuzzle/widgets
![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-widgets.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_widgets&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_widgets)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fwidgets?style=flat)](https://www.npmjs.com/package/@processpuzzle/widgets)

This library provides a range of small widgets that can be built into an Angular application. These widgets primarily define UI elements 
but also domain classes or signal stores. Some of them are even persistent, so they need appropriate configuration to access the backend.

## Language Selector Component
The **LanguageSelector** is an Angular component designed to allow users to select their preferred language in an application. It integrates seamlessly with the Transloco library for internationalization and supports dynamic updates of the application's language.

### Features
- **Language Selection**: Allows switching between predefined languages.
- **Integration with Transloco**: Updates active language using `TranslocoService`.
- **Dynamic Label Translation**: Displays localized language names.
- **Single Selection**: Users can select only one language at a time.
- **Customizable Options**: Easily configure available languages and their labels.
### Setup and Usage
#### Installation
Ensure that you have the required dependencies for Angular Material and Transloco installed:
```
npm install @angular/material @jsverse/transloco
```
### Template Example
Add the `LanguageSelector` component to your application:
```xhtml
<pp-language-selector [languages]="languages" [selectedLanguage]="selectedLanguage"></pp-language-selector>
```
### Component Configuration
#### Input Properties

| Input | Type | Description |
| --- | --- | --- |
| `languages` | `Array` | An array of available language objects. Each object should include `code`, `label`, and `flag`. |
| `selectedLanguage` | `string` | The current active language code. This value indicates which language is selected by default. |
Example Language Configuration:

## Like Button Component


## Navigate Back Button Component

## Share Button Component
