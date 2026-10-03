"""Keeps schemas.py aligned with vision-server-api.yaml, the contract base-ai-backend's client is generated from.

Property names, requiredness and enum values are compared for every component schema. A field added to one
side only fails here rather than at the first call.
"""

import os
from enum import Enum
from pathlib import Path

import pytest
import yaml
from pydantic import BaseModel

from vision_server import schemas

CONTRACT = (
    Path(os.environ["VISION_CONTRACT"])
    if "VISION_CONTRACT" in os.environ
    else (
        Path(__file__).resolve().parents[3] / "libs/java-shared/api-contracts/src/main/resources/vision-server-api.yaml"
    )
)
COMPONENTS = yaml.safe_load(CONTRACT.read_text(encoding="utf-8"))["components"]["schemas"]
# Inline object properties the Java generator names <Parent><Property>; schemas.py follows the same names.
INLINE = {("VisionModels", "detector"), ("VisionModels", "embedding"), ("VisionModels", "ocr")}


def flatten(schema: dict) -> tuple[dict, set]:
    properties, required = dict(schema.get("properties", {})), set(schema.get("required", []))
    for part in schema.get("allOf", []):
        part = COMPONENTS[part["$ref"].split("/")[-1]] if "$ref" in part else part
        sub_properties, sub_required = flatten(part)
        properties |= sub_properties
        required |= sub_required
    return properties, required


def object_schemas():
    for name, schema in COMPONENTS.items():
        if "enum" not in schema:
            yield name, schema
            for prop, inline in schema.get("properties", {}).items():
                if (name, prop) in INLINE:
                    yield name + prop[0].upper() + prop[1:], inline


@pytest.mark.parametrize(("name", "schema"), list(object_schemas()), ids=lambda v: v if isinstance(v, str) else "")
def test_object_schema_matches(name, schema):
    model = getattr(schemas, name)
    assert issubclass(model, BaseModel)
    properties, required = flatten(schema)
    fields = {field.alias or key: field for key, field in model.model_fields.items()}
    assert set(fields) == set(properties)
    model_required = {alias for alias, field in fields.items() if field.is_required()}
    assert model_required == required


@pytest.mark.parametrize("name", [n for n, s in COMPONENTS.items() if "enum" in s])
def test_enum_schema_matches(name):
    model = getattr(schemas, name)
    assert issubclass(model, Enum)
    assert {member.value for member in model} == set(COMPONENTS[name]["enum"])
