import { BaseEntityAttrDescriptor, DEFAULT_DATE_TIME_FORMAT, FormControlType } from '@processpuzzle/base-entity';

/**
 * A server-stamped instant — `createdAt`, `startedAt`, `completedAt` — rendered as a date and time in the
 * active language, the way base-entity renders every DATE_TIME attribute, rather than as the raw ISO string.
 *
 * Disabled, because the server sets these and ignores them on write. A DATE control with
 * {@link DEFAULT_DATE_TIME_FORMAT} shows the date and time fields in the form and `ppDate`-formats the
 * list cell.
 */
export function timestampAttr(attrName: string, label: string): BaseEntityAttrDescriptor {
  const attr = new BaseEntityAttrDescriptor(attrName, FormControlType.DATE, label);
  attr.dateFormat = { ...DEFAULT_DATE_TIME_FORMAT };
  attr.disabled = true;
  return attr;
}
