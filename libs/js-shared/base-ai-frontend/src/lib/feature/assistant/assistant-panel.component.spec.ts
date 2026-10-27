import { ComponentFixture } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { setUpTranslocoTestBed } from '@processpuzzle/test-util';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssistantService } from '../../domain/assistant/assistant.service';
import { AssistantPanelComponent } from './assistant-panel.component';

describe('AssistantPanelComponent', () => {
  let fixture: ComponentFixture<AssistantPanelComponent>;
  let turn: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    turn = vi.fn().mockResolvedValue({ sessionId: 's-1', reply: 'Saved.' });
    const result = await setUpTranslocoTestBed(AssistantPanelComponent, { translations: { en: {} } }, {
      providers: [{ provide: AssistantService, useValue: { turn } }],
    });
    fixture = result.fixture;
    fixture.detectChanges();
  });

  afterEach(() => vi.clearAllMocks());

  function textarea(): HTMLTextAreaElement {
    return fixture.debugElement.query(By.css('textarea')).nativeElement;
  }

  function type(text: string): void {
    textarea().value = text;
    textarea().dispatchEvent(new Event('input'));
  }

  function press(init: KeyboardEventInit): void {
    textarea().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ...init, cancelable: true }));
  }

  it('sends the message on Enter', async () => {
    type('Define an invoice.');

    press({});
    await fixture.whenStable();

    expect(turn).toHaveBeenCalledWith('Define an invoice.', undefined, expect.any(Function));
  });

  it('starts a new line on Shift+Enter instead of sending', async () => {
    type('Define an invoice.');

    press({ shiftKey: true });
    await fixture.whenStable();

    expect(turn).not.toHaveBeenCalled();
  });

  it('does nothing on Enter for a blank message', async () => {
    type('   ');

    press({});
    await fixture.whenStable();

    expect(turn).not.toHaveBeenCalled();
  });

  it('shows an icon for each speaker, with the speaker kept for screen readers', async () => {
    type('Define an invoice.');
    press({});
    await fixture.whenStable();
    fixture.detectChanges();

    const icons = fixture.debugElement.queryAll(By.css('.pp-assistant__icon')).map((icon) => icon.nativeElement.textContent.trim());
    expect(icons).toEqual(['person', 'smart_toy']);
  });
});
