import uvicorn
from fastapi import FastAPI
from contextlib import asynccontextmanager
from db.database import engine, redis_client
from api import recommendation
from core.config import settings

@asynccontextmanager
async def lifespan(app: FastAPI):
    yield

    await engine.dispose()
    await redis_client.aclose()

app = FastAPI(title="Recommendation Service", lifespan=lifespan)

app.include_router(recommendation.router)

@app.get("/")
async def root():
    return {"message": "Recommendation Service is up and running"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=settings.PORT)
