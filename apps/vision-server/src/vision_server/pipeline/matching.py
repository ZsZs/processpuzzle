"""Fusion of identifier and embedding scores, and the one-to-one assignment of tracks to candidates.

Pure numpy / scipy; the inputs are what the models produced, the output is the contract's track verdicts.
See docs/ai/boat-recognition-design.md §3.3.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.optimize import linear_sum_assignment

from vision_server.pipeline.identifiers import similarity

TOP_CANDIDATES = 3
EMBEDDING_TOP_K = 3


@dataclass(frozen=True)
class TrackEvidence:
    """What a track contributes to matching: its voted identifier and its mean embedding (L2-normalized)."""

    track_id: int
    identifier: str | None
    embedding: np.ndarray | None


@dataclass(frozen=True)
class CandidateEvidence:
    """A candidate subject: its registered identifier and its gallery, one L2-normalized row per photo."""

    candidate_id: str
    identifier: str | None
    gallery: np.ndarray | None


@dataclass(frozen=True)
class PairScore:
    candidate_id: str
    score: float
    identifier_score: float | None
    embedding_score: float | None


@dataclass(frozen=True)
class Verdict:
    track_id: int
    auto_matched: bool
    candidate_id: str | None
    score: float | None
    candidates: list[PairScore]


def embedding_score(track: np.ndarray, gallery: np.ndarray) -> float:
    """Mean of the top-k cosine similarities against the gallery, clipped to 0..1.

    Top-k rather than max, so that one lucky gallery photo does not carry the match; rather than the mean of
    all, so that photos of the other side of the boat do not dilute it.
    """
    similarities = gallery @ track
    k = min(EMBEDDING_TOP_K, similarities.shape[0])
    top = np.sort(similarities)[-k:]
    return float(np.clip(top.mean(), 0.0, 1.0))


def score_pair(track: TrackEvidence, candidate: CandidateEvidence, identifier_weight: float) -> PairScore:
    """Fused score of one (track, candidate) pair.

    With both signals the score is their weighted sum. With one, it is that one alone — a boat whose number is
    hidden is still matchable by appearance, and a candidate without a gallery by number. With neither, 0.
    """
    id_score = similarity(track.identifier, candidate.identifier) if track.identifier and candidate.identifier else None
    has_gallery = candidate.gallery is not None and candidate.gallery.shape[0] > 0
    emb_score = (
        embedding_score(track.embedding, candidate.gallery) if track.embedding is not None and has_gallery else None
    )
    if id_score is not None and emb_score is not None:
        score = identifier_weight * id_score + (1 - identifier_weight) * emb_score
    elif id_score is not None:
        score = id_score
    elif emb_score is not None:
        score = emb_score
    else:
        score = 0.0
    return PairScore(candidate.candidate_id, score, id_score, emb_score)


def assign(
    tracks: list[TrackEvidence],
    candidates: list[CandidateEvidence],
    identifier_weight: float,
    accept_score: float,
    accept_margin: float,
) -> list[Verdict]:
    """Assign tracks to candidates one-to-one, then decide which assignments are confident enough.

    The Hungarian algorithm maximizes the total score, so each candidate is claimed by at most one track. An
    assignment is automatic only if its score reaches accept_score and leads the track's second-best candidate
    by accept_margin; every other track — unassigned, low-scoring or ambiguous — goes to review. Pairs scoring
    0 carry no evidence and are never assigned.
    """
    if not tracks:
        return []
    scores = [[score_pair(t, c, identifier_weight) for c in candidates] for t in tracks]
    matrix = np.array([[pair.score for pair in row] for row in scores]) if candidates else np.zeros((len(tracks), 0))

    assigned: dict[int, int] = {}
    if matrix.size:
        rows, cols = linear_sum_assignment(matrix, maximize=True)
        assigned = {int(r): int(c) for r, c in zip(rows, cols, strict=True) if matrix[r, c] > 0}

    verdicts = []
    for row, track in enumerate(tracks):
        ranked = sorted(scores[row], key=lambda pair: pair.score, reverse=True)
        col = assigned.get(row)
        if col is None:
            verdicts.append(Verdict(track.track_id, False, None, None, ranked[:TOP_CANDIDATES]))
            continue
        chosen = scores[row][col]
        runner_up = max((pair.score for i, pair in enumerate(scores[row]) if i != col), default=0.0)
        confident = chosen.score >= accept_score and chosen.score - runner_up >= accept_margin
        verdicts.append(
            Verdict(
                track.track_id,
                confident,
                chosen.candidate_id if confident else None,
                chosen.score if confident else None,
                ranked[:TOP_CANDIDATES],
            )
        )
    return verdicts
