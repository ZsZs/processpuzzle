import numpy as np

from vision_server.pipeline import encoding
from vision_server.schemas import Embedding, MatchingSettings


def test_embedding_round_trips_as_base64_float32():
    vector = np.array([0.5, -1.25, 3.0], dtype=np.float32)
    wire = Embedding(model="m", vector=encoding.to_bytes(vector)).model_dump(mode="json", by_alias=True)
    assert isinstance(wire["vector"], str)
    parsed = Embedding.model_validate(wire)
    assert np.array_equal(encoding.from_bytes(parsed.vector, 3), vector)


def test_wire_names_are_camel_case_and_defaults_match_the_contract():
    settings = MatchingSettings.model_validate({"acceptScore": 0.8})
    assert settings.accept_score == 0.8
    assert settings.model_dump(by_alias=True) == {
        "identifierWeight": 0.6,
        "acceptScore": 0.8,
        "acceptMargin": 0.1,
        "sampleFps": 3,
    }


def test_from_bytes_rejects_a_wrong_dimension():
    import pytest

    with pytest.raises(ValueError):
        encoding.from_bytes(encoding.to_bytes(np.zeros(4)), 3)
