from uuid import UUID

import redis.asyncio as redis
from sqlalchemy.ext.asyncio import AsyncSession

from repository import history, rules as rule_store

REASON_FREQUENTLY_BOUGHT_TOGETHER = "frequently_bought_together"
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
        "recommended_items": await _recommend(db, cache, seed, limit, user_id),
    }


async def recommend_for_product(db: AsyncSession, cache: redis.Redis, product_id: int, limit: int) -> dict | None:
    """
    Builds the recommendation for one product from every sales order, whatever its
    status. Answers None when the product does not exist or is no longer sold.
    """
    if product_id not in await history.get_product_details(db, [product_id]):
        return None

    return {
        "product_id": product_id,
        "recommended_items": await _recommend(db, cache, [product_id], limit),
    }


async def _frequently_bought_together(
    db: AsyncSession, cache: redis.Redis, seed: list[int], exclude: list[int], limit: int
):
    """
    Products the association rules of the Market Basket Analysis put in the same
    order as the seed. A rule applies when the seed holds its whole antecedent, so
    for a product page, only the rules whose antecedent is that product alone. Each
    product is scored by the most confident rule leading to it.
    """
    seed_set = set(seed)
    excluded = set(exclude)
    best = {}
    for rule in await rule_store.get_rules(cache, seed):
        if not set(rule["antecedent"]) <= seed_set:
            continue
        for product_id in rule["consequent"]:
            if product_id in excluded:
                continue
            current = best.get(product_id)
            if current is None or (rule["confidence"], rule["lift"]) > (current["confidence"], current["lift"]):
                best[product_id] = rule

    # The rules are mined from every order, so they may lead to a product no longer sold
    sold = await history.get_product_details(db, list(best))
    ranked = sorted(
        ((product_id, rule) for product_id, rule in best.items() if product_id in sold),
        key=lambda item: (-item[1]["confidence"], -item[1]["lift"], item[0]),
    )
    return [(product_id, rule["confidence"], rule) for product_id, rule in ranked[:limit]]


async def _recommend(
    db: AsyncSession,
    cache: redis.Redis,
    seed: list[int],
    limit: int,
    user_id: UUID | None = None,
) -> list[dict]:
    """
    Fills the list in order of relevance: products the association rules put in the
    same order as the seed products, then products other users took along with them,
    then best sellers of the same categories, then best sellers overall. The seed
    products are never part of the list.
    """
    picked: list[tuple[int, float, str, dict | None]] = []

    async def fill(fetch, reason):
        remaining = limit - len(picked)
        if remaining <= 0:
            return
        exclude = seed + [product_id for product_id, _, _, _ in picked]
        for product_id, score, *rule in await fetch(exclude, remaining):
            picked.append((product_id, score, reason, rule[0] if rule else None))

    if seed:
        await fill(
            lambda exclude, n: _frequently_bought_together(db, cache, seed, exclude, n),
            REASON_FREQUENTLY_BOUGHT_TOGETHER,
        )
        await fill(lambda exclude, n: history.get_bought_together(db, user_id, seed, exclude, n), REASON_BOUGHT_TOGETHER)
        await fill(lambda exclude, n: history.get_same_category(db, seed, exclude, n), REASON_SAME_CATEGORY)
    await fill(lambda exclude, n: history.get_popular(db, exclude, n), REASON_POPULAR)

    details = await history.get_product_details(db, [product_id for product_id, _, _, _ in picked])

    items = []
    for product_id, score, reason, rule in picked:
        if product_id not in details:
            continue
        item = {"product_id": product_id, "score": score, "reason": reason, **details[product_id]}
        if rule:
            item["rule"] = rule
        items.append(item)
    return items
