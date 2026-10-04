"""The pipeline seam between the job queue and the models.

``jobs`` and ``app`` depend only on the ``Pipeline`` protocol, so they are tested with a fake; ``vision``
holds the real implementation and is the only module that imports torch.
"""

from __future__ import annotations

from pathlib import Path
from typing import Protocol

from vision_server.schemas import (
    EmbeddingJobRequest,
    EmbeddingJobResult,
    EnrollmentJobRequest,
    EnrollmentJobResult,
    RecognitionJobRequest,
    RecognitionJobResult,
    VisionModels,
)


class JobCancelled(Exception):
    """Raised inside a pipeline call when the job was cancelled; the worker records CANCELLED."""


class MediaUnavailable(Exception):
    """A media URL could not be fetched, or was too large."""


class JobContext(Protocol):
    def progress(self, fraction: float) -> None:
        """Report progress, 0..1. Also raises JobCancelled if the job has been cancelled."""

    def fetch(self, url: str, max_bytes: int) -> Path:
        """Download a media URL to a temporary file, deleted when the job ends."""


class Pipeline(Protocol):
    def models(self) -> VisionModels: ...

    def enroll(self, request: EnrollmentJobRequest, ctx: JobContext) -> EnrollmentJobResult: ...

    def embed(self, request: EmbeddingJobRequest, ctx: JobContext) -> EmbeddingJobResult: ...

    def recognize(self, request: RecognitionJobRequest, ctx: JobContext) -> RecognitionJobResult: ...

    def release(self) -> None:
        """Drop loaded models; the next call reloads them."""
