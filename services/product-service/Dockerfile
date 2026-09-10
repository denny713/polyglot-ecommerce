# ---- build stage -------------------------------------------------------------
FROM golang:1.25-alpine AS build

WORKDIR /src

# Dependencies first so this layer is only rebuilt when go.mod/go.sum change.
COPY go.mod go.sum ./
RUN go mod download

COPY . .

# CGO is not needed - pgx and minio-go are pure Go - so the binary comes out
# static and runs on a bare runtime image.
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /out/product-service .

# ---- runtime stage -----------------------------------------------------------
FROM alpine:3.22

# The CA bundle comes from the build stage instead of `apk add`, so this stage
# needs no package repository of its own. It is what verifies MinIO's
# certificate once MINIO_USE_SSL is turned on.
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt

RUN adduser -D -u 10001 app

WORKDIR /app
COPY --from=build /out/product-service /app/product-service

USER app
EXPOSE 7130

# No .env is baked into the image: configuration comes from the environment,
# which is what compose injects.
ENTRYPOINT ["/app/product-service"]
