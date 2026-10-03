"""Frames and crops: video sampling, crop quality, and the per-track best-crop selection.

Uses OpenCV and numpy only. Images are RGB uint8 arrays throughout; OpenCV's BGR stops at the reader.
"""

from __future__ import annotations

import heapq
from collections.abc import Iterator
from dataclasses import dataclass, field

import cv2
import numpy as np

CROP_PADDING = 0.05
SHARPNESS_REFERENCE = 150.0
EDGE_PENALTY = 0.5
QUALITY_WIDTH = 256


@dataclass(frozen=True)
class Frame:
    index: int
    millis: int
    image: np.ndarray


@dataclass(frozen=True)
class VideoInfo:
    fps: float
    frame_count: int
    width: int
    height: int


def video_info(path: str) -> VideoInfo:
    capture = cv2.VideoCapture(path)
    if not capture.isOpened():
        raise ValueError("the video could not be decoded")
    try:
        fps = capture.get(cv2.CAP_PROP_FPS) or 25.0
        return VideoInfo(
            fps=fps,
            frame_count=int(capture.get(cv2.CAP_PROP_FRAME_COUNT)),
            width=int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)),
            height=int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)),
        )
    finally:
        capture.release()


def sample_frames(path: str, sample_fps: float) -> Iterator[Frame]:
    """Every n-th frame, n chosen so that about sample_fps frames per second of video are returned.

    Skipped frames are grabbed but not retrieved, which saves the colour conversion but not the decode.
    """
    capture = cv2.VideoCapture(path)
    if not capture.isOpened():
        raise ValueError("the video could not be decoded")
    try:
        native_fps = capture.get(cv2.CAP_PROP_FPS) or 25.0
        step = max(1, round(native_fps / sample_fps))
        index = 0
        while capture.grab():
            if index % step == 0:
                ok, bgr = capture.retrieve()
                if ok:
                    yield Frame(index, int(index * 1000 / native_fps), cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
            index += 1
    finally:
        capture.release()


def crop(image: np.ndarray, box: np.ndarray, padding: float = CROP_PADDING) -> np.ndarray:
    """The box (xyxy, pixels) grown by padding on each side, clamped to the image."""
    height, width = image.shape[:2]
    x1, y1, x2, y2 = box
    pad_x, pad_y = (x2 - x1) * padding, (y2 - y1) * padding
    left, top = max(0, int(x1 - pad_x)), max(0, int(y1 - pad_y))
    right, bottom = min(width, int(x2 + pad_x)), min(height, int(y2 + pad_y))
    return image[top:bottom, left:right]


def sharpness(image: np.ndarray) -> float:
    """Variance of the Laplacian, measured at a fixed width so crops of different sizes compare fairly."""
    if image.size == 0:
        return 0.0
    height, width = image.shape[:2]
    scale = QUALITY_WIDTH / max(1, width)
    small = cv2.resize(image, (QUALITY_WIDTH, max(1, int(height * scale))), interpolation=cv2.INTER_AREA)
    grey = cv2.cvtColor(small, cv2.COLOR_RGB2GRAY)
    return float(cv2.Laplacian(grey, cv2.CV_64F).var())


def touches_edge(box: np.ndarray, width: int, height: int, margin: int = 2) -> bool:
    x1, y1, x2, y2 = box
    return x1 <= margin or y1 <= margin or x2 >= width - margin or y2 >= height - margin


def quality(image: np.ndarray, box: np.ndarray) -> float:
    """How useful a detection is for OCR and embedding: big, sharp and wholly in frame.

    Area dominates — a sail number is readable only at some size — scaled by sharpness up to a reference
    value, and halved for a box cut off by the frame edge, which is usually part of a boat.
    """
    height, width = image.shape[:2]
    x1, y1, x2, y2 = box
    area = max(0.0, (x2 - x1) * (y2 - y1))
    factor = min(1.0, sharpness(crop(image, box, 0)) / SHARPNESS_REFERENCE)
    if touches_edge(box, width, height):
        factor *= EDGE_PENALTY
    return area * factor


def encode_jpeg(image: np.ndarray, max_side: int, quality_percent: int = 90) -> tuple[bytes, int, int]:
    """JPEG bytes of the image, downscaled so that its longer side is at most max_side."""
    height, width = image.shape[:2]
    scale = min(1.0, max_side / max(height, width, 1))
    if scale < 1.0:
        image = cv2.resize(image, (int(width * scale), int(height * scale)), interpolation=cv2.INTER_AREA)
    ok, data = cv2.imencode(".jpg", cv2.cvtColor(image, cv2.COLOR_RGB2BGR), [cv2.IMWRITE_JPEG_QUALITY, quality_percent])
    if not ok:
        raise ValueError("the crop could not be encoded")
    return data.tobytes(), image.shape[1], image.shape[0]


def decode_image(data: bytes) -> np.ndarray:
    image = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("the image could not be decoded (JPEG, PNG and WebP are supported)")
    return cv2.cvtColor(image, cv2.COLOR_BGR2RGB)


@dataclass(order=True)
class _Kept:
    quality: float
    order: int
    jpeg: bytes = field(compare=False)


@dataclass
class TrackCrops:
    """The best crops of one track, kept as JPEG so a long video does not hold every crop in RAM."""

    track_id: int
    keep: int
    first_ms: int = -1
    last_ms: int = -1
    sightings: int = 0
    _heap: list[_Kept] = field(default_factory=list)

    def offer(self, image: np.ndarray, box: np.ndarray, millis: int, max_side: int) -> None:
        self.sightings += 1
        self.first_ms = millis if self.first_ms < 0 else self.first_ms
        self.last_ms = millis
        score = quality(image, box)
        if len(self._heap) >= self.keep and score <= self._heap[0].quality:
            return
        jpeg, _, _ = encode_jpeg(crop(image, box), max_side, 95)
        entry = _Kept(score, self.sightings, jpeg)
        if len(self._heap) < self.keep:
            heapq.heappush(self._heap, entry)
        else:
            heapq.heapreplace(self._heap, entry)

    def best(self) -> list[np.ndarray]:
        """The kept crops, best first, decoded back to RGB."""
        return [decode_image(kept.jpeg) for kept in sorted(self._heap, reverse=True)]
