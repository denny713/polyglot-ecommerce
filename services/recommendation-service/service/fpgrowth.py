"""
Market Basket Analysis with FP-Growth: frequent itemsets over the order baskets,
then the association rules they give with support, confidence and lift.
"""
from collections import defaultdict
from dataclasses import dataclass
from itertools import combinations
from typing import Iterable


@dataclass(frozen=True)
class Rule:
    antecedent: frozenset[int]
    consequent: frozenset[int]
    support: float
    confidence: float
    lift: float


class _Node:
    __slots__ = ("item", "count", "parent", "children")

    def __init__(self, item, parent):
        self.item = item
        self.count = 0
        self.parent = parent
        self.children = {}


def _build_tree(transactions: Iterable[tuple[Iterable[int], int]], min_count: int):
    """
    Builds the FP-tree of weighted transactions, keeping the frequent items only.
    Answers the header table (item -> nodes holding it) and each item's count.
    """
    transactions = list(transactions)

    counts = defaultdict(int)
    for items, weight in transactions:
        for item in items:
            counts[item] += weight
    counts = {item: count for item, count in counts.items() if count >= min_count}

    root = _Node(None, None)
    header = defaultdict(list)
    for items, weight in transactions:
        # Most frequent first, so transactions share as much of the tree as they can
        ordered = sorted((item for item in items if item in counts), key=lambda item: (-counts[item], item))
        node = root
        for item in ordered:
            child = node.children.get(item)
            if child is None:
                child = _Node(item, node)
                node.children[item] = child
                header[item].append(child)
            child.count += weight
            node = child

    return header, counts


def _mine(header, counts, suffix: frozenset[int], min_count: int, max_len: int, found: dict):
    # Least frequent first, as FP-Growth grows each suffix from the bottom of the tree
    for item in sorted(counts, key=lambda item: (counts[item], item)):
        itemset = suffix | {item}
        found[itemset] = counts[item]
        if len(itemset) >= max_len:
            continue

        # The conditional pattern base: every path leading to this item
        base = []
        for node in header[item]:
            path = []
            parent = node.parent
            while parent.item is not None:
                path.append(parent.item)
                parent = parent.parent
            if path:
                base.append((path, node.count))

        sub_header, sub_counts = _build_tree(base, min_count)
        if sub_counts:
            _mine(sub_header, sub_counts, itemset, min_count, max_len, found)


def frequent_itemsets(baskets: list[frozenset[int]], min_count: int, max_len: int) -> dict[frozenset[int], int]:
    """Every itemset of at most max_len items found in at least min_count baskets."""
    header, counts = _build_tree(((basket, 1) for basket in baskets), min_count)
    found = {}
    _mine(header, counts, frozenset(), min_count, max_len, found)
    return found


def association_rules(
    itemsets: dict[frozenset[int], int],
    total: int,
    min_confidence: float,
    min_lift: float,
) -> list[Rule]:
    """
    Splits every frequent itemset into antecedent -> consequent. Every subset of a
    frequent itemset is frequent too, so its count is always in itemsets.
    """
    rules = []
    for itemset, count in itemsets.items():
        if len(itemset) < 2:
            continue
        for size in range(1, len(itemset)):
            for antecedent in map(frozenset, combinations(itemset, size)):
                consequent = itemset - antecedent
                confidence = count / itemsets[antecedent]
                lift = confidence / (itemsets[consequent] / total)
                if confidence >= min_confidence and lift > min_lift:
                    rules.append(Rule(antecedent, consequent, count / total, confidence, lift))
    return rules
