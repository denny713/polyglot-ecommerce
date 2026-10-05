from decimal import Decimal
from uuid import UUID

from repository import history
from tests.helpers import row

USER = UUID("aaaaaaaa-0000-0000-0000-000000000000")


class TestPurchasedProducts:

    async def test_answers_the_quantity_of_every_product_checked_out(self, db):
        db.answer(history.PURCHASED_PRODUCTS, [row(product_id=1, quantity=Decimal(3)), row(product_id=4, quantity=1)])

        assert await history.get_purchased_products(db, USER) == {1: 3, 4: 1}
        assert db.params_of(history.PURCHASED_PRODUCTS) == [{"user_id": USER}]

    async def test_answers_nothing_for_a_user_without_orders(self, db):
        assert await history.get_purchased_products(db, USER) == {}


class TestCartProducts:

    async def test_reads_every_line_of_the_users_cart(self, cache):
        await cache.set(f"cart:{USER}:4", 2)
        await cache.set(f"cart:{USER}:7", 1)
        # Another user's cart is not this one
        await cache.set("cart:bbbbbbbb-0000-0000-0000-000000000000:9", 5)

        assert await history.get_cart_products(cache, USER) == {4: 2, 7: 1}

    async def test_answers_nothing_for_an_empty_cart(self, cache):
        assert await history.get_cart_products(cache, USER) == {}

    async def test_skips_a_key_that_does_not_name_a_product(self, cache):
        await cache.set(f"cart:{USER}:4", 2)
        await cache.set(f"cart:{USER}:not-a-product", 1)

        assert await history.get_cart_products(cache, USER) == {4: 2}

    async def test_skips_a_line_that_expires_between_scan_and_read(self, cache, monkeypatch):
        await cache.set(f"cart:{USER}:4", 2)
        await cache.set(f"cart:{USER}:7", 1)

        async def mget_after_expiry(keys):
            return [None if key.endswith(":7") else "2" for key in keys]

        monkeypatch.setattr(cache, "mget", mget_after_expiry)

        assert await history.get_cart_products(cache, USER) == {4: 2}

    async def test_follows_the_configured_prefix(self, cache, monkeypatch):
        monkeypatch.setattr(history.settings, "CART_KEY_PREFIX", "basket")
        await cache.set(f"basket:{USER}:4", 3)
        await cache.set(f"cart:{USER}:7", 1)

        assert await history.get_cart_products(cache, USER) == {4: 3}


class TestRankedProducts:

    async def test_bought_together_sends_the_seed_and_answers_scores(self, db):
        db.answer(history.BOUGHT_TOGETHER, [row(product_id=2, score=2), row(product_id=3, score=1)])

        assert await history.get_bought_together(db, USER, [1], [1, 9], 5) == [(2, 2), (3, 1)]
        assert db.params_of(history.BOUGHT_TOGETHER) == [
            {"user_id": USER, "seed": [1], "exclude": [1, 9], "limit": 5}
        ]

    async def test_same_category_sends_the_seed_and_answers_scores(self, db):
        db.answer(history.SAME_CATEGORY, [row(product_id=7, score=Decimal(0))])

        assert await history.get_same_category(db, [4], [4], 3) == [(7, 0)]
        assert db.params_of(history.SAME_CATEGORY) == [{"seed": [4], "exclude": [4], "limit": 3}]

    async def test_popular_answers_scores(self, db):
        db.answer(history.POPULAR, [row(product_id=1, score=3)])

        assert await history.get_popular(db, [], 10) == [(1, 3)]
        assert db.params_of(history.POPULAR) == [{"exclude": [], "limit": 10}]


class TestOrderBaskets:

    async def test_answers_one_basket_per_order(self, db):
        db.answer(history.ORDER_BASKETS, [row(product_ids=[1, 2]), row(product_ids=[3])])

        assert await history.get_order_baskets(db) == [frozenset({1, 2}), frozenset({3})]


class TestProductDetails:

    async def test_answers_the_details_by_id(self, db):
        db.answer(history.PRODUCT_DETAILS, [
            row(id=2, name="Mouse", sell_price=Decimal("15.00"), image_url=None, category_id=1),
        ])

        assert await history.get_product_details(db, [2, 6]) == {
            2: {"name": "Mouse", "sell_price": 15.0, "image_url": None, "category_id": 1},
        }
        assert db.params_of(history.PRODUCT_DETAILS) == [{"ids": [2, 6]}]

    async def test_does_not_query_for_no_ids(self, db):
        assert await history.get_product_details(db, []) == {}
        assert db.calls == []
