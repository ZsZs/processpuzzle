import { describe, expect, it } from 'vitest';
import { datePart, instantToLocal, localToInstant, nextDay, plusMinutes, previousDay, toInstant } from './zoned-time';

const BUDAPEST = 'Europe/Budapest';

describe('zoned-time', () => {
  it('shows an instant as the wall clock of the organization zone, whatever zone the browser is in', () => {
    expect(instantToLocal('2026-12-01T09:00:00Z', BUDAPEST)).toBe('2026-12-01T10:00');
    expect(instantToLocal('2026-07-01T09:00:00Z', BUDAPEST)).toBe('2026-07-01T11:00');
    expect(instantToLocal('2026-12-01T09:00:00Z', 'UTC')).toBe('2026-12-01T09:00');
    expect(instantToLocal('2026-12-01T09:00:00Z', 'America/New_York')).toBe('2026-12-01T04:00');
  });

  it('turns a wall clock back into the instant, winter and summer', () => {
    expect(localToInstant('2026-12-01T10:00', BUDAPEST)).toBe('2026-12-01T09:00:00Z');
    expect(localToInstant('2026-07-01T11:00', BUDAPEST)).toBe('2026-07-01T09:00:00Z');
  });

  it('round-trips a wall clock through an instant', () => {
    for (const local of ['2026-03-01T00:00', '2026-12-31T23:30', '2027-06-15T12:45']) {
      expect(instantToLocal(localToInstant(local, BUDAPEST), BUDAPEST)).toBe(local);
    }
  });

  it('moves a wall-clock time the spring jump skips to the next valid one', () => {
    // Clocks in Budapest go from 02:00 to 03:00 on 2027-03-28: 02:30 does not exist.
    expect(localToInstant('2027-03-28T02:30', BUDAPEST)).toBe('2027-03-28T01:30:00Z');
    expect(instantToLocal('2027-03-28T01:30:00Z', BUDAPEST)).toBe('2027-03-28T03:30');
  });

  it('takes the earlier of the two instants an autumn wall-clock time names', () => {
    // 02:30 happens twice on 2026-10-25 in Budapest; the first is still summer time.
    expect(localToInstant('2026-10-25T02:30', BUDAPEST)).toBe('2026-10-25T00:30:00Z');
  });

  it('reads an ISO value with an offset as that instant and one without as the zone wall clock', () => {
    expect(toInstant('2026-12-01T10:00:00+01:00', BUDAPEST)).toBe('2026-12-01T09:00:00Z');
    expect(toInstant('2026-12-01T09:00:00Z', BUDAPEST)).toBe('2026-12-01T09:00:00Z');
    expect(toInstant('2026-12-01T10:00:00', BUDAPEST)).toBe('2026-12-01T09:00:00Z');
    expect(toInstant('2026-12-01T10:00', BUDAPEST)).toBe('2026-12-01T09:00:00Z');
  });

  it('does the day arithmetic across month and year ends', () => {
    expect(nextDay('2026-12-31')).toBe('2027-01-01');
    expect(nextDay('2028-02-28')).toBe('2028-02-29');
    expect(previousDay('2027-01-01')).toBe('2026-12-31');
    expect(datePart('2026-12-01T10:00:00+01:00')).toBe('2026-12-01');
    expect(datePart('2026-12-01')).toBe('2026-12-01');
    expect(plusMinutes('2026-12-01T09:00:00Z', 90)).toBe('2026-12-01T10:30:00Z');
  });
});
