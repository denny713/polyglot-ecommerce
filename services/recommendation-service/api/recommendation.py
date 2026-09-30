from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
import redis.asyncio as redis
import json
from uuid import UUID

from core.config import settings
from db.database import get_db, get_redis
from repository.rules import get_meta
from service.recommendation import recommend_for_product, recommend_for_user

router = APIRouter(prefix="/recommendations", tags=["Recommendations"])

@router.get("/users/{user_id}")
async def get_user_recommendation(
    user_id: UUID,
    limit: int = Query(settings.RECOMMENDATION_LIMIT, ge=1, le=50),
    db: AsyncSession = Depends(get_db),
    cache: redis.Redis = Depends(get_redis)
):
    cache_key = f"recom:user:{user_id}:{limit}"
    cached_data = await cache.get(cache_key)
    if cached_data:
        return {"source": "redis", "data": json.loads(cached_data)}

    response_data = await recommend_for_user(db, cache, user_id, limit)

    await cache.setex(
        cache_key,
        settings.RECOMMENDATION_CACHE_TTL,
        json.dumps(response_data)
    )

    return {"source": "postgresql", "data": response_data}

@router.get("/mba/status")
async def get_mba_status(cache: redis.Redis = Depends(get_redis)):
    meta = await get_meta(cache)
    if not meta:
        raise HTTPException(status_code=404, detail="Association rules have not been built yet")
    return {"source": "redis", "data": meta}

@router.get("/{product_id}")
async def get_recommendation(
    product_id: int,
    limit: int = Query(settings.RECOMMENDATION_LIMIT, ge=1, le=50),
    db: AsyncSession = Depends(get_db),
    cache: redis.Redis = Depends(get_redis)
):
    # Product recommendations live in Redis only; a miss is built from the
    # sales order history and stored back.
    cache_key = f"recom:product:{product_id}:{limit}"
    cached_data = await cache.get(cache_key)
    if cached_data:
        return {"source": "redis", "data": json.loads(cached_data)}

    response_data = await recommend_for_product(db, cache, product_id, limit)
    if response_data is None:
        raise HTTPException(status_code=404, detail="Product not found")

    await cache.setex(
        cache_key,
        settings.PRODUCT_RECOMMENDATION_CACHE_TTL,
        json.dumps(response_data)
    )

    return {"source": "postgresql", "data": response_data}
