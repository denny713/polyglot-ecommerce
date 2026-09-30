"""
The shapes the endpoints answer with, which is also what Swagger UI shows. The
responses are validated against them, so the documentation cannot drift from what
the service really sends.
"""
from typing import Literal

from pydantic import BaseModel, Field

Source = Literal["postgresql", "redis"]
Reason = Literal["frequently_bought_together", "bought_together", "same_category", "popular"]

SOURCE_DESCRIPTION = "`postgresql` when built for this request, `redis` when served from the cache"


class AssociationRule(BaseModel):
    antecedent: list[int] = Field(description="Products that, bought together, lead to the consequent")
    consequent: list[int] = Field(description="Products bought along with the antecedent")
    support: float = Field(description="Share of all orders holding every product of the rule")
    confidence: float = Field(description="Of the orders holding the antecedent, the share also holding the consequent")
    lift: float = Field(description="Confidence divided by how common the consequent is; above 1 means they go together")


class RecommendedItem(BaseModel):
    product_id: int
    score: int | float = Field(
        description="Confidence for `frequently_bought_together`, customers for `bought_together`, "
                    "quantity sold otherwise"
    )
    reason: Reason = Field(description="The step of the recommendation that found the product")
    name: str
    sell_price: float
    image_url: str | None
    category_id: int
    rule: AssociationRule | None = Field(
        default=None, description="The association rule behind a `frequently_bought_together` item only"
    )


class UserHistory(BaseModel):
    purchased_product_ids: list[int] = Field(description="Products in the customer's sales orders, any status")
    cart_product_ids: list[int] = Field(description="Products in the customer's cart")


class UserRecommendation(BaseModel):
    user_id: str
    history: UserHistory
    recommended_items: list[RecommendedItem]


class ProductRecommendation(BaseModel):
    product_id: int
    recommended_items: list[RecommendedItem]


class MbaMeta(BaseModel):
    generated_at: str = Field(description="When the rules were built, ISO 8601 in UTC")
    orders: int = Field(description="Sales orders the rules were mined from")
    rules: int = Field(description="Association rules found")
    min_order_count: int = Field(description="Orders a pattern had to appear in")
    min_support: float
    min_confidence: float
    min_lift: float
    max_itemset_size: int
    duration_seconds: float


MOUSE = {
    "product_id": 2,
    "score": 0.4,
    "reason": "frequently_bought_together",
    "name": "Mouse",
    "sell_price": 15.0,
    "image_url": None,
    "category_id": 1,
    "rule": {"antecedent": [1], "consequent": [2], "support": 0.25, "confidence": 0.4, "lift": 1.6},
}
KEYBOARD = {
    "product_id": 3,
    "score": 3,
    "reason": "bought_together",
    "name": "Keyboard",
    "sell_price": 30.0,
    "image_url": None,
    "category_id": 1,
}


class UserRecommendationResponse(BaseModel):
    source: Source = Field(description=SOURCE_DESCRIPTION)
    data: UserRecommendation

    model_config = {"json_schema_extra": {"examples": [{
        "source": "postgresql",
        "data": {
            "user_id": "aaaaaaaa-0000-0000-0000-000000000000",
            "history": {"purchased_product_ids": [1], "cart_product_ids": [4]},
            "recommended_items": [MOUSE, KEYBOARD],
        },
    }]}}


class ProductRecommendationResponse(BaseModel):
    source: Source = Field(description=SOURCE_DESCRIPTION)
    data: ProductRecommendation

    model_config = {"json_schema_extra": {"examples": [{
        "source": "redis",
        "data": {"product_id": 1, "recommended_items": [MOUSE, KEYBOARD]},
    }]}}


class MbaStatusResponse(BaseModel):
    source: Literal["redis"]
    data: MbaMeta

    model_config = {"json_schema_extra": {"examples": [{
        "source": "redis",
        "data": {
            "generated_at": "2026-09-30T08:19:50.947548+00:00",
            "orders": 8,
            "rules": 16,
            "min_order_count": 2,
            "min_support": 0.01,
            "min_confidence": 0.1,
            "min_lift": 1.0,
            "max_itemset_size": 3,
            "duration_seconds": 0.099,
        },
    }]}}


class ErrorResponse(BaseModel):
    detail: str


class TokenErrorDetail(BaseModel):
    timestamp: str
    status: int
    error: str


class TokenErrorResponse(BaseModel):
    """The error shape of the other services, answered when the access token is refused."""

    code: int
    status: str
    data: TokenErrorDetail

    model_config = {"json_schema_extra": {"examples": [{
        "code": 401,
        "status": "Unauthorized",
        "data": {"timestamp": "2026-09-30T15:30:00.123456", "status": 401, "error": "No access token found, please login first"},
    }]}}


class HealthResponse(BaseModel):
    message: str
