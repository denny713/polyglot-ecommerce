import json

import pytest
from fastapi.testclient import TestClient

from uuid import UUID

from api import recommendation as api
from core.security import Principal, authenticate, unauthorized
from db.database import get_db, get_redis
from main import app

USER = "aaaaaaaa-0000-0000-0000-000000000000"
OTHER_USER = "bbbbbbbb-0000-0000-0000-000000000000"

ADMIN = Principal(UUID("cccccccc-0000-0000-0000-000000000000"), frozenset({"admin"}))
CUSTOMER = Principal(UUID(USER), frozenset({"user"}))

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
    "image_url": "http://minio/keyboard.png",
    "category_id": 1,
}
USER_DATA = {
    "user_id": USER,
    "history": {"purchased_product_ids": [1], "cart_product_ids": [4]},
    "recommended_items": [MOUSE, KEYBOARD],
}
PRODUCT_DATA = {"product_id": 1, "recommended_items": [MOUSE, KEYBOARD]}
META = {
    "generated_at": "2026-09-30T08:19:50.947548+00:00",
    "orders": 8,
    "rules": 16,
    "min_order_count": 2,
    "min_support": 0.01,
    "min_confidence": 0.1,
    "min_lift": 1.0,
    "max_itemset_size": 3,
    "duration_seconds": 0.099,
}


@pytest.fixture
def auth():
    """Who the request is made as; the token itself is tested in core/security_test.py."""
    return {"principal": ADMIN}


@pytest.fixture
def client(db, cache, auth):
    async def override_db():
        yield db

    async def override_redis():
        yield cache

    def override_authenticate():
        if auth["principal"] is None:
            raise unauthorized("No access token found, please login first")
        return auth["principal"]

    app.dependency_overrides[get_db] = override_db
    app.dependency_overrides[get_redis] = override_redis
    app.dependency_overrides[authenticate] = override_authenticate
    # No lifespan: the background job and the real connections stay out of it
    yield TestClient(app)
    app.dependency_overrides.clear()


class TestUserRecommendation:

    def test_builds_on_a_miss(self, client, monkeypatch):
        calls = []

        async def recommend(db, cache_, user_id, limit):
            calls.append((str(user_id), limit))
            return USER_DATA

        monkeypatch.setattr(api, "recommend_for_user", recommend)

        response = client.get(f"/recommendations/users/{USER}?limit=5")

        assert response.status_code == 200
        assert response.json() == {"source": "postgresql", "data": USER_DATA}
        assert calls == [(USER, 5)]

    async def test_stores_the_result_for_a_short_while(self, client, cache, monkeypatch):
        async def recommend(db, cache_, user_id, limit):
            return USER_DATA

        monkeypatch.setattr(api, "recommend_for_user", recommend)

        client.get(f"/recommendations/users/{USER}")

        assert json.loads(await cache.get(f"recom:user:{USER}:10")) == USER_DATA
        assert 0 < await cache.ttl(f"recom:user:{USER}:10") <= 300

    async def test_answers_from_the_cache_on_a_hit(self, client, cache, monkeypatch):
        await cache.set(f"recom:user:{USER}:10", json.dumps(USER_DATA))

        async def recommend(*_args):
            raise AssertionError("must not be built again")

        monkeypatch.setattr(api, "recommend_for_user", recommend)

        response = client.get(f"/recommendations/users/{USER}")

        assert response.json() == {"source": "redis", "data": USER_DATA}

    def test_answers_exactly_what_was_built(self, client, monkeypatch):
        async def recommend(*_args):
            return USER_DATA

        monkeypatch.setattr(api, "recommend_for_user", recommend)

        items = client.get(f"/recommendations/users/{USER}").json()["data"]["recommended_items"]

        # The response model neither adds a rule to an item that has none, nor
        # turns a whole score into a fraction
        assert "rule" not in items[1]
        assert isinstance(items[1]["score"], int)
        assert items[0]["image_url"] is None

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
            assert (product_id, limit) == (1, 3)
            return PRODUCT_DATA

        monkeypatch.setattr(api, "recommend_for_product", recommend)

        response = client.get("/recommendations/1?limit=3")

        assert response.json() == {"source": "postgresql", "data": PRODUCT_DATA}
        assert json.loads(await cache.get("recom:product:1:3")) == PRODUCT_DATA
        assert 3000 < await cache.ttl("recom:product:1:3") <= 3600

    async def test_answers_from_the_cache_on_a_hit(self, client, cache):
        await cache.set("recom:product:1:10", json.dumps(PRODUCT_DATA))

        response = client.get("/recommendations/1")

        assert response.json() == {"source": "redis", "data": PRODUCT_DATA}

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
        await cache.set("mba:meta", json.dumps(META))

        response = client.get("/recommendations/mba/status")

        assert response.json() == {"source": "redis", "data": META}

    def test_answers_not_found_before_the_first_build(self, client):
        response = client.get("/recommendations/mba/status")

        assert response.status_code == 404
        assert response.json() == {"detail": "Association rules have not been built yet"}


