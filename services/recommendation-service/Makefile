VENV ?= venv
PORT ?= 7170

PYTHON = $(VENV)/bin/python
PIP = $(VENV)/bin/pip

.PHONY: help install install-dev run dev test test-coverage mba clean

help:
	@echo "make install        Create the virtualenv and install the runtime dependencies"
	@echo "make install-dev    Same, plus the test dependencies"
	@echo "make run            Run the service on the port set in .env"
	@echo "make dev            Run the service with auto reload on PORT (default $(PORT))"
	@echo "make test           Run the unit tests"
	@echo "make test-coverage  Run the unit tests with coverage (fails under 90%)"
	@echo "make mba            Rebuild the Market Basket Analysis rules once"
	@echo "make clean          Remove caches and coverage reports"

$(PYTHON):
	python3 -m venv $(VENV)

install: $(PYTHON)
	$(PIP) install -r requirements.txt

install-dev: $(PYTHON)
	$(PIP) install -r requirements-dev.txt

run:
	$(PYTHON) main.py

dev:
	$(VENV)/bin/uvicorn main:app --reload --host 0.0.0.0 --port $(PORT)

test:
	$(PYTHON) -m pytest

test-coverage:
	$(PYTHON) -m pytest --cov --cov-report=term-missing --cov-report=html
	@echo "HTML report: htmlcov/index.html"

mba:
	$(PYTHON) -m service.mba

clean:
	rm -rf .pytest_cache htmlcov .coverage coverage.xml
	find . -name __pycache__ -not -path "./$(VENV)/*" -exec rm -rf {} +
