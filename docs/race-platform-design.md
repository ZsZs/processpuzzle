# Race: the platform pieces it needs (design)

Race (Sail Race Control) is shared by every SailRC organization and written by the organizing club. Three
platform pieces are generic and are designed here, so that Race is one use of them and not a special case.

## 1. Snapshot references (Entity Descriptor)

A reference to a record that another owner keeps private (a Boat or a Sailor of another club) cannot be
resolved by the other clubs. A reference attribute may therefore carry a **snapshot**: a copy of selected fields
of the referenced record, stored in the referencing record, next to the reference.

Descriptor (on a REFERENCE attribute with a linked type):

```yaml
- code: boat
  valueKind: REFERENCE
  formControlType: FOREIGN_KEY
  linkedEntityType: sailrc-boat
  snapshot:
    fields: [sailNumber, name, boatClass]
```

- Stored value: `{ "id": "<uuid>", "snapshot": { "sailNumber": "...", ... } }`.
- The snapshot is taken when the referencing record is written. It is not kept up to date: a race keeps the boat as
  it was when the boat was entered (what the results show). A later change to the boat does not rewrite the race.
- Readers who can see the referenced record (its owner) may follow `id`; every other reader shows the snapshot.
- Validation: `fields` must be attributes of the linked type; the snapshot holds only those fields.
- Lists and search over a snapshot use the snapshot's fields.

The same mechanism serves any "copy of a private record" case (a crew member, a club's name on a result).

## 2. Date-time period (value kind and control)

One control for a from/to pair, instead of two date-time controls.

- valueKind `PERIOD`, formControlType `DATE_TIME_PERIOD`.
- Value: `{ "from": "<instant>", "to": "<instant>" }`, both ISO-8601 with offset; `to` is not before `from`.
- Descriptor: `required`, and an optional `granularity` (`DATE` or `DATE_TIME`, default `DATE_TIME`).
- Storage: the two instants as two columns in the entity object's stored payload's indexed projection, so that
  range queries (RSQL on `from`, `to`) keep working.
- The control shows a start and an end picker on one row, and refuses an end before the start.

## 3. Application-wide lists (country, MIME type, and similar)

A list is a shared, read-only, platform-owned set of values: ISO 3166 countries, MIME types, units. Today an
enumeration is fixed in the definition (valueKind `ENUM`), which does not scale.

- A list is a definition of kind `LIST` shipped by the platform (not by a starter, not by an organization):
  `code`, `name`, `entries: [{ code, label (translated), ... }]`.
- Visible to every organization; no organization can write it.
- An attribute refers to a list with formControlType `LOOKUP` and `linkedEntityType: <list code>` (the same
  mechanism as a lookup entity, but the target is a list, so there are no instances to create).
- Validation: the value must be one of the list's entry codes.
- The labels come from the list's translations, in the user's language.

Lists to ship first: `country` (ISO 3166-1 alpha-2), `mime-type`.

## Race, on top of these

- `title` (TEXT, required)
- `period` (PERIOD, required) — replaces fromDate / toDate
- `country` (LOOKUP → list `country`)
- `sailingPlace` (TEXT, or REFERENCE to a Sailing Area when the area is known)
- `organizerClub` (FOREIGN_KEY → Sailing Club)
- `state` (ENUM: planned, completed, canceled)
- `registrations` (EMBEDDED list, owned by the Race)
  - `boat` (FOREIGN_KEY → Boat, with snapshot: sailNumber, name, boatClass)
  - `skipper` (FOREIGN_KEY → Sailor, with snapshot: firstName, lastName, email)
  - `crew` (FOREIGN_KEY → Sailor, multi, with snapshot: firstName, lastName)
- `laps` (EMBEDDED list, ordered by `index`)
  - `index` (NUMBER, required)
  - `courseShape` (ENUM: windward-leeward, triangle, trapezoid, coastal)
  - `state` (ENUM: planned, initialized, countdown, started, running, finished, canceled)
  - `startTime`, `finishTime` (DATE_TIME)
  - `participants` (REFERENCE to registrations, multi)
  - `fieldMarks` (EMBEDDED list)
    - `kind` (LOOKUP → shared lookup `race-field-mark-kind`)
    - `geoposition` (a latitude/longitude value)
- `race-field-mark-kind`: a shared lookup type in the common, with the kinds from the course-marks list.

## Open points

1. The Race is public, so the registrations' snapshots and the Race's laps are visible to every club. A club's
   own boat is in its snapshot once it is entered; the club decides whether to enter it.
2. The lap `participants` refer to registrations of the same race; this is one level of embedding, so the rule of
   "a reference inside an embedded list names the same record's list" needs a check.
3. The geoposition's precision and whether a race's marks are public: they are, with the race.

## Order of work

1. Snapshot references (core: definition, validation, the write-time copy, the reader rule). Tests.
2. The PERIOD value kind and its control (core: definition, validation, storage projection, control). Tests.
3. Application-wide lists (core: a platform-owned LIST kind, LOOKUP to a list, the country and mime-type lists).
4. The Race entity and its lookup (biz: the common), once 1–3 are in.
