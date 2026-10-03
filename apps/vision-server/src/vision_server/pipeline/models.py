"""The three models — detector, embedder, OCR — behind small wrappers. The only module importing torch.

Each is loaded on first use and dropped by ``Models.release``. Weights come from the Hugging Face hub and
EasyOCR's model store on first load and are cached under HF_HOME / VISION_MODEL_DIR (a volume in Docker).
"""

from __future__ import annotations

import gc
import logging
import os
import threading
from dataclasses import dataclass

import numpy as np

from vision_server.config import Settings
from vision_server.pipeline.identifiers import Reading

log = logging.getLogger(__name__)

EMBED_BATCH = 16
OCR_ALLOWLIST = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 "
OCR_MIN_HEIGHT = 320
# EasyOCR's cost grows with the pixel count; a sail number stays legible well below a 4K crop.
OCR_MAX_SIDE = 1280
# A first-pass reading at least this confident makes the mirrored pass unnecessary.
OCR_CONFIDENT = 0.5
MIRRORED_PENALTY = 0.9


@dataclass(frozen=True)
class Detection:
    box: np.ndarray  # xyxy, pixels
    confidence: float
    class_id: int


class Detector:
    def __init__(self, settings: Settings):
        import torch
        from transformers import AutoImageProcessor, AutoModelForObjectDetection

        self._torch = torch
        self._processor = AutoImageProcessor.from_pretrained(settings.detector_model)
        self._model = AutoModelForObjectDetection.from_pretrained(settings.detector_model).eval().to(settings.device)
        self._device = settings.device
        self.labels: dict[int, str] = {int(k): v for k, v in self._model.config.id2label.items()}

    def class_id(self, name: str) -> int:
        for class_id, label in self.labels.items():
            if label == name:
                return class_id
        raise ValueError(f"the detector has no class '{name}'")

    def detect(self, image: np.ndarray, class_id: int, min_confidence: float) -> list[Detection]:
        torch = self._torch
        inputs = self._processor(images=image, return_tensors="pt").to(self._device)
        with torch.inference_mode():
            outputs = self._model(**inputs)
        height, width = image.shape[:2]
        result = self._processor.post_process_object_detection(
            outputs, target_sizes=torch.tensor([(height, width)]), threshold=min_confidence
        )[0]
        detections = []
        for score, label, box in zip(result["scores"], result["labels"], result["boxes"], strict=True):
            if int(label) == class_id:
                detections.append(Detection(box.cpu().numpy(), float(score), int(label)))
        return detections


class Embedder:
    def __init__(self, settings: Settings):
        import torch
        from transformers import AutoImageProcessor, AutoModel

        self._torch = torch
        self._processor = AutoImageProcessor.from_pretrained(settings.embedding_model)
        self._model = AutoModel.from_pretrained(settings.embedding_model).eval().to(settings.device)
        self._device = settings.device
        self.dimension: int = int(self._model.config.hidden_size)

    def embed(self, images: list[np.ndarray]) -> np.ndarray:
        """One L2-normalized row per image: the CLS token after the final layer norm."""
        torch = self._torch
        rows = []
        for start in range(0, len(images), EMBED_BATCH):
            inputs = self._processor(images=images[start : start + EMBED_BATCH], return_tensors="pt").to(self._device)
            with torch.inference_mode():
                pooled = self._model(**inputs).pooler_output
            rows.append(torch.nn.functional.normalize(pooled, dim=-1).cpu().numpy())
        return np.concatenate(rows).astype(np.float32)


class Ocr:
    def __init__(self, settings: Settings):
        import easyocr

        model_dir = os.environ.get("VISION_MODEL_DIR")
        self._reader = easyocr.Reader(
            list(settings.ocr_languages),
            gpu=settings.device != "cpu",
            model_storage_directory=model_dir,
            # EasyOCR otherwise creates ~/.EasyOCR, and the service user has no home directory.
            user_network_directory=os.path.join(model_dir, "user_network") if model_dir else None,
            download_enabled=model_dir is None,
            verbose=False,
        )

    def read(self, image: np.ndarray) -> list[Reading]:
        """Text on the image, and on its mirror image when the image itself yields nothing confident.

        A sail is translucent: the number on the far side shows through mirrored, and when a boat heels the
        near side may be the only one in view. The mirrored pass doubles the cost, so it runs only when the
        direct one found nothing worth having; mirrored readings count slightly less.
        """
        image = _ocr_scale(image)
        readings = [Reading(text, float(conf)) for _, text, conf in self._read(image)]
        if not any(r.confidence >= OCR_CONFIDENT and r.text.strip() for r in readings):
            readings += [Reading(text, float(conf) * MIRRORED_PENALTY) for _, text, conf in self._read(image[:, ::-1])]
        return readings

    def _read(self, image: np.ndarray):
        return self._reader.readtext(np.ascontiguousarray(image), allowlist=OCR_ALLOWLIST, paragraph=False)


def _ocr_scale(image: np.ndarray) -> np.ndarray:
    """Upscale a crop too small to read, downscale one larger than OCR needs."""
    import cv2

    height, width = image.shape[:2]
    if 0 < height < OCR_MIN_HEIGHT:
        scale = OCR_MIN_HEIGHT / height
        return cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    longest = max(height, width)
    if longest > OCR_MAX_SIDE:
        scale = OCR_MAX_SIDE / longest
        return cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
    return image


class Models:
    """Lazy holder of the three models; thread-safe load, explicit release."""

    def __init__(self, settings: Settings):
        self._settings = settings
        self._lock = threading.Lock()
        self._detector: Detector | None = None
        self._embedder: Embedder | None = None
        self._ocr: Ocr | None = None
        if settings.torch_threads:
            import torch

            torch.set_num_threads(settings.torch_threads)

    def detector(self) -> Detector:
        with self._lock:
            if self._detector is None:
                log.info("loading detector %s", self._settings.detector_model)
                self._detector = Detector(self._settings)
            return self._detector

    def embedder(self) -> Embedder:
        with self._lock:
            if self._embedder is None:
                log.info("loading embedding model %s", self._settings.embedding_model)
                self._embedder = Embedder(self._settings)
            return self._embedder

    def ocr(self) -> Ocr:
        with self._lock:
            if self._ocr is None:
                log.info("loading OCR (%s)", ",".join(self._settings.ocr_languages))
                self._ocr = Ocr(self._settings)
            return self._ocr

    def release(self) -> None:
        with self._lock:
            self._detector = self._embedder = self._ocr = None
        gc.collect()
