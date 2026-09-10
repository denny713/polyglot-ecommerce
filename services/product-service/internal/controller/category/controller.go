package category

import (
	svc "product-service/internal/service/category"
)

// Controller holds the handlers of the category endpoints, the service they
// delegate to is injected so it can be replaced in a test.
type Controller struct {
	service svc.Service
}

// NewController builds the category controller.
func NewController(service svc.Service) Controller {
	return Controller{service: service}
}
