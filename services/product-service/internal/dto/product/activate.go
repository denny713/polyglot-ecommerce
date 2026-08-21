package product

type (
	ProductActivateReq struct {
		Id int64
	}

	ProductActivateRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)
