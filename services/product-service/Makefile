STEP ?= -all

SWAG_CMD = swag init -d ./,./ -o docs --ot go,json,yaml --pd true

run:
	go run main.go

test:
	go test -race -coverprofile cover.out ./...
	go tool cover -html=cover.out

swag:
	@echo "🚀 Generating Swagger Docs..."
	$(SWAG_CMD)