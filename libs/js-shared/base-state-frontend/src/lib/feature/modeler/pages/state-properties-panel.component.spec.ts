import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { State } from '../../../domain/state-machine-definition';
import { StateEdit, StatePropertiesPanelComponent } from './state-properties-panel.component';

describe('StatePropertiesPanelComponent', () => {
  const state = new State({ key: 'DRAFT', name: 'Draft', description: 'Entered but not reviewed.', isFinal: false, isLocked: false });

  let fixture: ComponentFixture<StatePropertiesPanelComponent>;
  let edits: StateEdit[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatePropertiesPanelComponent],
      providers: [provideTranslocoTesting({ translations: { en: { 'base_state.state_machine_state.name': 'Name' } } })],
    }).compileComponents();

    fixture = TestBed.createComponent(StatePropertiesPanelComponent);
    fixture.componentRef.setInput('state', state);
    edits = [];
    fixture.componentInstance.stateChanged.subscribe((edit) => edits.push(edit));
    fixture.detectChanges();
  });

  function field(testid: string): HTMLInputElement {
    const element = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(`[data-testid="${testid}"]`);
    if (!element) throw new Error(`No ${testid} field is rendered.`);
    return element;
  }

  /** `(change)` rather than `(input)`, so this is the gesture the panel listens for. */
  function commit(testid: string, value: string): void {
    const input = field(testid);
    input.value = value;
    input.dispatchEvent(new Event('change'));
  }

  function tick(testid: string, checked = true): void {
    const input = field(testid);
    input.checked = checked;
    input.dispatchEvent(new Event('change'));
  }

  it('shows the selected state', () => {
    expect(field('state-key').value).toBe('DRAFT');
    expect(field('state-name').value).toBe('Draft');
    expect(field('state-description').value).toBe('Entered but not reviewed.');
    expect(field('state-is-final').checked).toBe(false);
  });

  it('emits the whole state with the edit applied, keyed by what it was before', () => {
    commit('state-name', 'Captured');

    expect(edits).toHaveLength(1);
    expect(edits[0].previousKey).toBe('DRAFT');
    expect(edits[0].state.name).toBe('Captured');
    expect(edits[0].state.description).toBe('Entered but not reviewed.');
  });

  // The panel is a view of its input, not an owner of its subject — see the class comment.
  it('leaves its input untouched', () => {
    commit('state-name', 'Captured');

    expect(state.name).toBe('Draft');
    expect(edits[0].state).not.toBe(state);
  });

  it('emits the flags as flags rather than as strings', () => {
    tick('state-is-final');
    tick('state-is-locked');

    expect(edits.map((edit) => [edit.state.isFinal, edit.state.isLocked])).toEqual([
      [true, false],
      [false, true],
    ]);
  });

  it('reports an emptied description as absent, so the field is not persisted as blank', () => {
    commit('state-description', '   ');

    expect(edits[0].state.description).toBe('   ');

    commit('state-description', '');

    expect(edits[1].state.description).toBeUndefined();
  });

  describe('the key', () => {
    it('is read-only for a state the machine already declares', () => {
      expect(field('state-key').readOnly).toBe(true);
    });

    it('is editable for a state that has only just been dropped', () => {
      fixture.componentRef.setInput('keyEditable', true);
      fixture.detectChanges();

      commit('state-key', 'CAPTURED');

      expect(field('state-key').readOnly).toBe(false);
      expect(edits[0]).toMatchObject({ previousKey: 'DRAFT' });
      expect(edits[0].state.key).toBe('CAPTURED');
    });

    // An unidentifiable state is worse than an unrenamed one.
    it('refuses to be emptied', () => {
      fixture.componentRef.setInput('keyEditable', true);
      fixture.detectChanges();

      commit('state-key', '  ');

      expect(edits).toEqual([]);
    });
  });

  describe('the entry point', () => {
    it('shows whether this is the state the machine starts in', () => {
      expect(field('state-initial').checked).toBe(false);

      fixture.componentRef.setInput('initial', true);
      fixture.detectChanges();

      expect(field('state-initial').checked).toBe(true);
    });

    it('claims the entry point when ticked', () => {
      tick('state-initial');

      expect(edits[0].initial).toBe(true);
      expect(edits[0].state.key).toBe('DRAFT');
    });

    // A machine has to start somewhere; the entry point moves by ticking its new holder.
    it('ignores an attempt to untick it', () => {
      fixture.componentRef.setInput('initial', true);
      fixture.detectChanges();

      tick('state-initial', false);

      expect(edits).toEqual([]);
    });

    it('carries the entry point through an unrelated edit', () => {
      fixture.componentRef.setInput('initial', true);
      fixture.detectChanges();

      commit('state-name', 'Captured');

      expect(edits[0].initial).toBe(true);
    });
  });
});