class TestAccess:

    def recommend_anything(self, monkeypatch):
        async def recommend_user(*_args):
            return USER_DATA

        async def recommend_product(*_args):
            return PRODUCT_DATA

        monkeypatch.setattr(api, "recommend_for_user", recommend_user)
        monkeypatch.setattr(api, "recommend_for_product", recommend_product)

    @pytest.mark.parametrize("path", [f"/recommendations/users/{USER}", "/recommendations/1", "/recommendations/mba/status"])
    def test_refuses_every_endpoint_without_a_token(self, client, auth, path):
        auth["principal"] = None

        response = client.get(path)

        assert response.status_code == 401
        body = response.json()
        assert (body["code"], body["status"], body["data"]["status"]) == (401, "Unauthorized", 401)
        assert body["data"]["error"] == "No access token found, please login first"
        assert "timestamp" in body["data"]

    def test_lets_a_customer_read_their_own_recommendations(self, client, auth, monkeypatch):
        self.recommend_anything(monkeypatch)
        auth["principal"] = CUSTOMER

        assert client.get(f"/recommendations/users/{USER}").status_code == 200

    async def test_keeps_a_customer_out_of_someone_elses_recommendations(self, client, auth, cache, monkeypatch):
        self.recommend_anything(monkeypatch)
        auth["principal"] = CUSTOMER
        # Not even a recommendation already cached for the other customer
        await cache.set(f"recom:user:{OTHER_USER}:10", json.dumps(USER_DATA))

        response = client.get(f"/recommendations/users/{OTHER_USER}")

        assert response.status_code == 403
        assert response.json()["data"]["error"] == "You don't have permission to access this resource"

    def test_lets_an_admin_read_anyones_recommendations(self, client, auth, monkeypatch):
        self.recommend_anything(monkeypatch)
        auth["principal"] = ADMIN

        assert client.get(f"/recommendations/users/{OTHER_USER}").status_code == 200

    def test_lets_a_customer_read_a_products_recommendations(self, client, auth, monkeypatch):
        self.recommend_anything(monkeypatch)
        auth["principal"] = CUSTOMER

        assert client.get("/recommendations/1").status_code == 200

    def test_keeps_the_mba_status_to_admins(self, client, auth):
        auth["principal"] = CUSTOMER

        response = client.get("/recommendations/mba/status")

        assert response.status_code == 403
        assert response.json()["status"] == "Forbidden"

    @pytest.mark.parametrize("path", ["/", "/docs", "/redoc", "/openapi.json"])
    def test_leaves_the_health_check_and_the_documentation_open(self, client, auth, path):
        auth["principal"] = None

        assert client.get(path).status_code == 200


class TestSwagger:

    def test_declares_the_bearer_token(self, client):
        document = client.get("/openapi.json").json()

        assert document["components"]["securitySchemes"]["bearerAuth"] == {
            "type": "http",
            "scheme": "bearer",
            "bearerFormat": "JWT",
            "description": "Access token of the Keycloak realm, from `POST /api/auth/login` of auth-service",
        }
        secured = document["paths"]["/recommendations/{product_id}"]["get"]["security"]
        assert secured == [{"bearerAuth": []}]
        assert "security" not in document["paths"]["/"]["get"]


    def test_serves_swagger_ui_and_redoc(self, client):
        assert "swagger-ui" in client.get("/docs").text
        assert client.get("/redoc").status_code == 200

    def test_describes_the_service(self, client):
        info = client.get("/openapi.json").json()["info"]

        assert info["title"] == "API for Recommendation Service"
        assert info["version"] == "1.0.0"

    def test_documents_every_endpoint_with_its_responses(self, client):
        paths = client.get("/openapi.json").json()["paths"]

        user = paths["/recommendations/users/{user_id}"]["get"]
        assert user["summary"] == "Recommend products to a customer"
        assert user["responses"]["200"]["content"]["application/json"]["schema"]["$ref"].endswith(
            "UserRecommendationResponse"
        )
        assert set(paths["/recommendations/{product_id}"]["get"]["responses"]) == {"200", "400", "401", "403", "404", "422"}
        assert "404" in paths["/recommendations/mba/status"]["get"]["responses"]
        assert paths["/"]["get"]["tags"] == ["Health"]

    def test_documents_the_parameters(self, client):
        parameters = client.get("/openapi.json").json()["paths"]["/recommendations/users/{user_id}"]["get"]["parameters"]
        by_name = {parameter["name"]: parameter for parameter in parameters}

        assert by_name["user_id"]["schema"]["format"] == "uuid"
        assert by_name["user_id"]["description"] == "The customer, as in `sales_order.created_by`"
        limit = by_name["limit"]["schema"]
        assert (limit["minimum"], limit["maximum"], limit["default"]) == (1, 50, 10)

    def test_gives_an_example_that_matches_the_schema(self, client):
        from schemas.recommendation import MbaStatusResponse, ProductRecommendationResponse, UserRecommendationResponse

        for model in (UserRecommendationResponse, ProductRecommendationResponse, MbaStatusResponse):
            for example in model.model_config["json_schema_extra"]["examples"]:
                model.model_validate(example)
