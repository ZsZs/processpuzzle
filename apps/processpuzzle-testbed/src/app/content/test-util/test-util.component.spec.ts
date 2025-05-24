import { render } from '@testing-library/angular';
import '@testing-library/jest-dom';
import { TestUtilsComponent } from './test-util.component';
import { CLIPBOARD_OPTIONS, ClipboardButtonComponent, MERMAID_OPTIONS, provideMarkdown } from 'ngx-markdown';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { SecurityContext } from '@angular/core';

describe('TestUtilsComponent', () => {
  const providers = [
    provideHttpClient(),
    provideMarkdown({
      loader: HttpClient,
      sanitize: SecurityContext.NONE,
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
    const { fixture } = await render(TestUtilsComponent, { providers });
    const componentInstance = fixture.componentInstance;

    expect(componentInstance).toBeTruthy();
  });

  it('should call the onLoad method when triggered', async () => {
    const { fixture } = await render(TestUtilsComponent, { providers });
    const componentInstance = fixture.componentInstance;
    const onLoadSpy = jest.spyOn(componentInstance, 'onLoad');

    const mockEvent = 'Sample load event';
    componentInstance.onLoad(mockEvent);

    expect(onLoadSpy).toHaveBeenCalledWith(mockEvent);
  });

  it('should call the onError method when triggered', async () => {
    const { fixture } = await render(TestUtilsComponent, { providers });
    const componentInstance = fixture.componentInstance;
    const onErrorSpy = jest.spyOn(componentInstance, 'onError');

    const mockErrorEvent = new Error('Sample error');
    componentInstance.onError(mockErrorEvent);

    expect(onErrorSpy).toHaveBeenCalledWith(mockErrorEvent);
  });
});
