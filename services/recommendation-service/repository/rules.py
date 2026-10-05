import json

import redis.asyncio as redis

from service.fpgrowth import Rule

# mba:rules:{product_id} holds every rule whose antecedent includes the product,
# mba:products the products that have such a key, mba:meta how the rules were built.
RULES_KEY = "mba:rules:{}"
PRODUCTS_KEY = "mba:products"
META_KEY = "mba:meta"


def _to_dict(rule: Rule) -> dict:
    return {
        "antecedent": sorted(rule.antecedent),
        "consequent": sorted(rule.consequent),
        "support": round(rule.support, 6),
        "confidence": round(rule.confidence, 6),
        "lift": round(rule.lift, 6),
    }


async def save_rules(cache: redis.Redis, rules: list[Rule], max_per_product: int, meta: dict):
    """
    Replaces the stored rules in one transaction, so a reader sees either the old
    set or the new one, never half of each.
    """
    by_product: dict[int, list[Rule]] = {}
    for rule in rules:
        for product_id in rule.antecedent:
            by_product.setdefault(product_id, []).append(rule)

    old_products = await cache.smembers(PRODUCTS_KEY)
    stale = [RULES_KEY.format(product_id) for product_id in old_products if int(product_id) not in by_product]

    async with cache.pipeline(transaction=True) as pipe:
        if stale:
            pipe.delete(*stale)
        pipe.delete(PRODUCTS_KEY)
        for product_id, product_rules in by_product.items():
            product_rules.sort(key=lambda rule: (-rule.confidence, -rule.lift, -rule.support))
            pipe.set(RULES_KEY.format(product_id), json.dumps([_to_dict(rule) for rule in product_rules[:max_per_product]]))
        if by_product:
            pipe.sadd(PRODUCTS_KEY, *by_product)
        pipe.set(META_KEY, json.dumps(meta))
        await pipe.execute()


async def get_rules(cache: redis.Redis, product_ids: list[int]) -> list[dict]:
    """Every stored rule whose antecedent includes one of product_ids, once each."""
    if not product_ids:
        return []

    rules = {}
    for value in await cache.mget([RULES_KEY.format(product_id) for product_id in product_ids]):
        for rule in json.loads(value) if value else []:
            rules[(tuple(rule["antecedent"]), tuple(rule["consequent"]))] = rule
    return list(rules.values())


async def get_meta(cache: redis.Redis) -> dict | None:
    value = await cache.get(META_KEY)
    return json.loads(value) if value else None
