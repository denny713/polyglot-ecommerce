from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    DATABASE_URL: str
    REDIS_URL: str
    PORT: int = 7170

    # Must match redis.cart.key-prefix of order-service
    CART_KEY_PREFIX: str = "cart"

    RECOMMENDATION_LIMIT: int = 10
    # Short, because the cart part of the history changes often
    RECOMMENDATION_CACHE_TTL: int = 300
    PRODUCT_RECOMMENDATION_CACHE_TTL: int = 3600

    class Config:
        env_file = ".env"

settings = Settings()
