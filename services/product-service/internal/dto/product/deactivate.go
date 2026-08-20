package product

type (
	ProductDeactivateReq struct {
		ID int64
	}

	ProductDeactivateRes struct {
		ID     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)
