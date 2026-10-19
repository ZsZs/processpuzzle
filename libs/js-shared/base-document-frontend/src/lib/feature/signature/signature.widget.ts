import { Provider } from '@angular/core';
import { provideWidget, WidgetRegistration } from '@processpuzzle/widgets';
import { SignatureStampComponent } from './signature-stamp.component';

export const SIGNATURE_WIDGET = 'signature';

/**
 * Lives here, not in base-widget-frontend: it needs a document's signing context, so — like `document-viewer`
 * (see `widget-registry.token.ts`) — it belongs to the aggregator and registers itself through `provideWidget`.
 * A template opts into signing by embedding it; nothing else about the template changes.
 */
export const SIGNATURE_WIDGET_REGISTRATION: WidgetRegistration = {
  type: SIGNATURE_WIDGET,
  component: SignatureStampComponent,
  definition: {
    name: 'Signature',
    translocoId: 'base_document.signature.name',
    description: 'Where a report is signed: a Sign button, then a stamp with the signer, the date and the signature id. The signed report is stored as a PDF.',
    category: 'Document',
    icon: 'draw',
    propsSchema: {
      type: 'object',
      properties: {
        label: { type: 'string', title: 'Label', description: 'Caption of the stamp, e.g. "Signed by".' },
        role: { type: 'string', title: 'Role', description: 'The role the signature is for, shown as "for <role>".' },
        stampText: { type: 'string', title: 'Stamp text', description: 'A word across the signed stamp, e.g. APPROVED.' },
      },
      additionalProperties: false,
    },
  },
};

export function provideSignatureWidget(): Provider[] {
  return provideWidget(SIGNATURE_WIDGET_REGISTRATION);
}
