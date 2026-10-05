from types import SimpleNamespace


def row(**fields):
    """One result row, read by attribute like a SQLAlchemy Row."""
    return SimpleNamespace(**fields)


class FakeSession:
    """
    Stands in for an AsyncSession: answers each statement with the rows registered
    for it, and records every call so a test can check the parameters sent. The rows
    may be a function of the parameters, to act on them the way the SQL would.
    """

    def __init__(self):
        self.results = {}
        self.calls = []

    def answer(self, statement, rows):
        self.results[statement] = rows

    async def execute(self, statement, params=None):
        self.calls.append((statement, params))
        rows = self.results.get(statement, [])
        return iter(rows(params) if callable(rows) else rows)

    def params_of(self, statement):
        return [params for called, params in self.calls if called is statement]


def products(*catalog):
    """
    Answers PRODUCT_DETAILS from a catalog of (id, name) of the products still sold,
    for the ids asked only.
    """
    by_id = {product_id: name for product_id, name in catalog}

    def answer(params):
        return [
            row(id=product_id, name=by_id[product_id], sell_price=10, image_url=None, category_id=1)
            for product_id in params["ids"]
            if product_id in by_id
        ]

    return answer


def ranked(*scores):
    """Answers a ranking query from (id, score), leaving out the excluded ids and keeping to the limit."""

    def answer(params):
        kept = [(product_id, score) for product_id, score in scores if product_id not in params["exclude"]]
        return [row(product_id=product_id, score=score) for product_id, score in kept[: params["limit"]]]

    return answer
