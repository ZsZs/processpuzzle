import numpy as np
import pytest

from vision_server.pipeline.matching import CandidateEvidence, TrackEvidence, assign, embedding_score, score_pair


def unit(*values):
    v = np.array(values, dtype=np.float32)
    return v / np.linalg.norm(v)


def candidate(cid, identifier=None, *gallery):
    return CandidateEvidence(cid, identifier, np.stack(gallery) if gallery else None)


def test_embedding_score_averages_the_top_three():
    gallery = np.stack([unit(1, 0), unit(1, 0), unit(1, 0), unit(0, 1)])
    assert embedding_score(unit(1, 0), gallery) == pytest.approx(1.0)


def test_score_pair_weights_both_signals():
    pair = score_pair(TrackEvidence(1, "GER 1", unit(1, 0)), candidate("a", "GER 1", unit(0, 1)), 0.6)
    assert pair.identifier_score == 1.0
    assert pair.embedding_score == 0.0
    assert pair.score == pytest.approx(0.6)


def test_score_pair_falls_back_to_the_available_signal():
    assert score_pair(TrackEvidence(1, None, unit(1, 0)), candidate("a", "GER 1", unit(1, 0)), 0.6).score == 1.0
    assert score_pair(TrackEvidence(1, "GER 1", None), candidate("a", "GER 1"), 0.6).score == 1.0
    assert score_pair(TrackEvidence(1, None, None), candidate("a", "GER 1"), 0.6).score == 0.0


def test_assign_is_one_to_one_and_sends_the_loser_to_review():
    tracks = [TrackEvidence(1, "GER 1234", None), TrackEvidence(2, "GER 1234", None)]
    verdicts = assign(tracks, [candidate("a", "GER 1234"), candidate("b", "NED 77")], 0.6, 0.75, 0.1)
    assert sum(v.auto_matched for v in verdicts) == 1
    loser = next(v for v in verdicts if not v.auto_matched)
    assert loser.candidate_id is None
    assert loser.candidates[0].candidate_id == "a"


def test_assign_requires_a_margin_over_the_runner_up():
    track = TrackEvidence(1, "GER 1234", None)
    close = assign([track], [candidate("a", "GER 1234"), candidate("b", "GER 1234")], 0.6, 0.75, 0.1)
    assert not close[0].auto_matched
    clear = assign([track], [candidate("a", "GER 1234"), candidate("b", "USA 9")], 0.6, 0.75, 0.1)
    assert clear[0].auto_matched and clear[0].candidate_id == "a"


def test_assign_never_assigns_a_pair_without_evidence():
    verdicts = assign([TrackEvidence(1, None, None)], [candidate("a", "GER 1")], 0.6, 0.0, 0.0)
    assert not verdicts[0].auto_matched


def test_assign_with_more_tracks_than_candidates():
    tracks = [TrackEvidence(i, "GER 1", None) for i in range(3)]
    verdicts = assign(tracks, [candidate("a", "GER 1")], 0.6, 0.75, 0.0)
    assert [v.auto_matched for v in verdicts].count(True) == 1
    assert all(len(v.candidates) == 1 for v in verdicts)
