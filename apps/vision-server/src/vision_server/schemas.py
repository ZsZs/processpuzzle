"""Wire types of vision-server-api.yaml, which is the contract; tests/test_contract.py keeps the two aligned.

Field names are snake_case in Python and camelCase on the wire, through the alias generator. Bytes fields
(``Embedding.vector``, ``Crop.data``) are base64 on the wire, as the contract's ``format: byte`` says.
"""

from __future__ import annotations

import base64
from datetime import datetime
from enum import StrEnum
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, PlainSerializer, PlainValidator, model_validator
from pydantic.alias_generators import to_camel


def _decode_base64(value: object) -> bytes:
    if isinstance(value, bytes):
        return value
    if isinstance(value, str):
        return base64.b64decode(value, validate=True)
    raise ValueError("expected a base64 string")


Base64Bytes = Annotated[
    bytes,
    PlainValidator(_decode_base64),
    PlainSerializer(lambda b: base64.b64encode(b).decode("ascii"), return_type=str),
]


class WireModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, use_enum_values=False)


# ── Common ─────────────────────────────────────────────────────────


class Requester(WireModel):
    stack: str
    org_key: str


class JobSubmission(WireModel):
    job_id: UUID
    requester: Requester
    callback_url: str
    callback_token: str


class DetectionSettings(WireModel):
    detector_class: str
    min_confidence: float = Field(default=0.4, ge=0, le=1)


class OcrSettings(WireModel):
    identifier_pattern: str | None = None


class Embedding(WireModel):
    model: str
    vector: Base64Bytes


class Crop(WireModel):
    content_type: str
    data: Base64Bytes
    width: int
    height: int


class IdentifierReading(WireModel):
    text: str
    confidence: float


class VisionJobKind(StrEnum):
    ENROLLMENT = "ENROLLMENT"
    EMBEDDING = "EMBEDDING"
    RECOGNITION = "RECOGNITION"


class VisionJobStatus(StrEnum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    DONE = "DONE"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class VisionJob(WireModel):
    job_id: UUID
    kind: VisionJobKind
    status: VisionJobStatus
    progress: float
    queue_position: int | None = None
    failure_reason: str | None = None
    submitted_at: datetime
    finished_at: datetime | None = None
    result_expires_at: datetime | None = None


class VisionJobNotification(WireModel):
    job_id: UUID
    kind: VisionJobKind
    status: VisionJobStatus


class MediaRef(WireModel):
    media_id: str
    url: str


# ── Enrollment ─────────────────────────────────────────────────────


class EnrollmentJobRequest(JobSubmission):
    detection: DetectionSettings
    ocr: OcrSettings | None = None
    photos: list[MediaRef] = Field(min_length=1, max_length=50)


class EnrollmentPhotoStatus(StrEnum):
    ENROLLED = "ENROLLED"
    NO_SUBJECT = "NO_SUBJECT"
    AMBIGUOUS = "AMBIGUOUS"
    FAILED = "FAILED"


class EnrollmentPhotoResult(WireModel):
    media_id: str
    status: EnrollmentPhotoStatus
    crop: Crop | None = None
    embedding: Embedding | None = None
    identifier: IdentifierReading | None = None
    failure_reason: str | None = None


class EnrollmentJobResult(WireModel):
    job_id: UUID
    photos: list[EnrollmentPhotoResult]


# ── Embedding ──────────────────────────────────────────────────────


class EmbeddingJobRequest(JobSubmission):
    crops: list[MediaRef] = Field(min_length=1, max_length=1000)


class EmbeddingResult(WireModel):
    media_id: str
    embedding: Embedding | None = None
    failure_reason: str | None = None


class EmbeddingJobResult(WireModel):
    job_id: UUID
    embeddings: list[EmbeddingResult]


# ── Recognition ────────────────────────────────────────────────────


class MatchingSettings(WireModel):
    identifier_weight: float = Field(default=0.6, ge=0, le=1)
    accept_score: float = Field(default=0.75, ge=0, le=1)
    accept_margin: float = Field(default=0.1, ge=0, le=1)
    sample_fps: float = Field(default=3, ge=0.5, le=30)


class Candidate(WireModel):
    candidate_id: str
    identifier_text: str | None = None
    gallery: list[Embedding] = Field(default_factory=list)


class RecognitionJobRequest(JobSubmission):
    detection: DetectionSettings
    ocr: OcrSettings | None = None
    matching: MatchingSettings = Field(default_factory=MatchingSettings)
    video: MediaRef | None = None
    frames: list[MediaRef] | None = Field(default=None, min_length=1, max_length=5)
    candidates: list[Candidate] = Field(min_length=1)

    @model_validator(mode="after")
    def _one_source(self) -> RecognitionJobRequest:
        if (self.video is None) == (self.frames is None):
            raise ValueError("exactly one of video and frames is required")
        return self


class CandidateScore(WireModel):
    candidate_id: str
    score: float
    identifier_score: float | None = None
    embedding_score: float | None = None


class TrackStatus(StrEnum):
    AUTO_MATCHED = "AUTO_MATCHED"
    NEEDS_REVIEW = "NEEDS_REVIEW"


class TrackResult(WireModel):
    track_id: int
    status: TrackStatus
    candidate_id: str | None = None
    score: float | None = None
    identifier: IdentifierReading | None = None
    best_crop: Crop | None = None
    embedding: Embedding | None = None
    first_seen_ms: int
    last_seen_ms: int
    candidates: list[CandidateScore]


class RecognitionJobResult(WireModel):
    job_id: UUID
    frames_processed: int | None = None
    processing_seconds: float | None = None
    tracks: list[TrackResult]


# ── Server ─────────────────────────────────────────────────────────


class VisionHealth(WireModel):
    status: str
    queued: int = 0
    running: int = 0


class VisionModelsDetector(WireModel):
    model: str
    classes: list[str]


class VisionModelsEmbedding(WireModel):
    model: str
    dimension: int


class VisionModelsOcr(WireModel):
    model: str | None = None


class VisionModels(WireModel):
    detector: VisionModelsDetector
    embedding: VisionModelsEmbedding
    ocr: VisionModelsOcr | None = None
    device: str


class Problem(WireModel):
    type: str = "about:blank"
    title: str | None = None
    status: int | None = None
    detail: str | None = None
    instance: str | None = None
