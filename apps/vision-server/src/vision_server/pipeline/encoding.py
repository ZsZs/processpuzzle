"""Embedding vectors on the wire: float32, little-endian, base64 in JSON (the base64 step is schemas.Base64Bytes)."""

from __future__ import annotations

import numpy as np

_DTYPE = np.dtype("<f4")


def to_bytes(vector: np.ndarray) -> bytes:
    return np.asarray(vector, dtype=_DTYPE).tobytes()


def from_bytes(data: bytes, dimension: int | None = None) -> np.ndarray:
    if len(data) % _DTYPE.itemsize:
        raise ValueError(f"embedding of {len(data)} bytes is not a float32 vector")
    vector = np.frombuffer(data, dtype=_DTYPE).astype(np.float32)
    if dimension is not None and vector.shape[0] != dimension:
        raise ValueError(f"embedding has dimension {vector.shape[0]}, expected {dimension}")
    return vector


def normalize(vector: np.ndarray) -> np.ndarray:
    norm = float(np.linalg.norm(vector))
    return vector / norm if norm > 0 else vector


def gallery_matrix(vectors: list[np.ndarray]) -> np.ndarray | None:
    """Rows of L2-normalized vectors, or None for an empty gallery."""
    if not vectors:
        return None
    return np.stack([normalize(v) for v in vectors])
