/**
 * Prints one element — a rendered report — through the browser's print dialog, where *Save as PDF* is the PDF
 * export of the first version.
 *
 * Through a hidden iframe holding a copy of the element and of the page's style sheets, rather than
 * `window.print()` on the page with a print style sheet: the application shell around the report (header,
 * sidenav, tabs) is not this library's to hide, and a copy prints exactly what was rendered — resolved values
 * included — with nothing else on the page.
 */
export function printElement(element: HTMLElement, title: string): void {
  const frame = document.createElement('iframe');
  frame.setAttribute('aria-hidden', 'true');
  frame.style.position = 'fixed';
  frame.style.width = '0';
  frame.style.height = '0';
  frame.style.border = '0';
  document.body.appendChild(frame);

  const printed = frame.contentDocument;
  const printWindow = frame.contentWindow;
  if (!printed || !printWindow) {
    frame.remove();
    return;
  }
  printed.title = title;
  for (const style of Array.from(document.querySelectorAll('style, link[rel="stylesheet"]'))) {
    printed.head.appendChild(style.cloneNode(true));
  }
  const pageStyle = printed.createElement('style');
  pageStyle.textContent = '@page { margin: 18mm 16mm; } body { margin: 0; background: #fff; }';
  printed.head.appendChild(pageStyle);
  printed.body.appendChild(element.cloneNode(true));

  // Linked style sheets load asynchronously in the copy; printing before they have would print unstyled.
  const cleanUp = () => frame.remove();
  printWindow.addEventListener('afterprint', cleanUp, { once: true });
  setTimeout(() => {
    printWindow.focus();
    printWindow.print();
  }, PRINT_DELAY_MS);
}

const PRINT_DELAY_MS = 250;
