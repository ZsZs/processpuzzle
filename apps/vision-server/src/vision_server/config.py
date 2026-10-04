"""Settings, read once from the environment. Every variable is prefixed VISION_."""

from __future__ import annotations

import os
from dataclasses import dataclass


def _env(name: str, default: str | None = None) -> str | None:
    return os.environ.get(f"VISION_{name}", default)


def _int(name: str, default: int) -> int:
    return int(_env(name, str(default)))


@dataclass(frozen=True)
class Settings:
    service_token: str
    detector_model: str = "PekingU/rtdetr_r18vd_coco_o365"
    embedding_model: str = "facebook/dinov2-small"
    ocr_languages: tuple[str, ...] = ("en",)
    device: str = "cpu"
    torch_threads: int = 0
    max_queued_jobs: int = 20
    result_retention_seconds: int = 24 * 3600
    # Models are dropped after this long without a job, giving their RAM back to the host; the next job
    # reloads them. 0 keeps them loaded.
    idle_unload_seconds: int = 600
    max_photo_bytes: int = 40 * 1024 * 1024
    max_video_bytes: int = 4 * 1024 * 1024 * 1024
    download_timeout_seconds: int = 600
    callback_attempts: int = 3

    @staticmethod
    def from_env() -> Settings:
        token = _env("SERVICE_TOKEN")
        if not token:
            raise RuntimeError("VISION_SERVICE_TOKEN must be set; the server refuses to run unauthenticated")
        return Settings(
            service_token=token,
            detector_model=_env("DETECTOR_MODEL", Settings.detector_model),
            embedding_model=_env("EMBEDDING_MODEL", Settings.embedding_model),
            ocr_languages=tuple(_env("OCR_LANGUAGES", "en").split(",")),
            device=_env("DEVICE", Settings.device),
            torch_threads=_int("TORCH_THREADS", Settings.torch_threads),
            max_queued_jobs=_int("MAX_QUEUED_JOBS", Settings.max_queued_jobs),
            result_retention_seconds=_int("RESULT_RETENTION_SECONDS", Settings.result_retention_seconds),
            idle_unload_seconds=_int("IDLE_UNLOAD_SECONDS", Settings.idle_unload_seconds),
            max_photo_bytes=_int("MAX_PHOTO_BYTES", Settings.max_photo_bytes),
            max_video_bytes=_int("MAX_VIDEO_BYTES", Settings.max_video_bytes),
            download_timeout_seconds=_int("DOWNLOAD_TIMEOUT_SECONDS", Settings.download_timeout_seconds),
            callback_attempts=_int("CALLBACK_ATTEMPTS", Settings.callback_attempts),
        )
