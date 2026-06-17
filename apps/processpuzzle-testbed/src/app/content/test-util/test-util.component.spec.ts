import { describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/angular';
import '@testing-library/jest-dom/vitest';
import { TestUtilsComponent } from './test-util.component';
import { MarkdownComponent } from 'ngx-markdown';
import { Component, input, output } from '@angular/core';

@Component({ selector: 'markdown', template: '' })
class MockMarkdownComponent {
  readonly src = input<string>();
  readonly load = output<string>();
  readonly error = output<string | Error>();
}

describe('TestUtilsComponent', () => {
  const renderOptions = {
    importOverrides: [{ replace: MarkdownComponent, with: MockMarkdownComponent }],
  };

  it('should create the component', async () => {
    const { fixture } = await render(TestUtilsComponent, renderOptions);
    const componentInstance = fixture.componentInstance;

    expect(componentInstance).toBeTruthy();
  });

  it('should call the onLoad method when triggered', async () => {
    const { fixture } = await render(TestUtilsComponent, renderOptions);
    const componentInstance = fixture.componentInstance;
    const onLoadSpy = vi.spyOn(componentInstance, 'onLoad');

    const mockEvent = 'Sample load event';
    componentInstance.onLoad(mockEvent);

    expect(onLoadSpy).toHaveBeenCalledWith(mockEvent);
  });

  it('should call the onError method when triggered', async () => {
    const { fixture } = await render(TestUtilsComponent, renderOptions);
    const componentInstance = fixture.componentInstance;
    const onErrorSpy = vi.spyOn(componentInstance, 'onError');

    const mockErrorEvent = new Error('Sample error');
    componentInstance.onError(mockErrorEvent);

    expect(onErrorSpy).toHaveBeenCalledWith(mockErrorEvent);
  });
});
