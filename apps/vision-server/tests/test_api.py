"""The HTTP surface and the job queue, against a fake pipeline. No model is loaded."""

import base64
import threading
import time
import uuid

import numpy as np
import pytest
from fastapi.testclient import TestClient

from vision_server.app import create_app
from vision_server.config import Settings
from vision_server.jobs import JobManager
from vision_server.pipeline import encoding
from vision_server.schemas import (
    EnrollmentJobResult,
    RecognitionJobResult,
    VisionModels,
    VisionModelsDetector,
    VisionModelsEmbedding,
)

TOKEN = "test-token"
AUTH = {"Authorization": f"Bearer {TOKEN}"}
MODEL = "test/embedder"


class FakePipeline:
    def __init__(self):
        self.gate = threading.Event()
        self.gate.set()
        self.order: list[str] = []
        self.released = 0

    def models(self):
        return VisionModels(
            detector=VisionModelsDetector(model="test/detector", classes=["boat"]),
            embedding=VisionModelsEmbedding(model=MODEL, dimension=3),
            device="cpu",
        )

    def enroll(self, request, ctx):
        self.order.append(request.requester.stack)
        while not self.gate.wait(0.01):
            ctx.progress(0.5)
        return EnrollmentJobResult(job_id=request.job_id, photos=[])

    def embed(self, request, ctx):
        raise NotImplementedError

    def recognize(self, request, ctx):
        return RecognitionJobResult(job_id=request.job_id, tracks=[])

    def release(self):
        self.released += 1


class RecordingNotifier:
    def __init__(self):
        self.calls = []

    def __call__(self, request, notification):
        self.calls.append((request.callback_token, notification))


@pytest.fixture
def env():
    settings = Settings(service_token=TOKEN, max_queued_jobs=3, idle_unload_seconds=0)
    pipeline, notifier = FakePipeline(), RecordingNotifier()
    manager = JobManager(settings, pipeline, notifier)
    with TestClient(create_app(settings, pipeline, manager)) as client:
        yield client, pipeline, notifier


def enrollment(job_id=None, stack="testbed"):
    return {
        "jobId": str(job_id or uuid.uuid4()),
        "requester": {"stack": stack, "orgKey": "my-org"},
        "callbackUrl": "http://backend/api/v1/organizations/my-org/vision-notifications",
        "callbackToken": "per-job-secret",
        "detection": {"detectorClass": "boat"},
        "photos": [{"mediaId": "p1", "url": "http://minio/p1.jpg"}],
    }


def recognition(gallery_model=MODEL, dimension=3):
    vector = base64.b64encode(encoding.to_bytes(np.ones(dimension))).decode()
    return {
        "jobId": str(uuid.uuid4()),
        "requester": {"stack": "testbed", "orgKey": "my-org"},
        "callbackUrl": "http://backend/cb",
        "callbackToken": "t",
        "detection": {"detectorClass": "boat"},
        "video": {"mediaId": "v", "url": "http://minio/v.mp4"},
        "candidates": [
            {"candidateId": "b-1", "identifierText": "GER 1", "gallery": [{"model": gallery_model, "vector": vector}]}
        ],
    }


def wait_for(client, job_id, status, timeout=5.0):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        job = client.get(f"/v1/jobs/{job_id}", headers=AUTH).json()
        if job["status"] == status:
            return job
        time.sleep(0.02)
    raise AssertionError(f"job {job_id} never reached {status}: {job}")


def test_health_needs_no_token(env):
    client, _, _ = env
    assert client.get("/v1/health").json() == {"status": "UP", "queued": 0, "running": 0}


def test_every_other_call_needs_the_service_token(env):
    client, _, _ = env
    response = client.get("/v1/models", headers={"Authorization": "Bearer wrong"})
    assert response.status_code == 401
    assert response.headers["content-type"] == "application/problem+json"
    assert client.post("/v1/enrollment-jobs", json=enrollment()).status_code == 401


