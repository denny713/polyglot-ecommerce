from sqlalchemy.ext.asyncio import AsyncSession

from db import database


async def test_get_db_yields_a_session_of_the_database():
    sessions = database.get_db()

    session = await anext(sessions)

    assert isinstance(session, AsyncSession)
    assert session.bind is database.engine
    await sessions.aclose()


async def test_get_redis_yields_the_shared_client():
    clients = database.get_redis()

    assert await anext(clients) is database.redis_client
    await clients.aclose()


def test_connects_where_the_settings_say():
    assert database.engine.url.render_as_string(hide_password=False) == database.settings.DATABASE_URL
    assert database.redis_client.connection_pool.connection_kwargs["port"] == 6379
