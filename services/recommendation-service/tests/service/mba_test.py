import asyncio
import json
from contextlib import asynccontextmanager

import pytest

from repository import history
from service import mba
from tests.helpers import row

BASKETS = [row(product_ids=ids) for ids in ([1], [1, 2], [1, 2, 3, 5], [3, 5, 7], [3, 5, 7])]


class TestRefreshRules:

    async def test_mines_the_baskets_and_stores_the_rules(self, db, cache):
        db.answer(history.ORDER_BASKETS, BASKETS)

        meta = await mba.refresh_rules(db, cache)

        assert meta["orders"] == 5
        assert meta["rules"] > 0
        assert meta["min_order_count"] == 2
        assert json.loads(await cache.get("mba:meta")) == meta
        stored = json.loads(await cache.get("mba:rules:1"))
        assert {"antecedent": [1], "consequent": [2], "support": 0.4, "confidence": 0.666667, "lift": 1.666667} in stored

    async def test_takes_the_minimum_order_count_from_the_support_when_higher(self, db, cache, monkeypatch):
        monkeypatch.setattr(mba.settings, "MBA_MIN_SUPPORT", 0.5)
        db.answer(history.ORDER_BASKETS, BASKETS)

        meta = await mba.refresh_rules(db, cache)

        # 50% of 5 orders is 2.5, so a pattern needs 3 orders: only Keyboard with Celana
        assert meta["min_order_count"] == 3
        assert await cache.smembers("mba:products") == {"3", "5"}

    async def test_clears_the_recommendations_built_from_the_old_rules(self, db, cache):
        db.answer(history.ORDER_BASKETS, BASKETS)
        await cache.set("recom:product:1:10", "{}")
        await cache.set("recom:user:someone:10", "{}")
        await cache.set("cart:someone:1", 1)

        await mba.refresh_rules(db, cache)

        assert await cache.keys("recom:*") == []
        assert await cache.get("cart:someone:1") == "1"

    async def test_works_with_no_orders_and_no_cached_recommendation(self, db, cache):
        meta = await mba.refresh_rules(db, cache)

        assert meta["orders"] == 0
        assert meta["rules"] == 0

    async def test_releases_the_lock_when_done(self, db, cache):
        await mba.refresh_rules(db, cache)

        assert await cache.exists(mba.LOCK_KEY) == 0

    async def test_releases_the_lock_when_it_fails(self, db, cache, monkeypatch):
        async def broken(_db):
            raise RuntimeError("database down")

        monkeypatch.setattr(mba.history, "get_order_baskets", broken)

        with pytest.raises(RuntimeError):
            await mba.refresh_rules(db, cache)

        assert await cache.exists(mba.LOCK_KEY) == 0

    async def test_skips_while_another_instance_holds_the_lock(self, db, cache):
        await cache.set(mba.LOCK_KEY, "other-instance", ex=60)

        assert await mba.refresh_rules(db, cache) is None
        # Neither the rules nor the other instance's lock are touched
        assert db.calls == []
        assert await cache.get(mba.LOCK_KEY) == "other-instance"


class StopLoop(Exception):
    pass


def session_factory(db):
    @asynccontextmanager
    async def open_session():
        yield db

    return open_session


class TestRunPeriodically:

    async def test_rebuilds_then_waits_the_interval(self, db, cache, monkeypatch):
        waits = []

        async def sleep(seconds):
            waits.append(seconds)
            raise StopLoop

        monkeypatch.setattr(mba.asyncio, "sleep", sleep)
        monkeypatch.setattr(mba.settings, "MBA_INTERVAL", 123)

        with pytest.raises(StopLoop):
            await mba.run_periodically(session_factory(db), cache)

        assert waits == [123]
        assert await cache.exists("mba:meta") == 1

    async def test_keeps_running_after_a_failed_rebuild(self, db, cache, monkeypatch):
        attempts = []

        async def failing_refresh(_db, _cache):
            attempts.append(1)
            raise RuntimeError("database down")

        async def sleep(_seconds):
            if len(attempts) == 2:
                raise StopLoop

        monkeypatch.setattr(mba, "refresh_rules", failing_refresh)
        monkeypatch.setattr(mba.asyncio, "sleep", sleep)

        with pytest.raises(StopLoop):
            await mba.run_periodically(session_factory(db), cache)

        assert len(attempts) == 2

    async def test_stops_when_cancelled(self, db, cache, monkeypatch):
        async def cancelled_refresh(_db, _cache):
            raise asyncio.CancelledError

        monkeypatch.setattr(mba, "refresh_rules", cancelled_refresh)

        with pytest.raises(asyncio.CancelledError):
            await mba.run_periodically(session_factory(db), cache)


class TestMain:

    async def test_rebuilds_once_and_closes_the_connections(self, db, cache, monkeypatch, capsys):
        import db.database as database

        closed = []

        class Engine:
            async def dispose(self):
                closed.append("engine")

        async def aclose():
            closed.append("redis")

        db.answer(history.ORDER_BASKETS, BASKETS)
        monkeypatch.setattr(database, "AsyncSessionLocal", session_factory(db))
        monkeypatch.setattr(database, "engine", Engine())
        monkeypatch.setattr(database, "redis_client", cache)
        monkeypatch.setattr(cache, "aclose", aclose)

        await mba._main()

        assert "'orders': 5" in capsys.readouterr().out
        assert closed == ["engine", "redis"]
