"""The transient job queue: one worker, fair between stacks, results kept until fetched or expired.

Nothing here survives a restart, by design — the server holds no tenant data, and base-ai-backend resubmits a job
its polling finds missing. See vision-server-api.yaml, "Job flow".
"""

from __future__ import annotations

import logging
import shutil
import tempfile
import threading
import time
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import UUID

import httpx
from pydantic import BaseModel

from vision_server.config import Settings
from vision_server.pipeline import JobCancelled, MediaUnavailable, Pipeline
from vision_server.schemas import (
    EmbeddingJobRequest,
    EnrollmentJobRequest,
    JobSubmission,
    RecognitionJobRequest,
    VisionJob,
    VisionJobKind,
    VisionJobNotification,
    VisionJobStatus,
)

log = logging.getLogger(__name__)

FINISHED = {VisionJobStatus.DONE, VisionJobStatus.FAILED, VisionJobStatus.CANCELLED}


class QueueFull(Exception):
    def __init__(self, retry_after: int):
        super().__init__("the job queue is at capacity")
        self.retry_after = retry_after


class JobNotFound(Exception):
    pass


class JobNotDone(Exception):
    pass


@dataclass
class Job:
    kind: VisionJobKind
    request: JobSubmission
    submitted_at: datetime
    status: VisionJobStatus = VisionJobStatus.QUEUED
    progress: float = 0.0
    failure_reason: str | None = None
    finished_at: datetime | None = None
    result: BaseModel | None = None
    cancel_requested: bool = False

    @property
    def job_id(self) -> UUID:
        return self.request.job_id

    @property
    def stack(self) -> str:
        return self.request.requester.stack


Notifier = Callable[[JobSubmission, VisionJobNotification], None]


def http_notifier(attempts: int) -> Notifier:
    """POSTs the notification with the job's callback token; retries with backoff, then gives up quietly.

    Giving up is safe: base-ai-backend polls jobs whose notification is overdue.
    """

    def notify(request: JobSubmission, notification: VisionJobNotification) -> None:
        body = notification.model_dump(mode="json", by_alias=True)
        headers = {"X-Callback-Token": request.callback_token}
        for attempt in range(1, attempts + 1):
            try:
                response = httpx.post(request.callback_url, json=body, headers=headers, timeout=10)
                if response.status_code < 500:
                    if response.is_error:
                        log.warning("callback for job %s refused: %s", request.job_id, response.status_code)
                    return
            except httpx.HTTPError as error:
                log.warning("callback for job %s failed (attempt %d): %s", request.job_id, attempt, error)
            time.sleep(2**attempt)
        log.error("callback for job %s abandoned after %d attempts", request.job_id, attempts)

    return notify


class _Context:
    """The JobContext a pipeline call receives: progress reporting, cancellation and media download."""

    def __init__(self, manager: JobManager, job: Job, workdir: Path, timeout: int):
        self._manager = manager
        self._job = job
        self._workdir = workdir
        self._timeout = timeout
        self._downloads = 0

    def progress(self, fraction: float) -> None:
        with self._manager._lock:
            self._job.progress = max(self._job.progress, min(1.0, fraction))
            if self._job.cancel_requested:
                raise JobCancelled()

    def fetch(self, url: str, max_bytes: int) -> Path:
        self._downloads += 1
        target = self._workdir / f"media-{self._downloads}"
        received = 0
        try:
            with httpx.stream("GET", url, timeout=self._timeout, follow_redirects=False) as response:
                response.raise_for_status()
                with target.open("wb") as out:
                    for chunk in response.iter_bytes(1024 * 1024):
                        received += len(chunk)
                        if received > max_bytes:
                            raise MediaUnavailable(f"media exceeds {max_bytes} bytes")
                        out.write(chunk)
        except httpx.HTTPError as error:
            raise MediaUnavailable(f"media could not be fetched: {error}") from error
        return target


