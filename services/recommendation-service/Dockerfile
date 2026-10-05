FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# Dependencies first so this layer is only rebuilt when requirements.txt changes.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN useradd --uid 10001 --no-create-home app
USER app
EXPOSE 7170

# No .env is baked into the image: configuration comes from the environment,
# which is what compose injects.
CMD ["python", "main.py"]
