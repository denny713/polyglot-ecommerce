"""
The batch job of the Market Basket Analysis: mines the association rules out of the
sales order baskets and stores them in Redis for the recommendations to read.

Runs every MBA_INTERVAL seconds inside the service, or once by hand with
`python -m service.mba`.
"""
import asyncio
import logging
import math
import time
import uuid
from datetime import datetime, timezone

import redis.asyncio as redis
from sqlalchemy.ext.asyncio import AsyncSession

from core.config import settings
from repository import history, rules as rule_store
from service.fpgrowth import association_rules, frequent_itemsets

log = logging.getLogger("uvicorn.error")

LOCK_KEY = "mba:lock"
RECOMMENDATION_CACHE_PATTERN = "recom:*"

# Deletes the lock only while it is still the one this run took
RELEASE_LOCK = """
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
"""


def _mine(baskets):
    total = len(baskets)
    min_count = max(settings.MBA_MIN_ORDER_COUNT, math.ceil(settings.MBA_MIN_SUPPORT * total))
    itemsets = frequent_itemsets(baskets, min_count, settings.MBA_MAX_ITEMSET_SIZE)
    return association_rules(itemsets, total, settings.MBA_MIN_CONFIDENCE, settings.MBA_MIN_LIFT), min_count


async def refresh_rules(db: AsyncSession, cache: redis.Redis) -> dict | None:
    """
    Rebuilds the rules. Answers what was built, or None when another instance of the
    service is rebuilding them at the same time.
    """
    token = uuid.uuid4().hex
    # The lock outlives a run that dies, but not the next interval
    if not await cache.set(LOCK_KEY, token, nx=True, ex=max(settings.MBA_INTERVAL, 60)):
        log.info("MBA rebuild skipped, another instance holds the lock")
        return None

    try:
        started = time.monotonic()
        baskets = await history.get_order_baskets(db)
        # FP-Growth is CPU bound, so it runs off the event loop
        rules, min_count = await asyncio.to_thread(_mine, baskets)

        meta = {
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "orders": len(baskets),
            "rules": len(rules),
            "min_order_count": min_count,
            "min_support": settings.MBA_MIN_SUPPORT,
            "min_confidence": settings.MBA_MIN_CONFIDENCE,
            "min_lift": settings.MBA_MIN_LIFT,
            "max_itemset_size": settings.MBA_MAX_ITEMSET_SIZE,
            "duration_seconds": round(time.monotonic() - started, 3),
        }
        await rule_store.save_rules(cache, rules, settings.MBA_MAX_RULES_PER_PRODUCT, meta)

        # The cached recommendations were built from the old rules
        stale = [key async for key in cache.scan_iter(match=RECOMMENDATION_CACHE_PATTERN)]
        if stale:
            await cache.delete(*stale)

        log.info("MBA rules rebuilt: %s", meta)
        return meta
    finally:
        await cache.eval(RELEASE_LOCK, 1, LOCK_KEY, token)


async def run_periodically(session_factory, cache: redis.Redis):
    while True:
        try:
            async with session_factory() as db:
                await refresh_rules(db, cache)
        except asyncio.CancelledError:
            raise
        except Exception:
            # A failed run keeps the previous rules and is tried again next interval
            log.exception("MBA rebuild failed")
        await asyncio.sleep(settings.MBA_INTERVAL)


async def _main():
    from db.database import AsyncSessionLocal, engine, redis_client

    try:
        async with AsyncSessionLocal() as db:
            print(await refresh_rules(db, redis_client))
    finally:
        await engine.dispose()
        await redis_client.aclose()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    asyncio.run(_main())
