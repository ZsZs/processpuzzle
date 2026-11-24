import { readFileSync } from 'node:fs';
import path from 'node:path';
import { parse } from 'yaml';
import type { Tutorial, TutorialLanguage } from './tutorial';

/** One `narration.<lang>.yaml`: everything of a tutorial that is language, and nothing that is behaviour. */
export interface Narration {
  title: string;
  /** What the scenario types — names and descriptions, in this language. Codes stay ASCII in the scenario itself. */
  values: Record<string, string>;
  /** Step id → what the voice-over says during that step. */
  steps: Record<string, string>;
}

export const TUTORIALS_DIRECTORY = path.join(__dirname, '..', 'tutorials');

/**
 * Reads a tutorial's narration and holds it against the scenario: a step without a sentence, or a sentence without a
 * step, fails here rather than as a silent gap in one of five videos.
 */
export function loadNarration(tutorial: Tutorial, language: TutorialLanguage): Narration {
  const file = path.join(TUTORIALS_DIRECTORY, tutorial.name, `narration.${language}.yaml`);
  const raw = parse(readFileSync(file, 'utf8')) as Partial<Narration> | null;
  const narration: Narration = { title: raw?.title ?? '', values: raw?.values ?? {}, steps: raw?.steps ?? {} };

  const expected = tutorial.steps.map((step) => step.id);
  const missing = expected.filter((id) => !narration.steps[id]?.trim());
  const unknown = Object.keys(narration.steps).filter((id) => !expected.includes(id));
  if (!narration.title.trim() || missing.length > 0 || unknown.length > 0) {
    const problems = [
      narration.title.trim() ? '' : 'no title',
      missing.length ? `no narration for ${missing.join(', ')}` : '',
      unknown.length ? `narration for unknown steps ${unknown.join(', ')}` : '',
    ].filter(Boolean);
    throw new Error(`${file}: ${problems.join('; ')}`);
  }
  for (const id of expected) narration.steps[id] = narration.steps[id].replace(/\s+/g, ' ').trim();
  return narration;
}
