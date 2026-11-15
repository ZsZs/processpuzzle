import { WorkingContextDefinition, WorkingContextPersistence } from '@processpuzzle/base-entity';
import { WorkingContextRow } from './app-definition';

/** The `confirmSwitchWhen` key the form's `confirmSwitchStates` control writes. */
const STATE_CONDITION = 'state';

/** Lifts `picker` / `display` / `confirmSwitchWhen` onto the row, where a form control can reach them. */
export function flattenWorkingContext(dto: WorkingContextDefinition): WorkingContextRow {
  const states = dto.confirmSwitchWhen?.[STATE_CONDITION];
  return new WorkingContextRow({
    name: dto.name,
    entityName: dto.entityName,
    parent: dto.parent,
    basePath: dto.basePath,
    persist: dto.persist,
    pickerSearchFields: dto.picker?.searchFields,
    pickerRecent: dto.picker?.recent,
    displayLabel: dto.display?.label,
    displayBadge: dto.display?.badge,
    displayExtra: dto.display?.extra,
    confirmSwitchStates: states === undefined ? undefined : [states].flat().map(String),
    // Kept so nestWorkingContext can merge onto it rather than replace it.
    source: dto,
  });
}

/** The inverse: what the backend takes. Nested objects that would be empty are left out, not sent as `{}`. */
export function nestWorkingContext(row: WorkingContextRow): WorkingContextDefinition {
  const picker = clean({ ...row.source?.picker, searchFields: row.pickerSearchFields?.length ? row.pickerSearchFields : undefined, recent: row.pickerRecent ?? undefined });
  const display = clean({ ...row.source?.display, label: row.displayLabel || undefined, badge: row.displayBadge || undefined, extra: row.displayExtra || undefined });
  // Conditions on other attributes, which only a hand-written definition can carry, survive next to the one the form writes.
  const confirmSwitchWhen = clean({ ...row.source?.confirmSwitchWhen, [STATE_CONDITION]: row.confirmSwitchStates?.length ? row.confirmSwitchStates : undefined });
  return clean({
    ...row.source,
    name: row.name,
    entityName: row.entityName,
    parent: row.parent || undefined,
    basePath: row.basePath || undefined,
    persist: (row.persist || undefined) as WorkingContextPersistence | undefined,
    picker,
    display,
    confirmSwitchWhen,
  }) as WorkingContextDefinition;
}

/** Drops `undefined` members, and an object left with none. */
function clean<T extends object>(value: T): T | undefined {
  const entries = Object.entries(value).filter(([, member]) => member !== undefined);
  return entries.length === 0 ? undefined : (Object.fromEntries(entries) as T);
}
