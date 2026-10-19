import { afterEach, describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { EntityUserContext } from '../../base-entity-user/entity-user.context';
import { RichTextContent } from '../../rich-text/rich-text-editor.component';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { CommentsComponent } from './comments.component';
import { EntityComment } from './entity-comment';

function doc(text: string): RichTextContent {
  return { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] };
}

const EMPTY_DOC: RichTextContent = { type: 'doc', content: [{ type: 'paragraph' }] };

const HISTORY: EntityComment[] = [
  { author: 'Ada Lovelace', authorId: 'ada', createdAt: '2026-10-01T09:30:00.000Z', content: doc('First thoughts') },
  { author: '', createdAt: '2026-10-02T10:00:00.000Z', content: doc('Anonymous follow-up') },
];

async function setupComments(options: { value?: unknown; disabled?: boolean; user?: { userId: string; displayName?: string } } = {}) {
  const config = new BaseEntityAttrDescriptor('comments', FormControlType.COMMENTS, 'Comments');
  config.disabled = options.disabled === true;
  const entity = new TestEntity('1', 'name');
  if (options.value !== undefined) Reflect.set(entity, 'comments', options.value);
  const harness = await setupFormControlTest(CommentsComponent, config, entity, []);
  if (options.user) harness.fixture.debugElement.injector.get(EntityUserContext).set(options.user);
  harness.fixture.detectChanges();
  const component = harness.component as CommentsComponent<TestEntity>;
  return { ...harness, component, control: component.formGroup.get('comments')!, element: harness.fixture.nativeElement as HTMLElement };
}

describe('CommentsComponent', () => {
  afterEach(() => vi.useRealTimers());

  it('is hidden from list tables by default', () => {
    expect(new BaseEntityAttrDescriptor('comments', FormControlType.COMMENTS).hideInTable).toBe(true);
  });

  it('renders the saved history as read-only cards, oldest first', async () => {
    const { element } = await setupComments({ value: HISTORY });

    const cards = Array.from(element.querySelectorAll('[data-testid="comment-card"]'));
    expect(cards.map((card) => card.querySelector('.pp-comments__author')?.textContent?.trim())).toEqual(['Ada Lovelace', 'base_entity.comments.unknown_author']);
    expect(cards[0].textContent).toContain('First thoughts');
    expect(cards[0].querySelector('[contenteditable="true"]')).toBeNull();
    expect(cards[0].querySelector('time')?.getAttribute('datetime')).toBe('2026-10-01T09:30:00.000Z');
  });

  it('treats a value that is not an array as an empty history', async () => {
    const { component, element } = await setupComments({ value: 'garbage' });

    expect(component.history()).toEqual([]);
    expect(element.querySelectorAll('[data-testid="comment-card"]').length).toBe(0);
  });

  it('offers an editor for a new comment', async () => {
    const { element } = await setupComments({ value: HISTORY });

    expect(element.querySelector('.pp-comments__new [contenteditable="true"]')).not.toBeNull();
  });

  it('shows the history alone when the attribute is disabled', async () => {
    const { element, component } = await setupComments({ value: HISTORY, disabled: true });

    expect(component.isEditable()).toBe(false);
    expect(element.querySelector('.pp-comments__new')).toBeNull();
    expect(element.querySelectorAll('[data-testid="comment-card"]').length).toBe(2);
  });

  it('appends the draft stamped with the current user and time, and dirties the control', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-16T12:00:00.000Z'));
    const { component, control } = await setupComments({ value: HISTORY, user: { userId: 'grace', displayName: 'Grace Hopper' } });

    component.onDraftChange(doc('New remark'));

    expect(control.value).toEqual([...HISTORY, { author: 'Grace Hopper', authorId: 'grace', createdAt: '2026-10-16T12:00:00.000Z', content: doc('New remark') }]);
    expect(control.dirty).toBe(true);
    expect(control.touched).toBe(true);
  });

  it('refreshes the timestamp on every edit of the draft', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-16T12:00:00.000Z'));
    const { component, control } = await setupComments({ value: [] });

    component.onDraftChange(doc('Draft'));
    vi.setSystemTime(new Date('2026-10-16T12:05:00.000Z'));
    component.onDraftChange(doc('Draft, revised'));

    expect(control.value).toHaveLength(1);
    expect(control.value[0].createdAt).toBe('2026-10-16T12:05:00.000Z');
  });

  it('never rewrites a saved comment', async () => {
    const { component, control } = await setupComments({ value: HISTORY });

    component.onDraftChange(doc('One'));
    component.onDraftChange(doc('Two'));

    expect(control.value.slice(0, 2)).toEqual(HISTORY);
    expect(component.history()).toEqual(HISTORY);
  });

  it('puts the history back when the draft is emptied', async () => {
    const { component, control } = await setupComments({ value: HISTORY });

    component.onDraftChange(doc('Second thoughts'));
    component.onDraftChange(EMPTY_DOC);

    expect(control.value).toEqual(HISTORY);
    expect(control.dirty).toBe(false);
  });

  it('falls back to the user id when the host knows no display name', async () => {
    const { component, control } = await setupComments({ value: [], user: { userId: 'grace' } });

    component.onDraftChange(doc('Hello'));

    expect(control.value[0]).toMatchObject({ author: 'grace', authorId: 'grace' });
  });

  it('stamps an unknown author, without an id, when nobody is signed in', async () => {
    const { component, control } = await setupComments({ value: [] });

    component.onDraftChange(doc('Hello'));

    expect(control.value[0].author).toBe('Unknown');
    expect(control.value[0]).not.toHaveProperty('authorId');
  });
});
