package docs

import _ "embed"

// Swagger holds the generated OpenAPI specification. It is embedded so the spec
// is served regardless of the working directory the binary is started from.
//
// Regenerate with: swag init -g main.go -o docs --outputTypes json,yaml
//
//go:embed swagger.json
var Swagger []byte
