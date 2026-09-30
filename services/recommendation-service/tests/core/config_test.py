import pytest
from pydantic import ValidationError

from core.config import Settings


def test_reads_the_connections_from_the_environment():
    settings = Settings()

    assert settings.DATABASE_URL == "postgresql+asyncpg://test:test@localhost:5432/test"
    assert settings.PORT == 7170


def test_has_defaults_for_the_tuning(monkeypatch):
    monkeypatch.delenv("CART_KEY_PREFIX", raising=False)
    monkeypatch.delenv("MBA_MIN_SUPPORT", raising=False)

    settings = Settings(_env_file=None)

    assert settings.CART_KEY_PREFIX == "cart"
    assert settings.MBA_MIN_SUPPORT == 0.01
    assert settings.MBA_ENABLED is True


def test_refuses_to_start_without_a_database(monkeypatch):
    monkeypatch.delenv("DATABASE_URL")

    with pytest.raises(ValidationError):
        Settings(_env_file=None)
