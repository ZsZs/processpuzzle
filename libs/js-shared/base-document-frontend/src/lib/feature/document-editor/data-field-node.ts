import { effect, EffectRef, EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { formatValue } from '@processpuzzle/widgets';
import { mergeAttributes, Node } from '@tiptap/core';
import { Node as ProseMirrorNode } from '@tiptap/pm/model';
import { NodeView } from '@tiptap/pm/view';

export const DATA_FIELD_NODE = 'dataField';

export interface DataFieldNodeDeps {
  environmentInjector: EnvironmentInjector;
  /** Read inside an effect, so a value that arrives after the node is drawn redraws it. */
  resolveBinding: (path: string) => unknown;
  /** The locale the value is formatted for — the document translation's. */
  locale: () => string;
  /** Whether the block is being authored: an unresolved field then shows the path it names. */
  editable: boolean;
}

/**
 * One value of an input port inside the prose — "Dear {order.customerName}," — stored in the block's Tiptap
 * JSON as `{ type: 'dataField', attrs: { path, format } }`.
 *
 * Inline and atomic: it flows with the text and inherits its marks' look, and the caret steps over it as one
 * character. It is not a widget — no props schema, no ports, no registry entry — because all it does is print
 * a value, and the path is the whole of its configuration.
 */
export function createDataFieldExtension(deps: DataFieldNodeDeps) {
  return Node.create({
    name: DATA_FIELD_NODE,
    group: 'inline',
    inline: true,
    atom: true,
    selectable: true,

    addAttributes() {
      return {
        path: { default: null },
        format: { default: 'text' },
      };
    },

    parseHTML() {
      return [
        {
          tag: 'span[data-field-path]',
          getAttrs: (el) => ({ path: (el as HTMLElement).getAttribute('data-field-path'), format: (el as HTMLElement).getAttribute('data-field-format') ?? 'text' }),
        },
      ];
    },

    renderHTML({ HTMLAttributes }) {
      return ['span', mergeAttributes({ 'data-field-path': HTMLAttributes['path'], 'data-field-format': HTMLAttributes['format'] }), `{${HTMLAttributes['path']}}`];
    },

    addNodeView() {
      return (props: { node: ProseMirrorNode }) => new DataFieldNodeView(props.node, deps);
    },
  });
}

export class DataFieldNodeView implements NodeView {
  readonly dom: HTMLElement;
  private readonly effectRef: EffectRef;

  constructor(
    node: ProseMirrorNode,
    deps: DataFieldNodeDeps,
  ) {
    const path = String(node.attrs['path'] ?? '');
    const format = String(node.attrs['format'] ?? 'text');
    this.dom = document.createElement('span');
    this.dom.classList.add('pp-data-field');
    this.dom.setAttribute('data-field-path', path);
    this.dom.setAttribute('data-testid', `data-field-${path}`);
    this.effectRef = runInInjectionContext(deps.environmentInjector, () =>
      effect(() => {
        const text = formatValue(deps.resolveBinding(path), format, deps.locale());
        const unresolved = text === '';
        this.dom.classList.toggle('pp-data-field--unresolved', unresolved && deps.editable);
        this.dom.textContent = unresolved && deps.editable ? `{${path}}` : text;
      }),
    );
  }

  /** Attributes never change in place — a new path is a new node — so ProseMirror may always redraw. */
  update(): boolean {
    return false;
  }

  ignoreMutation(): boolean {
    return true;
  }

  destroy(): void {
    this.effectRef.destroy();
  }
}
