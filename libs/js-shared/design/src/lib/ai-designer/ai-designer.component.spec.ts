import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { setUpTranslocoTestBed } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { describe, expect, it } from 'vitest';
import { AiDesignerComponent } from './ai-designer.component';

describe('AiDesignerComponent', () => {
  it('hosts the assistant panel on the page', async () => {
    const { fixture } = await setUpTranslocoTestBed(
      AiDesignerComponent,
      { translations: { en: {} } },
      {
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: 'https://backend/organizations/acme' } } },
        ],
      },
    );
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('pp-assistant-panel'))).toBeTruthy();
  });
});
