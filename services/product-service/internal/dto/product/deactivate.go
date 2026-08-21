package product

type (
	ProductDeactivateReq struct {
		Id int64
	}

	ProductDeactivateRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)
