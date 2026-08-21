package product

type (
	ProductDeleteReq struct {
		Id int64
	}

	ProductDeleteRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)
