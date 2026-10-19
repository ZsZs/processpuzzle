import { describe, expect, it } from 'vitest';
import { reportFileName } from './report-pdf';

describe('reportFileName', () => {
  it('joins the template and the object, lower-cased', () => {
    expect(reportFileName('order-invoice', '1001')).toBe('order-invoice-1001.pdf');
  });

  it('turns everything unsafe in a file name into one dash', () => {
    expect(reportFileName('Order Invoice', 'a/b: c')).toBe('order-invoice-a-b-c.pdf');
  });

  it('never yields an empty name', () => {
    expect(reportFileName('', '')).toBe('report.pdf');
  });
});