def test_a_job_runs_notifies_and_serves_its_result(env):
    client, _, notifier = env
    body = enrollment()
    accepted = client.post("/v1/enrollment-jobs", json=body, headers=AUTH)
    assert accepted.status_code == 202
    assert accepted.json()["kind"] == "ENROLLMENT"

    done = wait_for(client, body["jobId"], "DONE")
    assert done["progress"] == 1.0 and done["resultExpiresAt"]
    result = client.get(f"/v1/enrollment-jobs/{body['jobId']}/result", headers=AUTH)
    assert result.status_code == 200 and result.json() == {"jobId": body["jobId"], "photos": []}

    deadline = time.monotonic() + 2
    while not notifier.calls and time.monotonic() < deadline:
        time.sleep(0.01)
    token, notification = notifier.calls[0]
    assert token == "per-job-secret"
    assert (str(notification.job_id), notification.status) == (body["jobId"], "DONE")


def test_submitting_the_same_job_id_twice_is_idempotent(env):
    client, pipeline, _ = env
    body = enrollment()
    client.post("/v1/enrollment-jobs", json=body, headers=AUTH)
    again = client.post("/v1/enrollment-jobs", json=body, headers=AUTH)
    assert again.status_code == 202 and again.json()["jobId"] == body["jobId"]
    wait_for(client, body["jobId"], "DONE")
    assert len(pipeline.order) == 1


def test_a_result_is_409_until_done_and_404_for_another_kind(env):
    client, pipeline, _ = env
    pipeline.gate.clear()
    body = enrollment()
    client.post("/v1/enrollment-jobs", json=body, headers=AUTH)
    wait_for(client, body["jobId"], "RUNNING")
    assert client.get(f"/v1/enrollment-jobs/{body['jobId']}/result", headers=AUTH).status_code == 409
    assert client.get(f"/v1/recognition-jobs/{body['jobId']}/result", headers=AUTH).status_code == 404
    pipeline.gate.set()


def test_deleting_a_running_job_cancels_it(env):
    client, pipeline, notifier = env
    pipeline.gate.clear()
    body = enrollment()
    client.post("/v1/enrollment-jobs", json=body, headers=AUTH)
    wait_for(client, body["jobId"], "RUNNING")
    assert client.delete(f"/v1/jobs/{body['jobId']}", headers=AUTH).status_code == 204
    wait_for(client, body["jobId"], "CANCELLED")
    # A finished job is discarded by a second delete.
    assert client.delete(f"/v1/jobs/{body['jobId']}", headers=AUTH).status_code == 204
    assert client.get(f"/v1/jobs/{body['jobId']}", headers=AUTH).status_code == 404


def test_a_full_queue_answers_503_with_retry_after(env):
    client, pipeline, _ = env
    pipeline.gate.clear()
    first = enrollment()
    client.post("/v1/enrollment-jobs", json=first, headers=AUTH)
    wait_for(client, first["jobId"], "RUNNING")
    for _ in range(3):
        assert client.post("/v1/enrollment-jobs", json=enrollment(), headers=AUTH).status_code == 202
    full = client.post("/v1/enrollment-jobs", json=enrollment(), headers=AUTH)
    assert full.status_code == 503 and full.headers["retry-after"] == "60"
    pipeline.gate.set()


def test_stacks_take_turns(env):
    client, pipeline, _ = env
    pipeline.gate.clear()
    blocker = enrollment(stack="a")
    client.post("/v1/enrollment-jobs", json=blocker, headers=AUTH)
    wait_for(client, blocker["jobId"], "RUNNING")
    last = None
    for stack in ["a", "a", "b"]:
        last = enrollment(stack=stack)
        client.post("/v1/enrollment-jobs", json=last, headers=AUTH)
    pipeline.gate.set()
    wait_for(client, last["jobId"], "DONE")
    time.sleep(0.2)
    assert pipeline.order == ["a", "a", "b", "a"]


def test_recognition_refuses_a_gallery_of_another_model(env):
    client, _, _ = env
    stale = client.post("/v1/recognition-jobs", json=recognition(gallery_model="old/model"), headers=AUTH)
    assert stale.status_code == 409
    wrong_size = client.post("/v1/recognition-jobs", json=recognition(dimension=4), headers=AUTH)
    assert wrong_size.status_code == 400
    assert client.post("/v1/recognition-jobs", json=recognition(), headers=AUTH).status_code == 202


def test_an_invalid_body_is_a_400_problem(env):
    client, _, _ = env
    body = enrollment()
    body["photos"] = []
    response = client.post("/v1/enrollment-jobs", json=body, headers=AUTH)
    assert response.status_code == 400
    assert "photos" in response.json()["detail"]
