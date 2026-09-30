import asyncio
import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
from db.database import engine, AsyncSessionLocal, redis_client
from api import recommendation
from core.config import settings
from core.security import TokenError
from schemas.recommendation import HealthResponse
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

app = FastAPI(
    title="API for Recommendation Service",
    version="1.0.0",
    description=(
        "API documentation for Recommendation Service application.\n\n"
        "Recommends products from the sales orders, whatever their status, and the cart, "
        "with Market Basket Analysis (FP-Growth) first and collaborative filtering, same "
        "category and best sellers after it."
    ),
    openapi_tags=[
        {"name": "Recommendations", "description": "Products to recommend to a customer or alongside a product"},
        {"name": "Health", "description": "Whether the service is up"},
    ],
    lifespan=lifespan,
)

@app.exception_handler(TokenError)
async def token_error(request: Request, exc: TokenError):
    return JSONResponse(status_code=exc.code, content=exc.body())

app.include_router(recommendation.router)

@app.get("/", tags=["Health"], summary="Tell the service is up", response_model=HealthResponse)
async def root():
    return {"message": "Recommendation Service is up and running"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=settings.PORT)
