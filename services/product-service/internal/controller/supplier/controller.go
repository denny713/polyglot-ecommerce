package supplier

import (
	svc "product-service/internal/service/supplier"
)

// Controller holds the handlers of the supplier endpoints, the service they
// delegate to is injected so it can be replaced in a test.
type Controller struct {
	service svc.Service
}

// NewController builds the supplier controller.
func NewController(service svc.Service) Controller {
	return Controller{service: service}
}
