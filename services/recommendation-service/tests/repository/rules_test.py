import json

from repository import rules as rule_store
from service.fpgrowth import Rule


def rule(antecedent, consequent, confidence, lift=2.0, support=0.2):
    return Rule(frozenset(antecedent), frozenset(consequent), support, confidence, lift)


class TestSaveRules:

    async def test_indexes_each_rule_under_every_product_of_its_antecedent(self, cache):
        await rule_store.save_rules(cache, [rule({3, 5}, {7}, 0.6)], 50, {"rules": 1})

        expected = [{"antecedent": [3, 5], "consequent": [7], "support": 0.2, "confidence": 0.6, "lift": 2.0}]
        assert json.loads(await cache.get("mba:rules:3")) == expected
        assert json.loads(await cache.get("mba:rules:5")) == expected
        assert await cache.get("mba:rules:7") is None
        assert await cache.smembers("mba:products") == {"3", "5"}
        assert json.loads(await cache.get("mba:meta")) == {"rules": 1}

    async def test_keeps_the_most_confident_rules_per_product(self, cache):
        rules = [rule({1}, {2}, 0.3), rule({1}, {3}, 0.9), rule({1}, {4}, 0.6), rule({1}, {5}, 0.6, lift=3.0)]

        await rule_store.save_rules(cache, rules, 3, {})

        stored = json.loads(await cache.get("mba:rules:1"))
        assert [item["consequent"] for item in stored] == [[3], [5], [4]]

    async def test_rounds_the_measures(self, cache):
        await rule_store.save_rules(cache, [rule({1}, {2}, 2 / 3, lift=4 / 3, support=1 / 3)], 50, {})

        stored = json.loads(await cache.get("mba:rules:1"))[0]
        assert stored["confidence"] == 0.666667
        assert stored["lift"] == 1.333333
        assert stored["support"] == 0.333333

    async def test_removes_the_rules_of_products_no_longer_in_any_rule(self, cache):
        await rule_store.save_rules(cache, [rule({1}, {2}, 0.5), rule({3}, {4}, 0.5)], 50, {})

        await rule_store.save_rules(cache, [rule({3}, {4}, 0.8)], 50, {})

        assert await cache.get("mba:rules:1") is None
        assert json.loads(await cache.get("mba:rules:3"))[0]["confidence"] == 0.8
        assert await cache.smembers("mba:products") == {"3"}

    async def test_clears_everything_when_no_rule_is_left(self, cache):
        await rule_store.save_rules(cache, [rule({1}, {2}, 0.5)], 50, {})

        await rule_store.save_rules(cache, [], 50, {"rules": 0})

        assert await cache.get("mba:rules:1") is None
        assert await cache.exists("mba:products") == 0
        assert json.loads(await cache.get("mba:meta")) == {"rules": 0}


class TestGetRules:

    async def test_answers_the_rules_of_every_product_once(self, cache):
        await rule_store.save_rules(cache, [rule({3, 5}, {7}, 0.6), rule({3}, {5}, 1.0)], 50, {})

        rules = await rule_store.get_rules(cache, [3, 5, 99])

        assert sorted((item["antecedent"], item["consequent"]) for item in rules) == [([3], [5]), ([3, 5], [7])]

    async def test_answers_nothing_for_no_products(self, cache):
        assert await rule_store.get_rules(cache, []) == []


class TestGetMeta:

    async def test_answers_how_the_rules_were_built(self, cache):
        await rule_store.save_rules(cache, [], 50, {"orders": 8})

        assert await rule_store.get_meta(cache) == {"orders": 8}

    async def test_answers_none_before_the_first_build(self, cache):
        assert await rule_store.get_meta(cache) is None
