import random
from itertools import combinations

import pytest

from service.fpgrowth import Rule, association_rules, frequent_itemsets

# Laptop=1, Mouse=2, Keyboard=3, Celana=5, Topi=7
BASKETS = [
    frozenset({1}),
    frozenset({1, 2}),
    frozenset({1, 2, 3, 5}),
    frozenset({3, 5, 7}),
    frozenset({3, 5, 7}),
]


def brute_force_itemsets(baskets, min_count, max_len):
    items = sorted(set().union(*baskets)) if baskets else []
    found = {}
    for size in range(1, max_len + 1):
        for candidate in map(frozenset, combinations(items, size)):
            count = sum(1 for basket in baskets if candidate <= basket)
            if count >= min_count:
                found[candidate] = count
    return found


class TestFrequentItemsets:

    def test_counts_every_itemset_found_in_enough_baskets(self):
        itemsets = frequent_itemsets(BASKETS, min_count=2, max_len=3)

        assert itemsets == {
            frozenset({1}): 3,
            frozenset({2}): 2,
            frozenset({3}): 3,
            frozenset({5}): 3,
            frozenset({7}): 2,
            frozenset({1, 2}): 2,
            frozenset({3, 5}): 3,
            frozenset({3, 7}): 2,
            frozenset({5, 7}): 2,
            frozenset({3, 5, 7}): 2,
        }

    def test_stops_growing_an_itemset_at_max_len(self):
        itemsets = frequent_itemsets(BASKETS, min_count=2, max_len=2)

        assert max(map(len, itemsets)) == 2
        assert frozenset({3, 5, 7}) not in itemsets

    def test_leaves_out_items_below_min_count(self):
        itemsets = frequent_itemsets(BASKETS, min_count=3, max_len=3)

        assert set(itemsets) == {frozenset({1}), frozenset({3}), frozenset({5}), frozenset({3, 5})}

    def test_finds_nothing_in_no_baskets(self):
        assert frequent_itemsets([], min_count=1, max_len=3) == {}

    def test_finds_nothing_when_no_item_is_frequent(self):
        assert frequent_itemsets(BASKETS, min_count=10, max_len=3) == {}

    @pytest.mark.parametrize("seed", range(100))
    def test_agrees_with_brute_force(self, seed):
        rng = random.Random(seed)
        n_items = rng.randint(1, 10)
        baskets = [
            frozenset(rng.sample(range(n_items), rng.randint(1, min(n_items, 5))))
            for _ in range(rng.randint(1, 40))
        ]
        min_count = rng.randint(1, 4)
        max_len = rng.randint(1, 4)

        assert frequent_itemsets(baskets, min_count, max_len) == brute_force_itemsets(baskets, min_count, max_len)


class TestAssociationRules:

    def rules_by_sides(self, rules):
        return {(rule.antecedent, rule.consequent): rule for rule in rules}

    def test_measures_support_confidence_and_lift(self):
        itemsets = frequent_itemsets(BASKETS, min_count=2, max_len=3)
        rules = self.rules_by_sides(association_rules(itemsets, len(BASKETS), 0.0, 0.0))

        # Laptop sits in 3 of 5 orders, 2 of them with Mouse; Mouse sits in 2 of 5
        laptop_to_mouse = rules[(frozenset({1}), frozenset({2}))]
        assert laptop_to_mouse.support == pytest.approx(2 / 5)
        assert laptop_to_mouse.confidence == pytest.approx(2 / 3)
        assert laptop_to_mouse.lift == pytest.approx((2 / 3) / (2 / 5))

        # A rule may have more than one product on either side
        pair_to_topi = rules[(frozenset({3, 5}), frozenset({7}))]
        assert pair_to_topi.confidence == pytest.approx(2 / 3)
        assert (frozenset({3}), frozenset({5, 7})) in rules

    def test_leaves_out_rules_below_min_confidence(self):
        itemsets = frequent_itemsets(BASKETS, min_count=2, max_len=3)
        rules = association_rules(itemsets, len(BASKETS), min_confidence=0.9, min_lift=0.0)

        assert rules
        assert all(rule.confidence >= 0.9 for rule in rules)

    def test_keeps_only_rules_with_a_lift_above_min_lift(self):
        # Every basket holds both products, so they are independent: lift is exactly 1
        baskets = [frozenset({1, 2})] * 4
        itemsets = frequent_itemsets(baskets, min_count=1, max_len=2)

        assert association_rules(itemsets, len(baskets), 0.0, min_lift=1.0) == []
        assert len(association_rules(itemsets, len(baskets), 0.0, min_lift=0.5)) == 2

    def test_gives_no_rule_without_an_itemset_of_two(self):
        itemsets = {frozenset({1}): 3, frozenset({2}): 2}

        assert association_rules(itemsets, 5, 0.0, 0.0) == []

    def test_rule_is_a_value(self):
        rule = Rule(frozenset({1}), frozenset({2}), 0.4, 0.5, 1.2)

        assert rule == Rule(frozenset({1}), frozenset({2}), 0.4, 0.5, 1.2)
