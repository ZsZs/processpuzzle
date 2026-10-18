import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CalculationSourceEditorComponent } from './calculation-source-editor.component';

describe('CalculationSourceEditorComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  async function editor(value: string) {
    const fixture = TestBed.createComponent(CalculationSourceEditorComponent);
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('shows the source in a code editor with line numbers', async () => {
    const fixture = await editor('calculation: a\ndecisions: {}\n');

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.cm-editor')).not.toBeNull();
    expect(host.querySelector('.cm-lineNumbers')).not.toBeNull();
    expect(fixture.componentInstance.text()).toBe('calculation: a\ndecisions: {}\n');
  });

  it('replaces what it edits when a different value arrives, and reports its own edits', async () => {
    const fixture = await editor('calculation: a\n');
    const emitted: string[] = [];
    fixture.componentInstance.valueChange.subscribe((value) => emitted.push(value));

    fixture.componentRef.setInput('value', 'calculation: b\n');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.text()).toBe('calculation: b\n');
    expect(emitted).toEqual(['calculation: b\n']);
    fixture.destroy();
  });
});
