from uuid import UUID

from repository import history, rules as rule_store
from service import recommendation
from service.fpgrowth import Rule
from tests.helpers import products, ranked, row

USER = UUID("aaaaaaaa-0000-0000-0000-000000000000")

# Laptop=1, Mouse=2, Keyboard=3, Kaos=4, Celana=5, Monitor=6 (no longer sold), Topi=7
CATALOG = products((1, "Laptop"), (2, "Mouse"), (3, "Keyboard"), (4, "Kaos"), (5, "Celana"), (7, "Topi"))


def rule(antecedent, consequent, confidence, lift=2.0):
    return Rule(frozenset(antecedent), frozenset(consequent), 0.25, confidence, lift)


def reasons(items):
    return [(item["product_id"], item["reason"]) for item in items]


class TestRecommendForUser:

    async def test_seeds_from_the_orders_and_the_cart(self, db, cache):
        db.answer(history.PURCHASED_PRODUCTS, [row(product_id=1, quantity=1)])
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        await cache.set(f"cart:{USER}:4", 2)

        result = await recommendation.recommend_for_user(db, cache, USER, 10)

        assert result["user_id"] == str(USER)
        assert result["history"] == {"purchased_product_ids": [1], "cart_product_ids": [4]}
        assert db.params_of(history.BOUGHT_TOGETHER)[0]["seed"] == [1, 4]
        assert db.params_of(history.BOUGHT_TOGETHER)[0]["user_id"] == USER

    async def test_fills_the_list_in_order_of_relevance(self, db, cache):
        db.answer(history.PURCHASED_PRODUCTS, [row(product_id=1, quantity=1)])
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        db.answer(history.BOUGHT_TOGETHER, ranked((2, 5), (3, 2)))
        db.answer(history.SAME_CATEGORY, ranked((3, 9), (5, 1)))
        db.answer(history.POPULAR, ranked((2, 9), (7, 4), (4, 1)))
        await rule_store.save_rules(cache, [rule({1}, {2}, 0.4)], 50, {})

        result = await recommendation.recommend_for_user(db, cache, USER, 5)

        # Each product once, from the most relevant step that found it
        assert reasons(result["recommended_items"]) == [
            (2, "frequently_bought_together"),
            (3, "bought_together"),
            (5, "same_category"),
            (7, "popular"),
            (4, "popular"),
        ]

    async def test_attaches_the_rule_and_the_details(self, db, cache):
        db.answer(history.PURCHASED_PRODUCTS, [row(product_id=1, quantity=1)])
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        await rule_store.save_rules(cache, [rule({1}, {2}, 0.4, lift=1.6)], 50, {})

        item = (await recommendation.recommend_for_user(db, cache, USER, 1))["recommended_items"][0]

        assert item == {
            "product_id": 2,
            "score": 0.4,
            "reason": "frequently_bought_together",
            "name": "Mouse",
            "sell_price": 10.0,
            "image_url": None,
            "category_id": 1,
            "rule": {"antecedent": [1], "consequent": [2], "support": 0.25, "confidence": 0.4, "lift": 1.6},
        }

    async def test_never_recommends_the_history_back(self, db, cache):
        db.answer(history.PURCHASED_PRODUCTS, [row(product_id=1, quantity=1)])
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        db.answer(history.POPULAR, ranked((1, 9), (4, 5), (2, 1)))
        await cache.set(f"cart:{USER}:4", 1)
        await rule_store.save_rules(cache, [rule({1}, {4}, 0.9)], 50, {})

        result = await recommendation.recommend_for_user(db, cache, USER, 10)

        assert reasons(result["recommended_items"]) == [(2, "popular")]

    async def test_applies_a_rule_only_when_the_history_holds_its_whole_antecedent(self, db, cache):
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        await cache.set(f"cart:{USER}:3", 1)
        await rule_store.save_rules(cache, [rule({3, 5}, {7}, 0.9), rule({3}, {2}, 0.5)], 50, {})

        only_keyboard = await recommendation.recommend_for_user(db, cache, USER, 10)
        assert reasons(only_keyboard["recommended_items"]) == [(2, "frequently_bought_together")]

        await cache.set(f"cart:{USER}:5", 1)
        keyboard_and_celana = await recommendation.recommend_for_user(db, cache, USER, 10)
        assert reasons(keyboard_and_celana["recommended_items"]) == [
            (7, "frequently_bought_together"),
            (2, "frequently_bought_together"),
        ]

    async def test_scores_a_product_by_its_most_confident_rule(self, db, cache):
        db.answer(history.PURCHASED_PRODUCTS, [row(product_id=1, quantity=1), row(product_id=3, quantity=1)])
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        await rule_store.save_rules(cache, [
            rule({1}, {2}, 0.4),
            rule({3}, {2}, 0.7),
            rule({1, 3}, {2}, 0.7, lift=3.0),
            rule({3}, {5}, 0.7, lift=1.5),
        ], 50, {})

        items = (await recommendation.recommend_for_user(db, cache, USER, 10))["recommended_items"]

        assert [(item["product_id"], item["score"], item["rule"]["antecedent"]) for item in items] == [
            (2, 0.7, [1, 3]),
            (5, 0.7, [3]),
        ]

    async def test_skips_a_rule_leading_to_a_product_no_longer_sold(self, db, cache):
        db.answer(history.PURCHASED_PRODUCTS, [row(product_id=1, quantity=1)])
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        await rule_store.save_rules(cache, [rule({1}, {6}, 0.9), rule({1}, {2}, 0.4)], 50, {})

        items = (await recommendation.recommend_for_user(db, cache, USER, 1))["recommended_items"]

        # Monitor would have taken the only place, and then been dropped
        assert reasons(items) == [(2, "frequently_bought_together")]

    async def test_keeps_to_the_limit(self, db, cache):
        db.answer(history.PURCHASED_PRODUCTS, [row(product_id=1, quantity=1)])
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        await rule_store.save_rules(cache, [rule({1}, {2}, 0.9), rule({1}, {3}, 0.8), rule({1}, {5}, 0.7)], 50, {})

        items = (await recommendation.recommend_for_user(db, cache, USER, 2))["recommended_items"]

        assert reasons(items) == [(2, "frequently_bought_together"), (3, "frequently_bought_together")]
        # The list was full before the other steps ran
        assert db.params_of(history.BOUGHT_TOGETHER) == []
        assert db.params_of(history.POPULAR) == []

    async def test_falls_back_to_best_sellers_for_a_user_without_history(self, db, cache):
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        db.answer(history.POPULAR, ranked((1, 3), (2, 3)))

        result = await recommendation.recommend_for_user(db, cache, USER, 10)

        assert result["history"] == {"purchased_product_ids": [], "cart_product_ids": []}
        assert reasons(result["recommended_items"]) == [(1, "popular"), (2, "popular")]
        assert db.params_of(history.BOUGHT_TOGETHER) == []
        assert db.params_of(history.SAME_CATEGORY) == []

    async def test_drops_a_product_whose_details_are_gone(self, db, cache):
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        # A ranking that still names a product no longer sold
        db.answer(history.POPULAR, ranked((6, 9), (2, 1)))

        items = (await recommendation.recommend_for_user(db, cache, USER, 10))["recommended_items"]

        assert reasons(items) == [(2, "popular")]
        assert "rule" not in items[0]


class TestRecommendForProduct:

    async def test_answers_none_for_a_product_not_sold(self, db, cache):
        db.answer(history.PRODUCT_DETAILS, CATALOG)

        assert await recommendation.recommend_for_product(db, cache, 6, 10) is None

    async def test_uses_only_rules_whose_antecedent_is_the_product_alone(self, db, cache):
        db.answer(history.PRODUCT_DETAILS, CATALOG)
        db.answer(history.BOUGHT_TOGETHER, ranked((5, 3), (2, 1)))
        await rule_store.save_rules(cache, [rule({3}, {5}, 1.0), rule({3, 5}, {7}, 0.9)], 50, {})

        result = await recommendation.recommend_for_product(db, cache, 3, 2)

        assert result["product_id"] == 3
        assert reasons(result["recommended_items"]) == [(5, "frequently_bought_together"), (2, "bought_together")]
        # Nobody is left out of the peers of a product
        assert db.params_of(history.BOUGHT_TOGETHER)[0]["user_id"] is None
        assert db.params_of(history.BOUGHT_TOGETHER)[0]["exclude"] == [3, 5]
