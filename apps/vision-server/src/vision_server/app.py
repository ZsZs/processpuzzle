"""The HTTP surface of vision-server-api.yaml, under /v1."""

from __future__ import annotations

import secrets
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from uuid import UUID

from fastapi import APIRouter, Depends, FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from vision_server.config import Settings
from vision_server.jobs import JobManager, JobNotDone, JobNotFound, QueueFull
from vision_server.pipeline import Pipeline
from vision_server.pipeline.encoding import from_bytes
from vision_server.schemas import (
    EmbeddingJobRequest,
    EmbeddingJobResult,
    EnrollmentJobRequest,
    EnrollmentJobResult,
    Problem,
    RecognitionJobRequest,
    RecognitionJobResult,
    VisionHealth,
    VisionJob,
    VisionJobKind,
    VisionModels,
)

PROBLEM_JSON = "application/problem+json"


class ProblemError(Exception):
    def __init__(self, status_code: int, title: str, detail: str | None = None, headers: dict | None = None):
        super().__init__(detail or title)
        self.status_code, self.title, self.detail, self.headers = status_code, title, detail, headers


def _problem(request: Request, status_code: int, title: str, detail: str | None, headers=None) -> JSONResponse:
    body = Problem(title=title, status=status_code, detail=detail, instance=request.url.path)
    return JSONResponse(
        body.model_dump(mode="json", by_alias=True, exclude_none=True),
        status_code=status_code,
        media_type=PROBLEM_JSON,
        headers=headers,
    )


def create_app(settings: Settings, pipeline: Pipeline, manager: JobManager | None = None) -> FastAPI:
    jobs = manager or JobManager(settings, pipeline)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        jobs.start()
        yield
        jobs.stop()

    app = FastAPI(title="ProcessPuzzle Vision Server", version="0.1.0", lifespan=lifespan)
    bearer = HTTPBearer(auto_error=False)

    def authenticated(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> None:
        if credentials is None or not secrets.compare_digest(credentials.credentials, settings.service_token):
            raise ProblemError(status.HTTP_401_UNAUTHORIZED, "Unauthorized", "a valid service token is required")

    @app.exception_handler(ProblemError)
    async def on_problem(request: Request, error: ProblemError) -> JSONResponse:
        return _problem(request, error.status_code, error.title, error.detail, error.headers)

    @app.exception_handler(RequestValidationError)
    async def on_invalid(request: Request, error: RequestValidationError) -> JSONResponse:
        detail = "; ".join(f"{'.'.join(map(str, e['loc']))}: {e['msg']}" for e in error.errors())
        return _problem(request, status.HTTP_400_BAD_REQUEST, "Invalid request", detail)

    def submit(kind: VisionJobKind, request) -> VisionJob:
        try:
            return jobs.submit(kind, request)
        except QueueFull as full:
            raise ProblemError(
                status.HTTP_503_SERVICE_UNAVAILABLE,
                "Queue full",
                str(full),
                headers={"Retry-After": str(full.retry_after)},
            ) from full

    def result(job_id: UUID, kind: VisionJobKind):
        try:
            return jobs.result(job_id, kind)
        except JobNotFound as missing:
            raise ProblemError(status.HTTP_404_NOT_FOUND, "Job not found") from missing
        except JobNotDone as pending:
            raise ProblemError(status.HTTP_409_CONFLICT, "Job not done", "the job has not finished DONE") from pending

    api = APIRouter(prefix="/v1")
    secured = APIRouter(prefix="/v1", dependencies=[Depends(authenticated)])
    json_out = {"response_model_by_alias": True, "response_model_exclude_none": True}

    @api.get("/health", response_model=VisionHealth, **json_out)
    def health() -> VisionHealth:
        queued, running = jobs.counts()
        return VisionHealth(status="UP", queued=queued, running=running)

    @secured.get("/models", response_model=VisionModels, **json_out)
    def models() -> VisionModels:
        return pipeline.models()

    @secured.post("/enrollment-jobs", status_code=202, response_model=VisionJob, **json_out)
    def submit_enrollment(request: EnrollmentJobRequest) -> VisionJob:
        return submit(VisionJobKind.ENROLLMENT, request)

    @secured.get("/enrollment-jobs/{job_id}/result", response_model=EnrollmentJobResult, **json_out)
    def enrollment_result(job_id: UUID):
        return result(job_id, VisionJobKind.ENROLLMENT)

    @secured.post("/embedding-jobs", status_code=202, response_model=VisionJob, **json_out)
    def submit_embedding(request: EmbeddingJobRequest) -> VisionJob:
        return submit(VisionJobKind.EMBEDDING, request)

    @secured.get("/embedding-jobs/{job_id}/result", response_model=EmbeddingJobResult, **json_out)
    def embedding_result(job_id: UUID):
        return result(job_id, VisionJobKind.EMBEDDING)

    @secured.post("/recognition-jobs", status_code=202, response_model=VisionJob, **json_out)
    def submit_recognition(request: RecognitionJobRequest) -> VisionJob:
        _check_gallery_model(request, pipeline.models())
        return submit(VisionJobKind.RECOGNITION, request)

    @secured.get("/recognition-jobs/{job_id}/result", response_model=RecognitionJobResult, **json_out)
    def recognition_result(job_id: UUID):
        return result(job_id, VisionJobKind.RECOGNITION)

    @secured.get("/jobs/{job_id}", response_model=VisionJob, **json_out)
    def get_job(job_id: UUID) -> VisionJob:
        try:
            return jobs.get(job_id)
        except JobNotFound as missing:
            raise ProblemError(status.HTTP_404_NOT_FOUND, "Job not found") from missing

    @secured.delete("/jobs/{job_id}", status_code=204)
    def delete_job(job_id: UUID) -> Response:
        try:
            jobs.delete(job_id)
        except JobNotFound as missing:
            raise ProblemError(status.HTTP_404_NOT_FOUND, "Job not found") from missing
        return Response(status_code=204)

    app.include_router(api)
    app.include_router(secured)
    return app


def _check_gallery_model(request: RecognitionJobRequest, models: VisionModels) -> None:
    """Embeddings of another model are not comparable; refuse with 409 so base-ai-backend re-embeds the gallery."""
    expected = models.embedding
    for candidate in request.candidates:
        for embedding in candidate.gallery:
            if embedding.model != expected.model:
                raise ProblemError(
                    status.HTTP_409_CONFLICT,
                    "Stale gallery",
                    f"candidate {candidate.candidate_id} has embeddings of {embedding.model}, "
                    f"the server embeds with {expected.model}",
                )
            try:
                from_bytes(embedding.vector, expected.dimension)
            except ValueError as wrong:
                raise ProblemError(status.HTTP_400_BAD_REQUEST, "Invalid embedding", str(wrong)) from wrong
