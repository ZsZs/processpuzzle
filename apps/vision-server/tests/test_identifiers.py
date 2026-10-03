from vision_server.pipeline.identifiers import Reading, accept, compact, normalize, similarity, vote

PATTERN = r"^[A-Z]{3} ?[0-9]{1,5}$"


def test_normalize_upper_cases_and_collapses_noise():
    assert normalize(" ger-1234\n") == "GER 1234"
    assert compact("GER 1234") == compact("GER1234") == "GER1234"


def test_accept_keeps_only_pattern_matches():
    readings = [Reading("ger 1234", 0.9), Reading("NORTH SAILS", 0.95), Reading("", 0.5)]
    assert accept(readings, PATTERN) == [Reading("GER 1234", 0.9)]


def test_accept_without_pattern_keeps_every_non_empty_reading():
    assert [r.text for r in accept([Reading("abc", 1), Reading(" ", 1)], None)] == ["ABC"]


def test_vote_pools_spellings_that_differ_only_in_spaces():
    readings = [Reading("GER 1234", 0.6), Reading("GER1234", 0.6), Reading("GER 1284", 0.9)]
    winner = vote(readings, frames=4)
    assert winner is not None
    assert compact(winner.text) == "GER1234"
    assert winner.confidence == 0.3


def test_vote_needs_readings():
    assert vote([], frames=5) is None


def test_similarity_tolerates_one_misread_digit():
    assert similarity("GER 1234", "GER1234") == 1.0
    assert 0.8 < similarity("GER 1284", "GER 1234") < 1.0
    assert similarity("", "GER 1234") == 0.0
