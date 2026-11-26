import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { NgxLoggerLevel, provideLogger } from 'ngx-logging-kit';
import { Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AI_ASSIST_CLIENT, AiAssistClient, AiAssistResult } from './ai-assist';
import { AiAssistButtonComponent } from './ai-assist-button.component';

const TRANSLATIONS = {
  en: {
    base_entity: { ai_assist: { tooltip: 'Generate', empty: 'Nothing came out.', replaced: 'Replaced.', issue: 'May be invalid: {{issue}}', undo: 'Undo' } },
  },
};

function result(candidate: string, issues: AiAssistResult['issues'] = []): AiAssistResult {
  return { candidate, explanation: '', assumptions: [], issues, rounds: 1, model: 'pp' };
}

describe('AiAssistButtonComponent', () => {
  const available = signal(true);
  const action = new Subject<void>();
  let client: { isAvailable: ReturnType<typeof vi.fn>; run: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<AiAssistButtonComponent>;
  let generated: string[];

  async function setUp(options: { client?: boolean; text?: string; current?: string } = {}): Promise<void> {
    available.set(true);
    client = { isAvailable: vi.fn(() => available), run: vi.fn() };
    snackBar = { open: vi.fn(() => ({ onAction: () => action.asObservable() })) };
    await TestBed.configureTestingModule({
      providers: [
        provideLogger({ level: NgxLoggerLevel.OFF, serverLogLevel: NgxLoggerLevel.OFF, disableConsoleLogging: true }),
        provideTranslocoTesting({ translations: TRANSLATIONS }),
        { provide: MatSnackBar, useValue: snackBar },
        ...(options.client === false ? [] : [{ provide: AI_ASSIST_CLIENT, useValue: client as unknown as AiAssistClient }]),
      ],
    }).compileComponents();
    // The scoped bundle is what the app loads; the keys are put straight on the service so `translate()` finds them.
    TestBed.inject(TranslocoService).setTranslation(TRANSLATIONS.en, 'en');
    fixture = TestBed.createComponent(AiAssistButtonComponent);
    fixture.componentRef.setInput('task', 'rule.expression');
    fixture.componentRef.setInput('text', options.text ?? 'The total must be positive');
    fixture.componentRef.setInput('parameters', { context: 'Order' });
    fixture.componentRef.setInput('current', options.current ?? '');
    generated = [];
    fixture.componentInstance.generated.subscribe((value) => generated.push(value));
    fixture.detectChanges();
  }

  const button = (): HTMLButtonElement | null => fixture.nativeElement.querySelector('[data-testid="ai-assist-button"]');

  beforeEach(() => TestBed.resetTestingModule());

  it('is not shown without an AI assist client', async () => {
    await setUp({ client: false });

    expect(button()).toBeNull();
  });

  it('is not shown while the task is unavailable and appears once it is', async () => {
    await setUp();
    available.set(false);
    fixture.detectChanges();
    expect(button()).toBeNull();

    available.set(true);
    fixture.detectChanges();
    expect(button()).not.toBeNull();
    expect(client.isAvailable).toHaveBeenCalledWith('rule.expression');
  });

  it('is disabled until the description has text', async () => {
    await setUp({ text: '   ' });
    expect(button()?.disabled).toBe(true);

    fixture.componentRef.setInput('text', 'The total must be positive');
    fixture.detectChanges();
    expect(button()?.disabled).toBe(false);
  });

  it('does nothing when clicked while disabled', async () => {
    await setUp({ text: '' });

    await fixture.componentInstance.generate();

    expect(client.run).not.toHaveBeenCalled();
  });

  it('asks the task with the description, the parameters and the current value, and hands the result to the field', async () => {
    await setUp({ current: '' });
    client.run.mockResolvedValue(result('entity.total > 0'));

    button()?.click();
    await fixture.whenStable();

    expect(client.run).toHaveBeenCalledWith('rule.expression', { text: 'The total must be positive', parameters: { context: 'Order' }, current: '' });
    expect(generated).toEqual(['entity.total > 0']);
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('is disabled while the request runs', async () => {
    await setUp();
    let finish: (value: AiAssistResult) => void = (_value) => undefined;
    client.run.mockReturnValue(new Promise<AiAssistResult>((resolve) => (finish = resolve)));

    const done = fixture.componentInstance.generate();
    fixture.detectChanges();
    expect(button()?.disabled).toBe(true);

    finish(result('entity.total > 0'));
    await done;
    fixture.detectChanges();
    expect(button()?.disabled).toBe(false);
  });

  it('offers to undo when it replaced something', async () => {
    await setUp({ current: 'entity.total >= 0' });
    client.run.mockResolvedValue(result('entity.total > 0'));

    await fixture.componentInstance.generate();

    expect(generated).toEqual(['entity.total > 0']);
    expect(snackBar.open).toHaveBeenCalledWith('Replaced.', 'Undo', { duration: 8000 });

    action.next();
    expect(generated).toEqual(['entity.total > 0', 'entity.total >= 0']);
  });

  it('still fills the field when errors remain, and says what is wrong', async () => {
    await setUp({ current: 'old' });
    client.run.mockResolvedValue(
      result('entity.totl > 0', [
        { severity: 'ERROR', path: 'expression', text: "'entity.totl' is not an attribute" },
        { severity: 'WARNING', path: '', text: 'minor' },
      ]),
    );

    await fixture.componentInstance.generate();

    expect(generated).toEqual(['entity.totl > 0']);
    expect(snackBar.open).toHaveBeenCalledWith("May be invalid: 'entity.totl' is not an attribute", 'Undo', { duration: 10000 });
  });

  it('says what is wrong without an undo when the field was empty', async () => {
    await setUp({ current: '' });
    client.run.mockResolvedValue(result('entity.totl > 0', [{ severity: 'ERROR', path: '', text: 'bad' }]));

    await fixture.componentInstance.generate();

    expect(snackBar.open).toHaveBeenCalledWith('May be invalid: bad', undefined, { duration: 10000 });
  });

  it('warnings alone are not worth a message', async () => {
    await setUp({ current: '' });
    client.run.mockResolvedValue(result('entity.total > 0', [{ severity: 'WARNING', path: '', text: 'minor' }]));

    await fixture.componentInstance.generate();

    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('leaves the field alone and says so when no result could be read', async () => {
    await setUp({ current: 'keep me' });
    client.run.mockResolvedValue(result(''));

    await fixture.componentInstance.generate();

    expect(generated).toEqual([]);
    expect(snackBar.open).toHaveBeenCalledWith('Nothing came out.', undefined, { duration: 6000 });
  });

  it('leaves a failed request to the application error handling and is usable again', async () => {
    await setUp();
    client.run.mockRejectedValueOnce(new HttpErrorResponse({ status: 502 })).mockResolvedValueOnce(result('entity.total > 0'));

    await fixture.componentInstance.generate();
    expect(generated).toEqual([]);
    expect(snackBar.open).not.toHaveBeenCalled();

    await fixture.componentInstance.generate();
    expect(generated).toEqual(['entity.total > 0']);
  });

  it('survives a failure that is not an HTTP error', async () => {
    await setUp();
    client.run.mockRejectedValue(new Error('boom'));

    await expect(fixture.componentInstance.generate()).resolves.toBeUndefined();
  });
});
