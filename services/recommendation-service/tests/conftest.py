import os

# Settings are read when core.config is imported, so the tests must never depend
# on a local .env: these win over it, and nothing here opens a real connection.
os.environ["DATABASE_URL"] = "postgresql+asyncpg://test:test@localhost:5432/test"
os.environ["REDIS_URL"] = "redis://localhost:6379/0"
os.environ["PORT"] = "7170"

import pytest
from fakeredis import FakeAsyncRedis

from tests.helpers import FakeSession


@pytest.fixture
def db():
    return FakeSession()


@pytest.fixture
async def cache():
    client = FakeAsyncRedis(decode_responses=True)
    yield client
    await client.flushall()
    await client.aclose()
