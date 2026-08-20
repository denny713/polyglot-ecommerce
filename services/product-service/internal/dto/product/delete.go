package product

type (
	ProductDeleteReq struct {
		ID int64
	}

	ProductDeleteRes struct {
		ID     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)
