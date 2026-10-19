import { InjectionToken } from '@angular/core';

const MARGIN_PT = 36;

/**
 * Renders one element — a report page — as an A4 PDF, in the browser: jsPDF's `.html()`, which rasterizes the
 * element through html2canvas and splits it over as many pages as it needs. Both are loaded on first use, so
 * the ~500 KB they weigh is fetched only when somebody signs.
 *
 * What comes out is a picture of the page, not text a reader can select — see the signature design note: server
 * side rendering and PAdES are the follow-up. Elements marked `data-html2canvas-ignore` (the stamp's Sign
 * button, the retry link) are left out of the capture.
 */
export async function renderPdf(element: HTMLElement): Promise<Blob> {
  const { jsPDF } = await import('jspdf');
  const pdf = new jsPDF({ unit: 'pt', format: 'a4', orientation: 'portrait' });
  const width = pdf.internal.pageSize.getWidth() - 2 * MARGIN_PT;
  await pdf.html(element, {
    margin: [MARGIN_PT, MARGIN_PT, MARGIN_PT, MARGIN_PT],
    width,
    windowWidth: Math.max(element.scrollWidth, 794),
    autoPaging: 'text',
    html2canvas: { scale: 1, backgroundColor: '#ffffff', useCORS: true },
  });
  return pdf.output('blob');
}

/** `order-invoice-1001.pdf` from a template slug and the object's label or id; anything unsafe becomes `-`. */
export function reportFileName(templateName: string, subjectLabel: string): string {
  // Split on the unsafe runs and drop the empty pieces, which trims the dashes without a backtracking pattern.
  const base = `${templateName}-${subjectLabel}`
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter(Boolean)
    .join('-');
  return `${base || 'report'}.pdf`;
}

/** The renderer the signing context uses — {@link renderPdf} unless a host or a test provides another. */
export const PDF_RENDERER = new InjectionToken<(element: HTMLElement) => Promise<Blob>>('PDF_RENDERER', { providedIn: 'root', factory: () => renderPdf });
