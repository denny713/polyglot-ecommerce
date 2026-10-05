import asyncio

import pytest
from fastapi.testclient import TestClient

import main


@pytest.fixture
def closed(monkeypatch):
    """Stands in for the real connections, recording that they were closed."""
    closed = []

    class Engine:
        async def dispose(self):
            closed.append("engine")

    class Redis:
        async def aclose(self):
            closed.append("redis")

    monkeypatch.setattr(main, "engine", Engine())
    monkeypatch.setattr(main, "redis_client", Redis())
    return closed


def test_root_says_the_service_is_up():
    response = TestClient(main.app).get("/")

    assert response.json() == {"message": "Recommendation Service is up and running"}


def test_lifespan_runs_the_mba_job_until_shutdown(closed, monkeypatch):
    state = {}

    async def run_periodically(session_factory, cache):
        state["started"] = True
        try:
            await asyncio.Event().wait()
        except asyncio.CancelledError:
            state["cancelled"] = True
            raise

    monkeypatch.setattr(main, "run_periodically", run_periodically)
    monkeypatch.setattr(main.settings, "MBA_ENABLED", True)

    with TestClient(main.app) as client:
        client.get("/")

    assert state == {"started": True, "cancelled": True}
    assert closed == ["engine", "redis"]


def test_lifespan_leaves_the_mba_job_out_when_disabled(closed, monkeypatch):
    async def run_periodically(session_factory, cache):
        raise AssertionError("must not run")

    monkeypatch.setattr(main, "run_periodically", run_periodically)
    monkeypatch.setattr(main.settings, "MBA_ENABLED", False)

    with TestClient(main.app) as client:
        client.get("/")

    assert closed == ["engine", "redis"]
