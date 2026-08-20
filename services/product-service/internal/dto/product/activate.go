package product

type (
	ProductActivateReq struct {
		ID int64
	}

	ProductActivateRes struct {
		ID     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)
