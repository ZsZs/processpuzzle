import { spawnSync } from 'node:child_process';
import { copyFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import type { Cue } from './tutorial';

/** The image used when no ffmpeg is installed; its entry point is `ffmpeg`. */
const FFMPEG_IMAGE = process.env['FFMPEG_IMAGE'] ?? 'linuxserver/ffmpeg:latest';

/**
 * Runs ffmpeg with `directory` as its working directory, so every path it is given is relative to it. That is what
 * lets the same arguments serve an installed ffmpeg (`FFMPEG_PATH`, or `ffmpeg` on the PATH) and the container
 * fallback, which sees nothing but that one directory. Playwright's own ffmpeg will not do: it encodes VP8 only.
 */
function ffmpeg(directory: string, args: string[]): void {
  const local = process.env['FFMPEG_PATH'] ?? 'ffmpeg';
  const installed = spawnSync(local, ['-version'], { stdio: 'ignore' }).status === 0;
  const [command, prefix] = installed ? [local, []] : ['docker', ['run', '--rm', '-v', `${directory}:/work`, '-w', '/work', FFMPEG_IMAGE]];
  const result = spawnSync(command, [...prefix, '-hide_banner', '-loglevel', 'error', ...args], { cwd: directory, encoding: 'utf8' });
  if (result.status !== 0) {
    const runner = installed ? local : `docker ${FFMPEG_IMAGE}`;
    throw new Error(`ffmpeg (${runner}) failed: ${result.error?.message ?? result.stderr}`);
  }
}

export interface Assembly {
  /** Where the raw recording lies and the result is written; ffmpeg's working directory. */
  directory: string;
  /** The raw Playwright recording, relative to `directory`. */
  rawVideo: string;
  /** How much of the raw recording's start to drop: the application loading, before the first step. */
  trimMs: number;
  /** Cue times are relative to the trimmed video. */
  cues: Cue[];
  /** The MP4 to write, relative to `directory`. */
  output: string;
}

/**
 * Cuts the published video: H.264 with the speech laid over it, each clip delayed to the moment its step began.
 * A run without speech (no text-to-speech key) yields the same video without an audio track.
 */
export function assembleVideo({ directory, rawVideo, trimMs, cues, output }: Assembly): void {
  const spoken = cues.filter((cue) => cue.clip);
  mkdirSync(path.join(directory, 'clips'), { recursive: true });
  const inputs = spoken.flatMap((cue, index) => {
    const clip = `clips/${String(index).padStart(2, '0')}.wav`;
    copyFileSync(cue.clip as string, path.join(directory, clip));
    return ['-i', clip];
  });

  const video = ['-c:v', 'libx264', '-crf', '20', '-preset', 'medium', '-pix_fmt', 'yuv420p', '-movflags', '+faststart'];
  if (spoken.length === 0) {
    ffmpeg(directory, ['-y', '-ss', (trimMs / 1000).toFixed(3), '-i', rawVideo, '-an', ...video, output]);
    return;
  }
  const delayed = spoken.map((cue, index) => `[${index + 1}:a]adelay=${cue.startMs}:all=1[a${index}]`);
  const pads = spoken.map((_, index) => '[a' + index + ']').join('');
  const mixed = `${pads}amix=inputs=${spoken.length}:normalize=0:dropout_transition=0[speech]`;
  ffmpeg(directory, [
    '-y',
    ...['-ss', (trimMs / 1000).toFixed(3), '-i', rawVideo],
    ...inputs,
    ...['-filter_complex', [...delayed, mixed].join(';')],
    ...['-map', '0:v', '-map', '[speech]'],
    ...video,
    ...['-c:a', 'aac', '-b:a', '128k'],
    output,
  ]);
}

const pad = (value: number, width = 2) => String(value).padStart(width, '0');

function vttTime(ms: number): string {
  return `${pad(Math.floor(ms / 3_600_000))}:${pad(Math.floor((ms % 3_600_000) / 60_000))}:${pad(Math.floor((ms % 60_000) / 1000))}.${pad(ms % 1000, 3)}`;
}

/**
 * WebVTT, which is what an HTML `<track>` reads. A step's narration is several sentences; each becomes a caption of
 * its own, given a share of the step's spoken time in proportion to its length.
 */
export function toVtt(cues: Cue[], spokenMs: (cue: Cue) => number): string {
  const captions = cues.flatMap((cue) => {
    // Split after a sentence's end mark, keeping the mark: a lookbehind, so the pattern cannot backtrack.
    const parts = cue.text.split(/(?<=[.!?])\s+/).map((sentence) => sentence.trim()).filter(Boolean);
    const sentences = parts.length > 0 ? parts : [cue.text];
    const characters = sentences.reduce((sum, sentence) => sum + sentence.length, 0);
    let start = cue.startMs;
    return sentences.map((sentence) => {
      const end = Math.round(start + (spokenMs(cue) * sentence.length) / characters);
      const caption = `${vttTime(start)} --> ${vttTime(end)}\n${sentence}\n`;
      start = end;
      return caption;
    });
  });
  return `WEBVTT\n\n${captions.join('\n')}`;
}

/** The narration as a readable page: what the designer card offers beside the video. */
export function toTranscript(title: string, cues: Cue[]): string {
  return `# ${title}\n\n${cues.map((cue) => cue.text).join('\n\n')}\n`;
}
