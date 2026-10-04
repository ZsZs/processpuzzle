"""The real Pipeline: enrollment, re-embedding and recognition. See docs/ai/boat-recognition-design.md §3."""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from functools import cached_property

import numpy as np

from vision_server.config import Settings
from vision_server.pipeline import JobContext
from vision_server.pipeline import encoding as enc
from vision_server.pipeline import frames as fr
from vision_server.pipeline.identifiers import Reading, accept, vote
from vision_server.pipeline.matching import CandidateEvidence, TrackEvidence, assign
from vision_server.pipeline.models import Detection, Models
from vision_server.schemas import (
    CandidateScore,
    Crop,
    Embedding,
    EmbeddingJobRequest,
    EmbeddingJobResult,
    EmbeddingResult,
    EnrollmentJobRequest,
    EnrollmentJobResult,
    EnrollmentPhotoResult,
    EnrollmentPhotoStatus,
    IdentifierReading,
    OcrSettings,
    RecognitionJobRequest,
    RecognitionJobResult,
    TrackResult,
    TrackStatus,
    VisionModels,
    VisionModelsDetector,
    VisionModelsEmbedding,
    VisionModelsOcr,
)

log = logging.getLogger(__name__)

# A photo is AMBIGUOUS when its second-largest detection is at least this fraction of the largest.
AMBIGUITY_RATIO = 0.6
ENROLLMENT_CROP_SIDE = 1024
TRACK_KEPT_CROPS = 5
TRACK_KEPT_CROP_SIDE = 1280
TRACK_BEST_CROP_SIDE = 640
MIN_TRACK_SIGHTINGS = 2
# The one track a frames recognition yields.
FRAMES_TRACK_ID = 1
# Fraction of a recognition job's progress spent on the video pass; the rest is per-track OCR and embedding.
VIDEO_PASS_SHARE = 0.7


@dataclass(frozen=True)
class _ModelInfo:
    detector_classes: list[str]
    embedding_dimension: int


