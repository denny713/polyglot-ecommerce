package product

import (
	svc "product-service/internal/service/product"
)

// Controller holds the handlers of the product endpoints, the service they
// delegate to is injected so it can be replaced in a test.
type Controller struct {
	service svc.Service
}

// NewController builds the product controller.
func NewController(service svc.Service) Controller {
	return Controller{service: service}
}
