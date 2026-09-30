from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    DATABASE_URL: str
    REDIS_URL: str
    PORT: int

    # Must match redis.cart.key-prefix of order-service
    CART_KEY_PREFIX: str = "cart"

    RECOMMENDATION_LIMIT: int = 10
    # Short, because the cart part of the history changes often
    RECOMMENDATION_CACHE_TTL: int = 300
    PRODUCT_RECOMMENDATION_CACHE_TTL: int = 3600

    # Market Basket Analysis (FP-Growth over sales_order baskets)
    MBA_ENABLED: bool = True
    # Seconds between two rebuilds of the association rules
    MBA_INTERVAL: int = 3600
    # A rule needs its itemset in at least this share of the orders...
    MBA_MIN_SUPPORT: float = 0.01
    # ...and in at least this many orders, so one order alone is never a pattern
    MBA_MIN_ORDER_COUNT: int = 2
    MBA_MIN_CONFIDENCE: float = 0.1
    # Rules must have a lift above this; 1 means the products are independent
    MBA_MIN_LIFT: float = 1.0
    MBA_MAX_ITEMSET_SIZE: int = 3
    MBA_MAX_RULES_PER_PRODUCT: int = 50

    class Config:
        env_file = ".env"

settings = Settings()
