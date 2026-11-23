import path from 'node:path';

/** Everything a run leaves behind lies under `tmp`, which is git-ignored. */
const TMP = path.join(__dirname, '..', '..', 'tmp');

/** The signed-in session `sign-in.setup.ts` leaves for the recordings. */
export const STORAGE_STATE = path.join(TMP, 'auth', 'presenter.json');
/** One directory per tutorial and language: the MP4, its captions and its transcript — what `publish` uploads. */
export const OUT_DIRECTORY = path.join(TMP, 'out');
/** Synthesized speech, kept between runs: a re-recording after a UI change costs no synthesis. */
export const SPEECH_CACHE_DIRECTORY = path.join(TMP, 'speech');
