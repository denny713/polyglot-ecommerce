import asyncio
import uvicorn
from fastapi import FastAPI
from contextlib import asynccontextmanager
from db.database import engine, AsyncSessionLocal, redis_client
from api import recommendation
from core.config import settings
from service.mba import run_periodically

@asynccontextmanager
async def lifespan(app: FastAPI):
    mba_task = asyncio.create_task(run_periodically(AsyncSessionLocal, redis_client)) if settings.MBA_ENABLED else None
    yield

    if mba_task:
        mba_task.cancel()
        try:
            await mba_task
        except asyncio.CancelledError:
            pass

    await engine.dispose()
    await redis_client.aclose()

app = FastAPI(title="Recommendation Service", lifespan=lifespan)

app.include_router(recommendation.router)

@app.get("/")
async def root():
    return {"message": "Recommendation Service is up and running"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=settings.PORT)
