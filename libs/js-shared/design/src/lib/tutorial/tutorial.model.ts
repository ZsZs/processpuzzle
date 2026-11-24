/** One row of the `tutorial` entity: a narrated video of one designer in one language. See the testbed seed. */
export interface TutorialRow {
  id: string;
  version?: number;
  payload: {
    tutorialKey: string;
    language: string;
    title: string;
    transcript?: string;
    video?: { bucket: string; objectId: string; name: string; mimeType: string } | null;
  };
}

/**
 * The row to show in `language`: that language's own, else English, else the first one there is. A tutorial recorded
 * in some languages only still opens, in the nearest one that exists.
 */
export function pickTutorial(rows: TutorialRow[], language: string, fallback = 'en'): TutorialRow | undefined {
  return rows.find((row) => row.payload.language === language) ?? rows.find((row) => row.payload.language === fallback) ?? rows[0];
}
