from fastapi import APIRouter, Depends, HTTPException, Path, Query
from sqlalchemy.ext.asyncio import AsyncSession
import redis.asyncio as redis
import json
from uuid import UUID

from core.config import settings
from core.security import Principal, authenticate, forbidden, require_admin
from db.database import get_db, get_redis
from repository.rules import get_meta
from schemas.recommendation import (
    ErrorResponse,
    TokenErrorResponse,
    MbaStatusResponse,
    ProductRecommendationResponse,
    UserRecommendationResponse,
)
from service.recommendation import recommend_for_product, recommend_for_user

# Every endpoint needs an access token; the ones below narrow who may call them
router = APIRouter(
    prefix="/recommendations",
    tags=["Recommendations"],
    dependencies=[Depends(authenticate)],
    responses={
        400: {"model": TokenErrorResponse, "description": "The access token is malformed"},
        401: {"model": TokenErrorResponse, "description": "No access token, or an invalid or expired one"},
        403: {"model": TokenErrorResponse, "description": "The access token lacks the role this needs"},
    },
)

LIMIT = Query(settings.RECOMMENDATION_LIMIT, ge=1, le=50, description="How many products to recommend, 1 to 50")

@router.get(
    "/users/{user_id}",
    response_model=UserRecommendationResponse,
    response_model_exclude_unset=True,
    summary="Recommend products to a customer",
    description=(
        "Built from every product in the customer's sales orders, whatever their status, and in their cart. "
        "Filled in four steps while places are left: `frequently_bought_together` (association rules of the "
        "Market Basket Analysis), `bought_together` (what other customers who bought the same products also "
        "bought), `same_category` and `popular`. Products already in the history are never recommended. "
        f"Cached for {settings.RECOMMENDATION_CACHE_TTL} seconds.\n\n"
        "Needs the `user` or `admin` role; a `user` may only ask for their own recommendations."
    ),
)
async def get_user_recommendation(
    user_id: UUID = Path(description="The customer, as in `sales_order.created_by`"),
    limit: int = LIMIT,
    principal: Principal = Depends(authenticate),
    db: AsyncSession = Depends(get_db),
    cache: redis.Redis = Depends(get_redis)
):
    # A customer's history is theirs: nobody but an admin reads someone else's
    if principal.user_id != user_id and not principal.is_admin:
        raise forbidden()

    cache_key = f"recom:user:{user_id}:{limit}"
    cached_data = await cache.get(cache_key)
    if cached_data:
        return {"source": "redis", "data": json.loads(cached_data)}

    response_data = await recommend_for_user(db, cache, user_id, limit)

    await cache.set(
        cache_key,
        json.dumps(response_data),
        ex=settings.RECOMMENDATION_CACHE_TTL
    )

    return {"source": "postgresql", "data": response_data}

@router.get(
    "/mba/status",
    response_model=MbaStatusResponse,
    summary="Tell how the association rules were built",
    description=(
        "When the Market Basket Analysis last rebuilt its rules, from how many orders, and with which "
        "thresholds. Needs the `admin` role."
    ),
    responses={404: {"model": ErrorResponse, "description": "The rules have not been built yet"}},
)
async def get_mba_status(
    _: Principal = Depends(require_admin),
    cache: redis.Redis = Depends(get_redis)
):
    meta = await get_meta(cache)
    if not meta:
        raise HTTPException(status_code=404, detail="Association rules have not been built yet")
    return {"source": "redis", "data": meta}

@router.get(
    "/{product_id}",
    response_model=ProductRecommendationResponse,
    response_model_exclude_unset=True,
    summary="Recommend products to show alongside a product",
    description=(
        "The same four steps as for a customer, with the product alone as the history. "
        f"Kept in Redis only, for {settings.PRODUCT_RECOMMENDATION_CACHE_TTL} seconds.\n\n"
        "Needs the `user` or `admin` role."
    ),
    responses={404: {"model": ErrorResponse, "description": "The product does not exist or is no longer sold"}},
)
async def get_recommendation(
    product_id: int = Path(description="The product, as in `product.id`"),
    limit: int = LIMIT,
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

    await cache.set(
        cache_key,
        json.dumps(response_data),
        ex=settings.PRODUCT_RECOMMENDATION_CACHE_TTL
    )

    return {"source": "postgresql", "data": response_data}
