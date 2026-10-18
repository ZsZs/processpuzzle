import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { TreeNavComponent, TreeNavNode, TreeNavNodeActionsDirective } from './tree-nav.component';

const BOOK: TreeNavNode[] = [
  {
    id: 'overview',
    label: 'Overview',
    icon: 'description',
    children: [{ id: 'details', label: 'Details', children: [{ id: 'deep', label: 'Deep dive' }] }],
  },
  { id: 'notes', label: 'Release notes' },
];

@Component({
  imports: [TreeNavComponent, TreeNavNodeActionsDirective],
  template: `
    <pp-tree-nav [nodes]="nodes()" [selectedId]="selectedId()" [expandAll]="expandAll()" (selected)="picked.push($event.id)">
      <ng-template ppTreeNavNodeActions let-node>
        <button type="button" [attr.data-testid]="'action-' + node.id">act</button>
      </ng-template>
    </pp-tree-nav>
  `,
})
class HostComponent {
  readonly nodes = signal<TreeNavNode[]>(BOOK);
  readonly selectedId = signal<string | undefined>(undefined);
  readonly expandAll = signal(false);
  readonly picked: string[] = [];
}

describe('TreeNavComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  const row = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="tree-nav-node-${id}"]`);
  const isShown = (id: string): boolean => {
    const element = row(id);
    return element !== null && element.style.display !== 'none';
  };

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
  });

  it('shows the top level folded by default', async () => {
    await render();

    expect(isShown('overview')).toBe(true);
    expect(isShown('notes')).toBe(true);
    expect(isShown('details')).toBe(false);
  });

  it('opens every branch when asked to', async () => {
    fixture.componentInstance.expandAll.set(true);
    await render();

    expect(isShown('details')).toBe(true);
    expect(isShown('deep')).toBe(true);
  });

  it('opens the branches above the selected node and marks it', async () => {
    fixture.componentInstance.selectedId.set('deep');
    await render();

    expect(isShown('deep')).toBe(true);
    expect(row('deep')?.classList).toContain('pp-tree-nav__node--selected');
    expect(row('deep')?.querySelector('.pp-tree-nav__label')?.getAttribute('aria-current')).toBe('page');
  });

  it('unfolds a branch with its toggle', async () => {
    await render();

    (row('overview')?.querySelector('.pp-tree-nav__toggle') as HTMLButtonElement).click();
    await render();

    expect(isShown('details')).toBe(true);
  });

  it('reports the node a reader selects', async () => {
    await render();

    (row('notes')?.querySelector('.pp-tree-nav__label') as HTMLButtonElement).click();

    expect(fixture.componentInstance.picked).toEqual(['notes']);
  });

  it('renders the caller’s actions on every row, with the row’s node', async () => {
    await render();

    expect(row('overview')?.querySelector('[data-testid="action-overview"]')).not.toBeNull();
    expect(row('notes')?.querySelector('[data-testid="action-notes"]')).not.toBeNull();
  });

  it('keeps an opened branch open when the caller hands in a rebuilt tree', async () => {
    await render();
    (row('overview')?.querySelector('.pp-tree-nav__toggle') as HTMLButtonElement).click();
    await render();

    fixture.componentInstance.nodes.set(structuredClone(BOOK));
    await render();

    expect(isShown('details')).toBe(true);
  });
});
