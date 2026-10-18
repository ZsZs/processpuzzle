import { afterRenderEffect, Component, contentChild, Directive, inject, input, output, TemplateRef, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTree, MatTreeNode, MatTreeNodeDef, MatTreeNodePadding, MatTreeNodeToggle } from '@angular/material/tree';

/** One entry of a {@link TreeNavComponent}. Plain data, so any feature can build it from its own model. */
export interface TreeNavNode {
  id: string;
  label: string;
  icon?: string;
  children?: TreeNavNode[];
}

/**
 * Marks the per-node actions of a {@link TreeNavComponent}: `<ng-template ppTreeNavNodeActions let-node>`.
 * Rendered at the end of every row, so a feature adds its own verbs without the tree knowing what they do.
 */
@Directive({ selector: 'ng-template[ppTreeNavNodeActions]' })
export class TreeNavNodeActionsDirective {
  readonly template = inject<TemplateRef<{ $implicit: TreeNavNode }>>(TemplateRef);
}

/**
 * A navigation tree: rows that select, branches that fold. Built on Material's `MatTree` with a
 * `childrenAccessor` — the nested data is rendered flat, indented by level — and keyed by node id, so a
 * branch stays open when the caller hands in a rebuilt tree after an edit.
 */
@Component({
  selector: 'pp-tree-nav',
  template: `
    <mat-tree #tree class="pp-tree-nav" [dataSource]="nodes()" [childrenAccessor]="childrenOf" [expansionKey]="keyOf" [trackBy]="trackById">
      <mat-tree-node
        *matTreeNodeDef="let node"
        matTreeNodePadding
        [matTreeNodePaddingIndent]="16"
        [isExpandable]="!!node.children?.length"
        class="pp-tree-nav__node"
        [class.pp-tree-nav__node--selected]="node.id === selectedId()"
        [attr.data-testid]="'tree-nav-node-' + node.id"
      >
        <button mat-icon-button matTreeNodeToggle type="button" class="pp-tree-nav__toggle" [class.pp-tree-nav__toggle--leaf]="!node.children?.length" [attr.aria-label]="node.label">
          <mat-icon>{{ tree.isExpanded(node) ? 'expand_more' : 'chevron_right' }}</mat-icon>
        </button>
        <button type="button" class="pp-tree-nav__label" [attr.aria-current]="node.id === selectedId() ? 'page' : null" (click)="selected.emit(node)">
          @if (node.icon) {
            <mat-icon class="pp-tree-nav__icon">{{ node.icon }}</mat-icon>
          }
          <span>{{ node.label }}</span>
        </button>
        @if (actions(); as actions) {
          <span class="pp-tree-nav__actions">
            <ng-container *ngTemplateOutlet="actions.template; context: { $implicit: node }" />
          </span>
        }
      </mat-tree-node>
    </mat-tree>
  `,
  styles: `
    :host {
      display: block;
    }
    .pp-tree-nav {
      background: transparent;
    }
    .pp-tree-nav__node {
      color: inherit;
      gap: 2px;
      min-height: 36px;
    }
    .pp-tree-nav__toggle--leaf {
      visibility: hidden;
    }
    .pp-tree-nav__label {
      align-items: center;
      background: none;
      border: none;
      border-radius: 4px;
      color: inherit;
      cursor: pointer;
      display: flex;
      flex: 1;
      font: inherit;
      gap: 6px;
      min-width: 0;
      padding: 6px 8px;
      text-align: start;
    }
    .pp-tree-nav__label span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .pp-tree-nav__label:hover,
    .pp-tree-nav__label:focus-visible {
      background: color-mix(in srgb, currentColor 10%, transparent);
    }
    .pp-tree-nav__node--selected .pp-tree-nav__label {
      background: color-mix(in srgb, currentColor 18%, transparent);
      font-weight: 500;
    }
    .pp-tree-nav__icon {
      font-size: 18px;
      height: 18px;
      width: 18px;
    }
    .pp-tree-nav__actions {
      display: inline-flex;
    }
  `,
  imports: [MatTree, MatTreeNode, MatTreeNodeDef, MatTreeNodePadding, MatTreeNodeToggle, MatIconButton, MatIcon, NgTemplateOutlet],
})
export class TreeNavComponent {
  readonly nodes = input<TreeNavNode[]>([]);
  readonly selectedId = input<string | undefined>(undefined);
  /** Opens every branch the first time the tree has nodes; later the reader's own folding is kept. */
  readonly expandAll = input(false);
  readonly selected = output<TreeNavNode>();

  protected readonly actions = contentChild(TreeNavNodeActionsDirective);
  private readonly tree = viewChild.required<MatTree<TreeNavNode, string>>('tree');
  private expandedInitially = false;

  protected readonly childrenOf = (node: TreeNavNode): TreeNavNode[] => node.children ?? [];
  protected readonly keyOf = (node: TreeNavNode): string => node.id;
  protected readonly trackById = (_index: number, node: TreeNavNode): string => node.id;

  constructor() {
    // After render, so the tree has taken in the nodes it is asked to expand.
    afterRenderEffect(() => {
      const nodes = this.nodes();
      const tree = this.tree();
      if (nodes.length === 0) return;

      if (this.expandAll() && !this.expandedInitially) {
        this.expandedInitially = true;
        tree.expandAll();
      }
      this.ancestorsOf(nodes, this.selectedId()).forEach((ancestor) => tree.expand(ancestor));
    });
  }

  /** The branches above the selected node, so a deep link never lands on a row hidden in a folded branch. */
  private ancestorsOf(nodes: TreeNavNode[], id: string | undefined, path: TreeNavNode[] = []): TreeNavNode[] {
    if (!id) return [];
    for (const node of nodes) {
      if (node.id === id) return path;
      const found = this.ancestorsOf(node.children ?? [], id, [...path, node]);
      if (found.length > 0) return found;
    }
    return [];
  }
}