class JobManager:
    def __init__(self, settings: Settings, pipeline: Pipeline, notifier: Notifier | None = None):
        self._settings = settings
        self._pipeline = pipeline
        self._notify = notifier or http_notifier(settings.callback_attempts)
        self._lock = threading.Lock()
        self._wakeup = threading.Condition(self._lock)
        self._jobs: dict[UUID, Job] = {}
        self._queues: dict[str, deque[Job]] = {}
        self._rotation: deque[str] = deque()
        self._running: Job | None = None
        self._stopping = False
        self._last_job_at = time.monotonic()
        self._models_loaded = False
        self._worker = threading.Thread(target=self._work, name="vision-worker", daemon=True)

    # ── Lifecycle ──────────────────────────────────────────────────

    def start(self) -> None:
        self._worker.start()

    def stop(self) -> None:
        with self._wakeup:
            self._stopping = True
            self._wakeup.notify_all()
        self._worker.join(timeout=5)

    # ── API operations ────────────────────────────────────────────

    def submit(self, kind: VisionJobKind, request: JobSubmission) -> VisionJob:
        with self._wakeup:
            self._purge_expired()
            existing = self._jobs.get(request.job_id)
            if existing is not None:
                return self._view(existing)
            if self._queued_count() >= self._settings.max_queued_jobs:
                raise QueueFull(retry_after=60)
            job = Job(kind=kind, request=request, submitted_at=_now())
            self._jobs[job.job_id] = job
            queue = self._queues.setdefault(job.stack, deque())
            if not queue and job.stack not in self._rotation:
                self._rotation.append(job.stack)
            queue.append(job)
            log.info("job %s (%s) queued for %s/%s", job.job_id, kind, job.stack, request.requester.org_key)
            self._wakeup.notify_all()
            return self._view(job)

    def get(self, job_id: UUID) -> VisionJob:
        with self._lock:
            self._purge_expired()
            return self._view(self._find(job_id))

    def result(self, job_id: UUID, kind: VisionJobKind) -> BaseModel:
        with self._lock:
            self._purge_expired()
            job = self._find(job_id)
            if job.kind != kind:
                raise JobNotFound()
            if job.status != VisionJobStatus.DONE or job.result is None:
                raise JobNotDone()
            return job.result

    def delete(self, job_id: UUID) -> None:
        """Cancel a queued or running job, or discard a finished one."""
        with self._lock:
            job = self._find(job_id)
            if job.status in FINISHED:
                del self._jobs[job_id]
            elif job.status == VisionJobStatus.QUEUED:
                self._queues[job.stack].remove(job)
                self._finish(job, VisionJobStatus.CANCELLED)
            else:
                job.cancel_requested = True

    def counts(self) -> tuple[int, int]:
        with self._lock:
            return self._queued_count(), 1 if self._running else 0

    # ── Worker ─────────────────────────────────────────────────────

    def _work(self) -> None:
        while True:
            with self._wakeup:
                job = self._next_job()
                while job is None and not self._stopping:
                    self._wakeup.wait(timeout=30)
                    self._release_if_idle()
                    job = self._next_job()
                if self._stopping:
                    return
                job.status = VisionJobStatus.RUNNING
                self._running = job
            self._run(job)

    def _run(self, job: Job) -> None:
        workdir = Path(tempfile.mkdtemp(prefix=f"vision-{job.job_id}-"))
        ctx = _Context(self, job, workdir, self._settings.download_timeout_seconds)
        status, result, reason = VisionJobStatus.DONE, None, None
        started = time.monotonic()
        try:
            self._models_loaded = True
            result = self._dispatch(job, ctx)
        except JobCancelled:
            status = VisionJobStatus.CANCELLED
        except MediaUnavailable as error:
            status, reason = VisionJobStatus.FAILED, str(error)
        except Exception as error:  # noqa: BLE001 — any pipeline failure fails the job, never the worker
            log.exception("job %s failed", job.job_id)
            status, reason = VisionJobStatus.FAILED, f"{type(error).__name__}: {error}"
        finally:
            shutil.rmtree(workdir, ignore_errors=True)
        log.info("job %s finished %s in %.1fs", job.job_id, status, time.monotonic() - started)
        with self._lock:
            job.result = result
            job.failure_reason = reason
            if status == VisionJobStatus.DONE:
                job.progress = 1.0
            self._finish(job, status)
            self._running = None
            self._last_job_at = time.monotonic()

    def _dispatch(self, job: Job, ctx: _Context) -> BaseModel:
        request = job.request
        if isinstance(request, EnrollmentJobRequest):
            return self._pipeline.enroll(request, ctx)
        if isinstance(request, EmbeddingJobRequest):
            return self._pipeline.embed(request, ctx)
        if isinstance(request, RecognitionJobRequest):
            return self._pipeline.recognize(request, ctx)
        raise TypeError(f"unknown job request {type(request).__name__}")

    def _finish(self, job: Job, status: VisionJobStatus) -> None:
        """Record the outcome and notify. Called with the lock held; the notification is sent off-thread."""
        job.status = status
        job.finished_at = _now()
        notification = VisionJobNotification(job_id=job.job_id, kind=job.kind, status=status)
        threading.Thread(target=self._notify, args=(job.request, notification), daemon=True).start()

    def _next_job(self) -> Job | None:
        """Round-robin over stacks, FIFO within one — a stack with a long video queue cannot starve another."""
        for _ in range(len(self._rotation)):
            stack = self._rotation[0]
            self._rotation.rotate(-1)
            queue = self._queues.get(stack)
            if queue:
                job = queue.popleft()
                if not queue:
                    self._rotation.remove(stack)
                return job
            self._rotation.remove(stack)
        return None

    def _release_if_idle(self) -> None:
        idle = self._settings.idle_unload_seconds
        if idle and self._models_loaded and time.monotonic() - self._last_job_at > idle:
            log.info("idle for %ds, releasing models", idle)
            self._pipeline.release()
            self._models_loaded = False

    # ── Helpers (lock held) ────────────────────────────────────────

    def _find(self, job_id: UUID) -> Job:
        job = self._jobs.get(job_id)
        if job is None:
            raise JobNotFound()
        return job

    def _queued_count(self) -> int:
        return sum(len(queue) for queue in self._queues.values())

    def _queue_position(self, job: Job) -> int | None:
        if job.status != VisionJobStatus.QUEUED:
            return None
        ahead = 1 if self._running else 0
        # Approximate under round-robin: jobs ahead in its own stack's queue, plus one per other waiting stack.
        own = self._queues.get(job.stack, deque())
        ahead += own.index(job) if job in own else 0
        ahead += sum(1 for stack, queue in self._queues.items() if stack != job.stack and queue)
        return ahead

    def _purge_expired(self) -> None:
        horizon = _now() - timedelta(seconds=self._settings.result_retention_seconds)
        expired = [job_id for job_id, job in self._jobs.items() if job.finished_at and job.finished_at < horizon]
        for job_id in expired:
            del self._jobs[job_id]

    def _view(self, job: Job) -> VisionJob:
        expires = (
            job.finished_at + timedelta(seconds=self._settings.result_retention_seconds) if job.finished_at else None
        )
        return VisionJob(
            job_id=job.job_id,
            kind=job.kind,
            status=job.status,
            progress=job.progress,
            queue_position=self._queue_position(job),
            failure_reason=job.failure_reason,
            submitted_at=job.submitted_at,
            finished_at=job.finished_at,
            result_expires_at=expires,
        )


def _now() -> datetime:
    return datetime.now(UTC)
