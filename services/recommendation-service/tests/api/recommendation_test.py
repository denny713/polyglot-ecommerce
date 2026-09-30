import json

import pytest
from fastapi.testclient import TestClient

from api import recommendation as api
from db.database import get_db, get_redis
from main import app

USER = "aaaaaaaa-0000-0000-0000-000000000000"


@pytest.fixture
def client(db, cache, monkeypatch):
    async def override_db():
        yield db

    async def override_redis():
        yield cache

    app.dependency_overrides[get_db] = override_db
    app.dependency_overrides[get_redis] = override_redis
    # No lifespan: the background job and the real connections stay out of it
    yield TestClient(app)
    app.dependency_overrides.clear()


class TestUserRecommendation:

    def test_builds_and_caches_on_a_miss(self, client, cache, monkeypatch):
        calls = []

        async def recommend(db, cache_, user_id, limit):
            calls.append((str(user_id), limit))
            return {"user_id": str(user_id), "recommended_items": []}

        monkeypatch.setattr(api, "recommend_for_user", recommend)

        response = client.get(f"/recommendations/users/{USER}?limit=5")

        assert response.status_code == 200
        assert response.json() == {"source": "postgresql", "data": {"user_id": USER, "recommended_items": []}}
        assert calls == [(USER, 5)]

    async def test_stores_the_result_for_a_short_while(self, client, cache, monkeypatch):
        async def recommend(db, cache_, user_id, limit):
            return {"user_id": str(user_id)}

        monkeypatch.setattr(api, "recommend_for_user", recommend)

        client.get(f"/recommendations/users/{USER}")

        assert json.loads(await cache.get(f"recom:user:{USER}:10")) == {"user_id": USER}
        assert 0 < await cache.ttl(f"recom:user:{USER}:10") <= 300

    async def test_answers_from_the_cache_on_a_hit(self, client, cache, monkeypatch):
        await cache.set(f"recom:user:{USER}:10", json.dumps({"cached": True}))

        async def recommend(*_args):
            raise AssertionError("must not be built again")

        monkeypatch.setattr(api, "recommend_for_user", recommend)

        response = client.get(f"/recommendations/users/{USER}")

        assert response.json() == {"source": "redis", "data": {"cached": True}}

    @pytest.mark.parametrize("path", [
        "/recommendations/users/not-a-uuid",
        f"/recommendations/users/{USER}?limit=0",
        f"/recommendations/users/{USER}?limit=51",
    ])
    def test_refuses_an_invalid_request(self, client, path):
        assert client.get(path).status_code == 422


class TestProductRecommendation:

    async def test_builds_and_caches_on_a_miss(self, client, cache, monkeypatch):
        async def recommend(db, cache_, product_id, limit):
            return {"product_id": product_id, "recommended_items": []}

        monkeypatch.setattr(api, "recommend_for_product", recommend)

        response = client.get("/recommendations/1?limit=3")

        assert response.json() == {"source": "postgresql", "data": {"product_id": 1, "recommended_items": []}}
        assert json.loads(await cache.get("recom:product:1:3")) == {"product_id": 1, "recommended_items": []}
        assert 3000 < await cache.ttl("recom:product:1:3") <= 3600

    async def test_answers_from_the_cache_on_a_hit(self, client, cache):
        await cache.set("recom:product:1:10", json.dumps({"cached": True}))

        response = client.get("/recommendations/1")

        assert response.json() == {"source": "redis", "data": {"cached": True}}

    async def test_answers_not_found_for_a_product_not_sold(self, client, cache, monkeypatch):
        async def recommend(*_args):
            return None

        monkeypatch.setattr(api, "recommend_for_product", recommend)

        response = client.get("/recommendations/6")

        assert response.status_code == 404
        assert response.json() == {"detail": "Product not found"}
        assert await cache.exists("recom:product:6:10") == 0

    def test_refuses_a_product_id_that_is_not_a_number(self, client):
        assert client.get("/recommendations/abc").status_code == 422


class TestMbaStatus:

    async def test_answers_how_the_rules_were_built(self, client, cache):
        await cache.set("mba:meta", json.dumps({"orders": 8, "rules": 16}))

        response = client.get("/recommendations/mba/status")

        assert response.json() == {"source": "redis", "data": {"orders": 8, "rules": 16}}

    def test_answers_not_found_before_the_first_build(self, client):
        response = client.get("/recommendations/mba/status")

        assert response.status_code == 404
