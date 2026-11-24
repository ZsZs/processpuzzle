/** The attribute that carries a row's language, as an ISO code such as `en` or `hu`. Rows without it are never picked by language. */
export const LANGUAGE_ATTRIBUTE = 'language';

/** The row shape the player reads: its own attributes, by name. */
export interface VideoPlayerRow {
  id: string;
  version?: number;
  payload: Record<string, unknown>;
}

/** A stored upload as an ARTIFACT attribute holds it. */
export interface StoredObject {
  bucket: string;
  objectId: string;
}

/**
 * The row to show in `language`: that language's own, else English, else the first one there is. A tutorial recorded
 * in some languages only still opens, in the nearest one that exists.
 */
export function pickVideoRow(rows: VideoPlayerRow[], language: string, fallback = 'en'): VideoPlayerRow | undefined {
  const inLanguage = (wanted: string) => rows.find((row) => row.payload[LANGUAGE_ATTRIBUTE] === wanted);
  return inLanguage(language) ?? inLanguage(fallback) ?? rows[0];
}

/** The stored object an attribute holds, when it holds one. */
export function storedObjectOf(row: VideoPlayerRow | undefined, attribute: string): StoredObject | undefined {
  const value = row?.payload[attribute] as Partial<StoredObject> | null | undefined;
  return value?.bucket && value.objectId ? (value as StoredObject) : undefined;
}