class VisionPipeline:
    def __init__(self, settings: Settings, models: Models | None = None):
        self._settings = settings
        self._models = models or Models(settings)

    # ── Pipeline protocol ──────────────────────────────────────────

    def models(self) -> VisionModels:
        info = self._info
        return VisionModels(
            detector=VisionModelsDetector(model=self._settings.detector_model, classes=info.detector_classes),
            embedding=VisionModelsEmbedding(model=self._settings.embedding_model, dimension=info.embedding_dimension),
            ocr=VisionModelsOcr(model="EasyOCR"),
            device=self._settings.device,
        )

    def enroll(self, request: EnrollmentJobRequest, ctx: JobContext) -> EnrollmentJobResult:
        detector = self._models.detector()
        class_id = detector.class_id(request.detection.detector_class)
        results = []
        for done, photo in enumerate(request.photos):
            ctx.progress(done / len(request.photos))
            try:
                image = fr.decode_image(ctx.fetch(photo.url, self._settings.max_photo_bytes).read_bytes())
                detections = detector.detect(image, class_id, request.detection.min_confidence)
                results.append(self._enroll_photo(photo.media_id, image, detections, request.ocr))
            except ValueError as error:
                results.append(
                    EnrollmentPhotoResult(
                        media_id=photo.media_id, status=EnrollmentPhotoStatus.FAILED, failure_reason=str(error)
                    )
                )
        return EnrollmentJobResult(job_id=request.job_id, photos=results)

    def embed(self, request: EmbeddingJobRequest, ctx: JobContext) -> EmbeddingJobResult:
        embedder = self._models.embedder()
        results = []
        for done, item in enumerate(request.crops):
            ctx.progress(done / len(request.crops))
            try:
                image = fr.decode_image(ctx.fetch(item.url, self._settings.max_photo_bytes).read_bytes())
                results.append(
                    EmbeddingResult(media_id=item.media_id, embedding=self._embedding(embedder.embed([image])[0]))
                )
            except ValueError as error:
                results.append(EmbeddingResult(media_id=item.media_id, failure_reason=str(error)))
        return EmbeddingJobResult(job_id=request.job_id, embeddings=results)

    def recognize(self, request: RecognitionJobRequest, ctx: JobContext) -> RecognitionJobResult:
        started = time.monotonic()
        if request.frames is not None:
            subject = self._frames_track(request, ctx)
            frames = len(request.frames)
            kept = [subject] if subject.sightings else []
            log.info("frames pass: %d frames, subject %s", frames, "seen" if kept else "not seen")
        else:
            path = str(ctx.fetch(request.video.url, self._settings.max_video_bytes))
            tracks, frames = self._track_video(path, request, ctx)
            kept = [t for t in tracks.values() if t.sightings >= MIN_TRACK_SIGHTINGS]
            log.info("video pass: %d frames, %d tracks (%d kept)", frames, len(tracks), len(kept))

        evidence, extras = [], {}
        for done, track in enumerate(kept):
            ctx.progress(VIDEO_PASS_SHARE + (1 - VIDEO_PASS_SHARE) * done / max(1, len(kept)))
            crops = track.best()
            reading = self._read_identifier(crops, request.ocr)
            vector = enc.normalize(self._models.embedder().embed(crops).mean(axis=0))
            evidence.append(TrackEvidence(track.track_id, reading.text if reading else None, vector))
            extras[track.track_id] = (track, crops[0], reading, vector)

        candidates = [
            CandidateEvidence(
                c.candidate_id,
                c.identifier_text,
                enc.gallery_matrix([enc.from_bytes(e.vector) for e in c.gallery]),
            )
            for c in request.candidates
        ]
        matching = request.matching
        verdicts = assign(
            evidence, candidates, matching.identifier_weight, matching.accept_score, matching.accept_margin
        )

        results = []
        for verdict in verdicts:
            track, best_crop, reading, vector = extras[verdict.track_id]
            results.append(
                TrackResult(
                    track_id=verdict.track_id,
                    status=TrackStatus.AUTO_MATCHED if verdict.auto_matched else TrackStatus.NEEDS_REVIEW,
                    candidate_id=verdict.candidate_id,
                    score=verdict.score,
                    identifier=IdentifierReading(text=reading.text, confidence=reading.confidence) if reading else None,
                    best_crop=self._crop(best_crop, TRACK_BEST_CROP_SIDE),
                    embedding=self._embedding(vector),
                    first_seen_ms=track.first_ms,
                    last_seen_ms=track.last_ms,
                    candidates=[
                        CandidateScore(
                            candidate_id=p.candidate_id,
                            score=p.score,
                            identifier_score=p.identifier_score,
                            embedding_score=p.embedding_score,
                        )
                        for p in verdict.candidates
                    ],
                )
            )
        results.sort(key=lambda r: r.first_seen_ms)
        return RecognitionJobResult(
            job_id=request.job_id,
            frames_processed=frames,
            processing_seconds=round(time.monotonic() - started, 1),
            tracks=results,
        )

    def release(self) -> None:
        self._models.release()

    # ── Steps ──────────────────────────────────────────────────────

    def _track_video(self, path: str, request: RecognitionJobRequest, ctx: JobContext) -> tuple[dict, int]:
        """Detect and track through the sampled frames, keeping each track's best crops."""
        import supervision as sv

        detector = self._models.detector()
        class_id = detector.class_id(request.detection.detector_class)
        info = fr.video_info(path)
        sample_fps = request.matching.sample_fps
        expected = max(1, int(info.frame_count / max(1, round(info.fps / sample_fps))))
        # lost_track_buffer is in frames at 30 fps and scaled by frame_rate inside ByteTrack: 150 keeps a
        # track alive through about five seconds of occlusion, at any sampling rate.
        tracker = sv.ByteTrack(frame_rate=max(1, round(sample_fps)), lost_track_buffer=150)
        tracks: dict[int, fr.TrackCrops] = {}
        processed = 0
        for frame in fr.sample_frames(path, sample_fps):
            detections = detector.detect(frame.image, class_id, request.detection.min_confidence)
            tracked = tracker.update_with_detections(_to_supervision(sv, detections))
            for box, track_id in zip(tracked.xyxy, tracked.tracker_id, strict=True):
                track = tracks.setdefault(int(track_id), fr.TrackCrops(int(track_id), TRACK_KEPT_CROPS))
                track.offer(frame.image, box, frame.millis, TRACK_KEPT_CROP_SIDE)
            processed += 1
            ctx.progress(VIDEO_PASS_SHARE * min(1.0, processed / expected))
        return tracks, processed

    def _frames_track(self, request: RecognitionJobRequest, ctx: JobContext) -> fr.TrackCrops:
        """Shots of one subject: the largest detection of each frame is a crop of it. No tracking needed."""
        detector = self._models.detector()
        class_id = detector.class_id(request.detection.detector_class)
        track = fr.TrackCrops(FRAMES_TRACK_ID, TRACK_KEPT_CROPS)
        for done, ref in enumerate(request.frames):
            ctx.progress(VIDEO_PASS_SHARE * done / len(request.frames))
            try:
                image = fr.decode_image(ctx.fetch(ref.url, self._settings.max_photo_bytes).read_bytes())
            except ValueError as error:
                log.warning("frame %s skipped: %s", ref.media_id, error)
                continue
            detections = detector.detect(image, class_id, request.detection.min_confidence)
            if detections:
                largest = max(detections, key=lambda d: _area(d.box))
                track.offer(image, largest.box, 0, TRACK_KEPT_CROP_SIDE)
        return track

    def _enroll_photo(
        self, media_id: str, image: np.ndarray, detections: list[Detection], ocr: OcrSettings | None
    ) -> EnrollmentPhotoResult:
        if not detections:
            return EnrollmentPhotoResult(media_id=media_id, status=EnrollmentPhotoStatus.NO_SUBJECT)
        by_area = sorted(detections, key=lambda d: _area(d.box), reverse=True)
        if len(by_area) > 1 and _area(by_area[1].box) >= AMBIGUITY_RATIO * _area(by_area[0].box):
            return EnrollmentPhotoResult(media_id=media_id, status=EnrollmentPhotoStatus.AMBIGUOUS)
        subject = fr.crop(image, by_area[0].box)
        reading = self._read_identifier([subject], ocr)
        return EnrollmentPhotoResult(
            media_id=media_id,
            status=EnrollmentPhotoStatus.ENROLLED,
            crop=self._crop(subject, ENROLLMENT_CROP_SIDE),
            embedding=self._embedding(self._models.embedder().embed([subject])[0]),
            identifier=IdentifierReading(text=reading.text, confidence=reading.confidence) if reading else None,
        )

    def _read_identifier(self, crops: list[np.ndarray], ocr: OcrSettings | None) -> Reading | None:
        if ocr is None:
            return None
        reader = self._models.ocr()
        readings = [r for image in crops for r in accept(reader.read(image), ocr.identifier_pattern)]
        return vote(readings, len(crops))

    def _embedding(self, vector: np.ndarray) -> Embedding:
        return Embedding(model=self._settings.embedding_model, vector=enc.to_bytes(vector))

    @staticmethod
    def _crop(image: np.ndarray, max_side: int) -> Crop:
        data, width, height = fr.encode_jpeg(image, max_side)
        return Crop(content_type="image/jpeg", data=data, width=width, height=height)

    @cached_property
    def _info(self) -> _ModelInfo:
        """Class list and embedding dimension from the model configs alone — no weights are loaded."""
        from transformers import AutoConfig

        detector = AutoConfig.from_pretrained(self._settings.detector_model)
        embedding = AutoConfig.from_pretrained(self._settings.embedding_model)
        classes = [detector.id2label[k] for k in sorted(detector.id2label, key=int)]
        return _ModelInfo(classes, int(embedding.hidden_size))


def _area(box: np.ndarray) -> float:
    x1, y1, x2, y2 = box
    return float(max(0.0, x2 - x1) * max(0.0, y2 - y1))


def _to_supervision(sv, detections: list[Detection]):
    if not detections:
        return sv.Detections.empty()
    return sv.Detections(
        xyxy=np.stack([d.box for d in detections]).astype(np.float32),
        confidence=np.array([d.confidence for d in detections], dtype=np.float32),
        class_id=np.array([d.class_id for d in detections], dtype=int),
    )
