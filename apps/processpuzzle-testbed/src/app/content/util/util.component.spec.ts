import { describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/angular';
import '@testing-library/jest-dom/vitest';
import { UtilsComponent } from './util.component';
import { CLIPBOARD_OPTIONS, ClipboardButtonComponent, MERMAID_OPTIONS, provideMarkdown, SANITIZE } from 'ngx-markdown';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { SecurityContext } from '@angular/core';

describe('UtilsComponent', () => {
  const providers = [
    provideHttpClient(),
    provideMarkdown({
      loader: HttpClient,
      sanitize: { provide: SANITIZE, useValue: SecurityContext.NONE },
      mermaidOptions: {
        provide: MERMAID_OPTIONS,
        useValue: {
          darkMode: true,
          look: 'handDrawn',
        },
      },
      clipboardOptions: {
        provide: CLIPBOARD_OPTIONS,
        useValue: {
          buttonComponent: ClipboardButtonComponent,
        },
      },
    }),
  ];
  it('should create the component', async () => {
    const { fixture } = await render(UtilsComponent, { providers });
    const componentInstance = fixture.componentInstance;

    expect(componentInstance).toBeTruthy();
  });

  it('should call the onLoad method when triggered', async () => {
    const { fixture } = await render(UtilsComponent, { providers });
    const componentInstance = fixture.componentInstance;
    const onLoadSpy = vi.spyOn(componentInstance, 'onLoad');

    const mockEvent = 'Sample load event';
    componentInstance.onLoad(mockEvent);

    expect(onLoadSpy).toHaveBeenCalledWith(mockEvent);
  });

  it('should call the onError method when triggered', async () => {
    const { fixture } = await render(UtilsComponent, { providers });
    const componentInstance = fixture.componentInstance;
    const onErrorSpy = vi.spyOn(componentInstance, 'onError');

    const mockErrorEvent = new Error('Sample error');
    componentInstance.onError(mockErrorEvent);

    expect(onErrorSpy).toHaveBeenCalledWith(mockErrorEvent);
  });
});
