from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import declarative_base
import redis.asyncio as redis
from core.config import settings

# --- PostgreSQL Setup ---
engine = create_async_engine(settings.DATABASE_URL, echo=True)
AsyncSessionLocal = async_sessionmaker(
    bind=engine, class_=AsyncSession, expire_on_commit=False
)
Base = declarative_base()

async def get_db():
    async with AsyncSessionLocal() as session:
        yield session

# --- Source PostgreSQL Setup (order-service database, read only) ---
source_engine = create_async_engine(settings.SOURCE_DATABASE_URL, echo=True)
SourceSessionLocal = async_sessionmaker(
    bind=source_engine, class_=AsyncSession, expire_on_commit=False
)

async def get_source_db():
    async with SourceSessionLocal() as session:
        yield session

# --- Redis Setup ---
redis_client = redis.from_url(settings.REDIS_URL, decode_responses=True)

async def get_redis():
    yield redis_client
