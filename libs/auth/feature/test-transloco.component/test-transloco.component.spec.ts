import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { screen } from '@testing-library/angular';
import { TestTranslocoComponent } from './test-transloco.component';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import '@testing-library/jest-dom/vitest';
import authEn from '../assets/i18n/auth/en.json';
import authDe from '../assets/i18n/auth/de.json';

describe('TestTranslocoComponent', () => {
  let component: TestTranslocoComponent;
  const testConfig: TranslocoTestConfig = {
    scope: 'auth',
    translations: {
      'auth/en': authEn,
      'auth/de': authDe,
    },
  };

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(TestTranslocoComponent, testConfig, {
      imports: [ReactiveFormsModule, TranslocoDirective, TranslocoPipe],
      providers: [],
    });
    component = result.component;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it.skip('should H1 and Label exist', async () => {
    // Wait for translations to load by checking the heading content
    const heading = await screen.findByRole('heading', { level: 1 });
    expect(heading).toBeInTheDocument();
    expect(heading).toHaveTextContent('Create Account');

    const emailLabel = await screen.findByText('Email');
    expect(emailLabel).toBeInTheDocument();
  });
});
