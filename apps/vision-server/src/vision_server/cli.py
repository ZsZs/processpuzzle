"""Phase 1 baseline: run recognition on a local video, without the HTTP service, and measure it.

    vision-baseline start.mp4 --start-list start-list.csv --pattern '^[A-Z]{3} ?[0-9]{1,5}$' --out out/race1

The start list is a CSV of ``candidateId,identifier`` rows (header optional). Without enrollment galleries
matching is by identifier alone — that is the OCR-only baseline of docs/ai/boat-recognition-design.md §10.
Pass ``--present`` with the identifiers truly visible in the video to get accuracy figures.

Writes ``tracks.json``, one ``track-<id>.jpg`` per track and ``report.json`` into --out.
"""

from __future__ import annotations

import argparse
import csv
import json
import logging
import shutil
import sys
import time
import uuid
from pathlib import Path

from vision_server.config import Settings
from vision_server.pipeline.identifiers import compact
from vision_server.pipeline.vision import VisionPipeline
from vision_server.schemas import (
    Candidate,
    DetectionSettings,
    MatchingSettings,
    MediaRef,
    OcrSettings,
    RecognitionJobRequest,
    Requester,
    TrackStatus,
)


class _LocalContext:
    def __init__(self, workdir: Path):
        self._workdir = workdir
        self._last = -1.0

    def progress(self, fraction: float) -> None:
        if fraction - self._last >= 0.05 or fraction >= 1:
            self._last = fraction
            print(f"\r{fraction:5.0%}", end="", file=sys.stderr, flush=True)

    def fetch(self, url: str, max_bytes: int) -> Path:
        return Path(url)


def _start_list(path: Path) -> list[Candidate]:
    candidates = []
    with path.open(newline="", encoding="utf-8") as file:
        for row in csv.reader(file):
            if len(row) < 2 or row[0].strip().lower() == "candidateid":
                continue
            candidates.append(Candidate(candidate_id=row[0].strip(), identifier_text=row[1].strip()))
    if not candidates:
        raise SystemExit(f"{path}: no candidates")
    return candidates


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="vision-baseline", description=__doc__.split("\n")[0])
    parser.add_argument("video", type=Path)
    parser.add_argument("--start-list", type=Path, required=True)
    parser.add_argument("--class", dest="detector_class", default="boat")
    parser.add_argument("--pattern", default=None, help="identifier regex, applied after normalization")
    parser.add_argument("--fps", type=float, default=3.0, help="frames per second sampled")
    parser.add_argument("--min-confidence", type=float, default=0.4)
    parser.add_argument("--present", nargs="*", default=None, help="identifiers truly visible, for accuracy")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    settings = Settings(service_token="cli")
    pipeline = VisionPipeline(settings)
    request = RecognitionJobRequest(
        job_id=uuid.uuid4(),
        requester=Requester(stack="cli", org_key="cli"),
        callback_url="http://localhost/unused",
        callback_token="unused",
        detection=DetectionSettings(detector_class=args.detector_class, min_confidence=args.min_confidence),
        ocr=OcrSettings(identifier_pattern=args.pattern),
        matching=MatchingSettings(sample_fps=args.fps),
        video=MediaRef(media_id="video", url=str(args.video)),
        candidates=_start_list(args.start_list),
    )

    if args.out.exists():
        shutil.rmtree(args.out)
    args.out.mkdir(parents=True)
    started = time.monotonic()
    result = pipeline.recognize(request, _LocalContext(args.out))
    elapsed = time.monotonic() - started
    print(file=sys.stderr)

    for track in result.tracks:
        if track.best_crop:
            (args.out / f"track-{track.track_id}.jpg").write_bytes(track.best_crop.data)
    tracks_json = result.model_dump(
        mode="json", by_alias=True, exclude={"tracks": {"__all__": {"best_crop", "embedding"}}}
    )
    (args.out / "tracks.json").write_text(json.dumps(tracks_json, indent=2), encoding="utf-8")

    report = _report(result, request.candidates, args.present, elapsed)
    (args.out / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


def _report(result, candidates: list[Candidate], present: list[str] | None, elapsed: float) -> dict:
    by_id = {c.candidate_id: compact(c.identifier_text or "") for c in candidates}
    tracks = result.tracks
    auto = [t for t in tracks if t.status == TrackStatus.AUTO_MATCHED]
    report = {
        "seconds": round(elapsed, 1),
        "framesProcessed": result.frames_processed,
        "tracks": len(tracks),
        "tracksWithReading": sum(1 for t in tracks if t.identifier),
        "autoMatched": len(auto),
        "needsReview": len(tracks) - len(auto),
    }
    if present is not None:
        truth = {compact(p) for p in present}
        matched = {by_id[t.candidate_id] for t in auto if t.candidate_id}
        report |= {
            "present": len(truth),
            "correctlyIdentified": len(matched & truth),
            "wrongAutoMatches": len(matched - truth),
            "missed": sorted(truth - matched),
            "identificationRate": round(len(matched & truth) / len(truth), 3) if truth else None,
        }
    return report


if __name__ == "__main__":
    main()
