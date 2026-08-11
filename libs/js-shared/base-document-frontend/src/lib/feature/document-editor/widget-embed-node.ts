import { createComponent, EnvironmentInjector, Signal, Type, effect, runInInjectionContext } from '@angular/core';
import { Node, mergeAttributes } from '@tiptap/core';
import { Node as ProseMirrorNode } from '@tiptap/pm/model';
import { NodeView } from '@tiptap/pm/view';
import { DocumentBlock, WidgetPlacement } from '../../domain/base-document';

/**
 * A widget-typed prop the mounted component reads, roughly mirroring what a base-app widget
 * component already expects: `props` (the block's static config) plus resolved values for any
 * `inputBindings`. Resolving a binding to an actual value is the frontend's job per the
 * contract's own scope boundary — this file only wires whatever `resolveBinding` returns onto
 * the mounted component's inputs, it never decides what a binding *means*.
 */
export interface WidgetEmbedHostProps {
  props: Record<string, unknown>;
  resolveBinding: (portName: string) => unknown;
}

export interface WidgetEmbedNodeDeps {
  environmentInjector: EnvironmentInjector;
  widgetRegistry: ReadonlyMap<string, Type<unknown>>;
  /** Kept live so a NodeView already on screen re-resolves if the referenced block's props change — e.g. the Properties-adjacent port list, or a future prop-editing panel. */
  blocksById: Signal<ReadonlyMap<string, DocumentBlock>>;
  resolveBinding: (portName: string) => unknown;
}

const NODE_NAME = 'widgetEmbed';

export function createWidgetEmbedExtension(deps: WidgetEmbedNodeDeps) {
  return Node.create({
    name: NODE_NAME,
    group: 'block',
    atom: true, // no editable ProseMirror content inside — the mounted component owns its own DOM entirely
    selectable: true,
    draggable: true,

    addAttributes() {
      return {
        blockId: { default: null },
      };
    },

    parseHTML() {
      return [{ tag: `div[data-widget-embed-block-id]`, getAttrs: (el) => ({ blockId: (el as HTMLElement).getAttribute('data-widget-embed-block-id') }) }];
    },

    renderHTML({ HTMLAttributes }) {
      // Only reached for read-only/exported rendering, e.g. a non-editable TEXT block
      // (DocumentBlock.editable === false) or server-side export — the live NodeView below is
      // what actually renders inside the running editor.
      return ['div', mergeAttributes(HTMLAttributes, { 'data-widget-embed-block-id': HTMLAttributes['blockId'] })];
    },

    addNodeView() {
      return (props: { node: ProseMirrorNode }) => new WidgetEmbedNodeView(props.node, deps);
    },
  });
}

class WidgetEmbedNodeView implements NodeView {
  readonly dom: HTMLElement;
  readonly contentDOM = undefined; // atom node: ProseMirror never renders/edits content inside this node

  private mountPoint: HTMLElement;
  private componentRef: ReturnType<typeof createComponent> | null = null;
  private currentBlockId: string | null = null;
  private effectRef: ReturnType<typeof effect> | null = null;

  constructor(private node: ProseMirrorNode, private deps: WidgetEmbedNodeDeps) {
    this.dom = document.createElement('div');
    this.dom.classList.add('pp-widget-embed');
    this.mountPoint = document.createElement('div');
    this.dom.appendChild(this.mountPoint);

    // runInInjectionContext because we're outside any component's constructor here — Tiptap
    // constructs NodeViews itself, not Angular's DI. effect() re-runs this whenever the
    // referenced block changes, which covers both "props edited elsewhere" and "block deleted
    // out from under an embed still on screen" without any manual subscription bookkeeping.
    this.effectRef = runInInjectionContext(deps.environmentInjector, () =>
      effect(() => this.render(deps.blocksById())),
    );
  }

  private render(blocksById: ReadonlyMap<string, DocumentBlock>) {
    const blockId = this.node.attrs['blockId'] as string | null;
    const block = blockId ? blocksById.get(blockId) : undefined;

    if (this.componentRef && this.currentBlockId === blockId && block) {
      // Same block, still resolvable — just push fresh props, no need to tear down and remount.
      this.applyProps(block);
      return;
    }

    this.teardown();
    this.currentBlockId = blockId;

    if (!blockId) {
      this.renderProblem('This embedded widget has no target — re-select a widget block.');
      return;
    }
    if (!block || block.placement !== WidgetPlacement.REFERENCED) {
      // Mirrors the backend's dangling-widget-embed check: editing stays possible even with a
      // broken reference, since validateDocument/replaceDocumentBlock will catch it on save —
      // the editor shouldn't be the thing that blocks the user from fixing their own document.
      this.renderProblem(`Widget block '${blockId}' is missing or not REFERENCED.`);
      return;
    }

    const component = block.type ? this.deps.widgetRegistry.get(block.type) : undefined;
    if (!component) {
      this.renderProblem(`No widget registered for type '${block.type}'.`);
      return;
    }

    this.componentRef = createComponent(component, { environmentInjector: this.deps.environmentInjector });
    this.applyProps(block);
    this.mountPoint.replaceChildren(this.componentRef.location.nativeElement);
    this.componentRef.changeDetectorRef.detectChanges();
  }

  private applyProps(block: DocumentBlock) {
    if (!this.componentRef) return;
    const resolveBinding = (widgetPropName: string) => {
      const portName = block.inputBindings?.[widgetPropName];
      return portName ? this.deps.resolveBinding(portName) : undefined;
    };
    // Every declared input binding wins over the matching static prop, if any — a bound value
    // is the document author saying "this one comes from the host," not a fallback.
    const resolvedProps: Record<string, unknown> = { ...block.props };
    for (const widgetPropName of Object.keys(block.inputBindings ?? {})) {
      resolvedProps[widgetPropName] = resolveBinding(widgetPropName);
    }
    for (const [key, value] of Object.entries(resolvedProps)) {
      this.componentRef.setInput(key, value);
    }
    this.componentRef.changeDetectorRef.detectChanges();
  }

  private renderProblem(message: string) {
    const placeholder = document.createElement('div');
    placeholder.classList.add('pp-widget-embed-broken');
    placeholder.textContent = message;
    this.mountPoint.replaceChildren(placeholder);
  }

  private teardown() {
    this.componentRef?.destroy();
    this.componentRef = null;
  }

  // ProseMirror calls update() when the node's attrs might have changed (e.g. undo/redo moved
  // a different widgetEmbed into this position) — returning false tells ProseMirror to destroy
  // and recreate the NodeView instead of reusing this instance, since the effect() above already
  // handles same-node prop changes on its own.
  update(node: ProseMirrorNode): boolean {
    if (node.type.name !== NODE_NAME) return false;
    this.node = node;
    return node.attrs['blockId'] === this.currentBlockId;
  }

  selectNode() {
    this.dom.classList.add('pp-widget-embed--selected');
  }

  deselectNode() {
    this.dom.classList.remove('pp-widget-embed--selected');
  }

  destroy() {
    this.effectRef?.destroy();
    this.teardown();
  }
}
