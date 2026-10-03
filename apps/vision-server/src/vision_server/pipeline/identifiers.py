"""Identifier text: normalization, pattern filtering, voting across frames and fuzzy comparison.

Pure functions, no model dependency. The identifier is whatever OCR-readable label a profile names —
a sail number for a boat.
"""

from __future__ import annotations

import re
from collections import defaultdict
from dataclasses import dataclass

from rapidfuzz import fuzz

_WHITESPACE = re.compile(r"\s+")
# Characters OCR reads off a sail that are never part of an identifier: punctuation, dashes, slashes.
_NOISE = re.compile(r"[^A-Z0-9 ]")


@dataclass(frozen=True)
class Reading:
    text: str
    confidence: float


def normalize(text: str) -> str:
    """Upper-case, noise characters dropped, whitespace collapsed to one space — the contract's normalization."""
    cleaned = _NOISE.sub(" ", text.upper())
    return _WHITESPACE.sub(" ", cleaned).strip()


def compact(text: str) -> str:
    """The comparison form: normalized and without spaces, so that "GER 1234" and "GER1234" are equal."""
    return normalize(text).replace(" ", "")


def accept(readings: list[Reading], pattern: str | None) -> list[Reading]:
    """Normalized readings that match the pattern; every non-empty one when there is no pattern."""
    regex = re.compile(pattern) if pattern else None
    accepted = []
    for reading in readings:
        text = normalize(reading.text)
        if text and (regex is None or regex.fullmatch(text)):
            accepted.append(Reading(text, reading.confidence))
    return accepted


def vote(readings: list[Reading], frames: int) -> Reading | None:
    """The reading most supported across a track's frames.

    Readings that compare equal (spaces ignored) pool their confidence. The winner's confidence is its pooled
    confidence over the number of frames looked at, so a number read clearly on one frame of ten counts for
    less than one read on every frame.
    """
    if not readings or frames <= 0:
        return None
    pooled: dict[str, float] = defaultdict(float)
    spelling: dict[str, str] = {}
    for reading in readings:
        key = compact(reading.text)
        pooled[key] += reading.confidence
        spelling.setdefault(key, reading.text)
    best = max(pooled, key=lambda key: pooled[key])
    return Reading(spelling[best], min(1.0, pooled[best] / frames))


def similarity(observed: str, registered: str) -> float:
    """Fuzzy similarity of two identifiers, 0..1, spaces ignored."""
    a, b = compact(observed), compact(registered)
    if not a or not b:
        return 0.0
    return fuzz.ratio(a, b) / 100.0
