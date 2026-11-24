import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import type { TutorialLanguage } from './tutorial';

/**
 * The voice-over of one step. Synthesized *before* the recording, because its length is what sizes the step: a
 * sentence takes a different time in every language, and an estimate from a word count is wrong in all of them.
 */
export interface SpeechClip {
  /** The WAV file; absent when no text-to-speech key is configured. */
  file?: string;
  durationMs: number;
}

interface Voice {
  languageCode: string;
  /** A voice of Google Cloud Text-to-Speech; without one the service picks the language's default. */
  name?: string;
}

/** Overridable per language with `TUTORIAL_VOICE_<LANG>`, e.g. `TUTORIAL_VOICE_HU=hu-HU-Wavenet-A`. */
const VOICES: Record<TutorialLanguage, Voice> = {
  en: { languageCode: 'en-US', name: 'en-US-Neural2-F' },
  de: { languageCode: 'de-DE', name: 'de-DE-Neural2-F' },
  fr: { languageCode: 'fr-FR', name: 'fr-FR-Neural2-A' },
  hu: { languageCode: 'hu-HU', name: 'hu-HU-Wavenet-A' },
  es: { languageCode: 'es-ES', name: 'es-ES-Neural2-A' },
};

const SAMPLE_RATE = 24_000;
const ENDPOINT = 'https://texttospeech.googleapis.com/v1/text:synthesize';
/** Reading speed assumed when nothing is synthesized: about 15 characters a second holds across the five languages. */
const ESTIMATED_CHARACTERS_PER_SECOND = 15;

export const speechConfigured = (): boolean => !!process.env['GOOGLE_TTS_API_KEY'];

function voiceOf(language: TutorialLanguage): Voice {
  const override = process.env[`TUTORIAL_VOICE_${language.toUpperCase()}`];
  return override ? { ...VOICES[language], name: override } : VOICES[language];
}

/** Length of a PCM WAV file, read from its header: the `data` chunk's size over the byte rate. */
export function wavDurationMs(wav: Buffer): number {
  const byteRate = wav.readUInt32LE(28);
  let offset = 12;
  while (offset + 8 <= wav.length) {
    const size = wav.readUInt32LE(offset + 4);
    if (wav.toString('ascii', offset, offset + 4) === 'data') {
      return Math.round((Math.min(size, wav.length - offset - 8) / byteRate) * 1000);
    }
    offset += 8 + size + (size % 2);
  }
  throw new Error('not a PCM WAV file: no data chunk');
}

async function request(text: string, voice: Voice): Promise<Response> {
  return fetch(ENDPOINT, {
    method: 'POST',
    // In a header rather than the query string, so the key never lands in a logged URL.
    headers: { 'Content-Type': 'application/json', 'X-Goog-Api-Key': process.env['GOOGLE_TTS_API_KEY'] as string },
    body: JSON.stringify({
      input: { text },
      voice: { languageCode: voice.languageCode, ...(voice.name ? { name: voice.name } : {}) },
      audioConfig: { audioEncoding: 'LINEAR16', sampleRateHertz: SAMPLE_RATE, speakingRate: Number(process.env['TUTORIAL_SPEAKING_RATE'] ?? 1) },
    }),
  });
}

/**
 * Speaks one sentence. Cached by text and voice, so a re-recording after a UI change costs no synthesis, and a
 * changed sentence re-synthesizes that sentence alone.
 *
 * Without `GOOGLE_TTS_API_KEY` the clip has an estimated length and no audio: the scenario still runs and the video
 * is still cut, silent — which is what the smoke run and a first look at a new scenario need.
 */
export async function speak(text: string, language: TutorialLanguage, cacheDirectory: string): Promise<SpeechClip> {
  if (!speechConfigured()) {
    return { durationMs: Math.max(1500, Math.round((text.length / ESTIMATED_CHARACTERS_PER_SECOND) * 1000)) };
  }
  const voice = voiceOf(language);
  const rate = process.env['TUTORIAL_SPEAKING_RATE'] ?? '1';
  const key = createHash('sha256').update(JSON.stringify([text, voice, rate, SAMPLE_RATE])).digest('hex').slice(0, 24);
  const file = path.join(cacheDirectory, `${language}-${key}.wav`);
  if (!existsSync(file)) {
    let response = await request(text, voice);
    // A voice name the service no longer offers must not cost the run: the language's default voice speaks instead.
    if (response.status === 400 && voice.name) {
      console.warn(`[tutorial] voice ${voice.name} was refused; using the default ${voice.languageCode} voice`);
      response = await request(text, { languageCode: voice.languageCode });
    }
    if (!response.ok) throw new Error(`text-to-speech (${language}) answered ${response.status}: ${await response.text()}`);
    mkdirSync(cacheDirectory, { recursive: true });
    writeFileSync(file, Buffer.from(((await response.json()) as { audioContent: string }).audioContent, 'base64'));
  }
  return { file, durationMs: wavDurationMs(readFileSync(file)) };
}
