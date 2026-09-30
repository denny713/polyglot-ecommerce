from uuid import UUID

import redis.asyncio as redis
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from core.config import settings

# Every product the user has checked out, whatever the status of the order.
# The owner of a sales order is its created_by.
PURCHASED_PRODUCTS = text("""
    SELECT d.product_id, SUM(d.quantity) AS quantity
    FROM sales_order so
    JOIN sales_order_detail d ON d.sales_order_id = so.id
    WHERE so.created_by = :user_id
      AND so.is_deleted = FALSE
      AND d.is_deleted = FALSE
    GROUP BY d.product_id
""")

# Products checked out by other users who checked out any of the seed products,
# scored by how many of those users took them. user_id is the user the list is
# for, left out of the peers; it is NULL when the list is for a product.
BOUGHT_TOGETHER = text("""
    WITH peers AS (
        SELECT DISTINCT so.created_by
        FROM sales_order so
        JOIN sales_order_detail d ON d.sales_order_id = so.id
        WHERE d.product_id = ANY(CAST(:seed AS BIGINT[]))
          AND so.created_by IS NOT NULL
          AND (CAST(:user_id AS UUID) IS NULL OR so.created_by <> CAST(:user_id AS UUID))
          AND so.is_deleted = FALSE
          AND d.is_deleted = FALSE
    )
    SELECT d.product_id, COUNT(DISTINCT so.created_by) AS score
    FROM sales_order so
    JOIN peers ON peers.created_by = so.created_by
    JOIN sales_order_detail d ON d.sales_order_id = so.id
    JOIN product p ON p.id = d.product_id
    WHERE d.product_id <> ALL(CAST(:exclude AS BIGINT[]))
      AND so.is_deleted = FALSE
      AND d.is_deleted = FALSE
      AND p.is_active = TRUE
      AND p.is_deleted = FALSE
    GROUP BY d.product_id
    ORDER BY score DESC, d.product_id
    LIMIT :limit
""")

# Best sellers of the categories the seed products belong to.
SAME_CATEGORY = text("""
    SELECT p.id AS product_id, COALESCE(SUM(d.quantity), 0) AS score
    FROM product p
    LEFT JOIN sales_order_detail d ON d.product_id = p.id AND d.is_deleted = FALSE
    WHERE p.category_id IN (
            SELECT category_id FROM product WHERE id = ANY(CAST(:seed AS BIGINT[]))
          )
      AND p.id <> ALL(CAST(:exclude AS BIGINT[]))
      AND p.is_active = TRUE
      AND p.is_deleted = FALSE
    GROUP BY p.id
    ORDER BY score DESC, p.id
    LIMIT :limit
""")

# Best sellers overall, for a user with no history yet.
POPULAR = text("""
    SELECT p.id AS product_id, COALESCE(SUM(d.quantity), 0) AS score
    FROM product p
    LEFT JOIN sales_order_detail d ON d.product_id = p.id AND d.is_deleted = FALSE
    WHERE p.id <> ALL(CAST(:exclude AS BIGINT[]))
      AND p.is_active = TRUE
      AND p.is_deleted = FALSE
    GROUP BY p.id
    ORDER BY score DESC, p.id
    LIMIT :limit
""")

# One basket per sales order, whatever its status, for the Market Basket Analysis.
ORDER_BASKETS = text("""
    SELECT ARRAY_AGG(DISTINCT d.product_id) AS product_ids
    FROM sales_order so
    JOIN sales_order_detail d ON d.sales_order_id = so.id
    WHERE so.is_deleted = FALSE
      AND d.is_deleted = FALSE
    GROUP BY so.id
""")

PRODUCT_DETAILS = text("""
    SELECT id, name, sell_price, image_url, category_id
    FROM product
    WHERE id = ANY(CAST(:ids AS BIGINT[]))
      AND is_active = TRUE
      AND is_deleted = FALSE
""")


async def get_purchased_products(db: AsyncSession, user_id: UUID) -> dict[int, int]:
    result = await db.execute(PURCHASED_PRODUCTS, {"user_id": user_id})
    return {row.product_id: int(row.quantity) for row in result}


async def get_cart_products(cache: redis.Redis, user_id: UUID) -> dict[int, int]:
    """
    Reads the cart order-service keeps in Redis, one key per product:
    {prefix}:{user_id}:{product_id} holding the quantity.
    """
    namespace = f"{settings.CART_KEY_PREFIX}:{user_id}:"
    keys = [key async for key in cache.scan_iter(match=f"{namespace}*")]
    if not keys:
        return {}

    cart = {}
    for key, value in zip(keys, await cache.mget(keys)):
        product_id = key[len(namespace):]
        # A line may expire between SCAN and MGET
        if value is None or not product_id.isdigit():
            continue
        cart[int(product_id)] = int(value)
    return cart


async def get_bought_together(db: AsyncSession, user_id: UUID | None, seed: list[int], exclude: list[int], limit: int):
    result = await db.execute(
        BOUGHT_TOGETHER, {"user_id": user_id, "seed": seed, "exclude": exclude, "limit": limit}
    )
    return [(row.product_id, int(row.score)) for row in result]


async def get_same_category(db: AsyncSession, seed: list[int], exclude: list[int], limit: int):
    result = await db.execute(SAME_CATEGORY, {"seed": seed, "exclude": exclude, "limit": limit})
    return [(row.product_id, int(row.score)) for row in result]


async def get_popular(db: AsyncSession, exclude: list[int], limit: int):
    result = await db.execute(POPULAR, {"exclude": exclude, "limit": limit})
    return [(row.product_id, int(row.score)) for row in result]


async def get_order_baskets(db: AsyncSession) -> list[frozenset[int]]:
    result = await db.execute(ORDER_BASKETS)
    return [frozenset(row.product_ids) for row in result]


async def get_product_details(db: AsyncSession, ids: list[int]) -> dict[int, dict]:
    if not ids:
        return {}
    result = await db.execute(PRODUCT_DETAILS, {"ids": ids})
    return {
        row.id: {
            "name": row.name,
            "sell_price": float(row.sell_price),
            "image_url": row.image_url,
            "category_id": row.category_id,
        }
        for row in result
    }
