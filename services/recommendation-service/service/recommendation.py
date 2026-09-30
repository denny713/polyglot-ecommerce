from uuid import UUID

import redis.asyncio as redis
from sqlalchemy.ext.asyncio import AsyncSession

from repository import history

REASON_BOUGHT_TOGETHER = "bought_together"
REASON_SAME_CATEGORY = "same_category"
REASON_POPULAR = "popular"


async def recommend_for_user(db: AsyncSession, cache: redis.Redis, user_id: UUID, limit: int) -> dict:
    """
    Builds the recommendation from what the user has checked out (any sales order
    status) and what is in their cart. Products already in the history are never
    recommended back.
    """
    purchased = await history.get_purchased_products(db, user_id)
    cart = await history.get_cart_products(cache, user_id)

    seed = sorted(set(purchased) | set(cart))

    return {
        "user_id": str(user_id),
        "history": {
            "purchased_product_ids": sorted(purchased),
            "cart_product_ids": sorted(cart),
        },
        "recommended_items": await _recommend(db, seed, limit, user_id),
    }


async def recommend_for_product(db: AsyncSession, product_id: int, limit: int) -> dict | None:
    """
    Builds the recommendation for one product from every sales order, whatever its
    status. Answers None when the product does not exist or is no longer sold.
    """
    if product_id not in await history.get_product_details(db, [product_id]):
        return None

    return {
        "product_id": product_id,
        "recommended_items": await _recommend(db, [product_id], limit),
    }


async def _recommend(db: AsyncSession, seed: list[int], limit: int, user_id: UUID | None = None) -> list[dict]:
    """
    Fills the list in order of relevance: products other users took along with the
    seed products, then best sellers of the same categories, then best sellers
    overall. The seed products are never part of the list.
    """
    picked: list[tuple[int, int, str]] = []

    async def fill(fetch, reason):
        remaining = limit - len(picked)
        if remaining <= 0:
            return
        exclude = seed + [product_id for product_id, _, _ in picked]
        for product_id, score in await fetch(exclude, remaining):
            picked.append((product_id, score, reason))

    if seed:
        await fill(lambda exclude, n: history.get_bought_together(db, user_id, seed, exclude, n), REASON_BOUGHT_TOGETHER)
        await fill(lambda exclude, n: history.get_same_category(db, seed, exclude, n), REASON_SAME_CATEGORY)
    await fill(lambda exclude, n: history.get_popular(db, exclude, n), REASON_POPULAR)

    details = await history.get_product_details(db, [product_id for product_id, _, _ in picked])

    return [
        {"product_id": product_id, "score": score, "reason": reason, **details[product_id]}
        for product_id, score, reason in picked
        if product_id in details
    ]
