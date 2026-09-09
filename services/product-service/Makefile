STEP ?= -all

SWAG_CMD = swag init -d ./,./ -o docs --ot go,json,yaml --pd true
TEST_PKGS = $$(go list -f '{{if or .TestGoFiles .XTestGoFiles}}{{.ImportPath}}{{end}}' ./...)

run:
	go run main.go

test:
	go test -race -coverprofile cover.out $(TEST_PKGS)
	go tool cover -html=cover.out

swag:
	@echo "🚀 Generating Swagger Docs..."
	$(SWAG_CMD)