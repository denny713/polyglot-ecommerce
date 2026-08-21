package model

type Supplier struct {
	Id            int64  `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	Name          string `gorm:"column:name;type:varchar(50);not null" json:"name"`
	Phone         string `gorm:"column:phone;type:varchar(50)" json:"phone"`
	Email         string `gorm:"column:email;type:varchar(500)" json:"email"`
	ContactPerson string `gorm:"column:contact_person;type:varchar(50)" json:"contact_person"`
	Address       string `gorm:"column:address;type:text" json:"address"`
	Province      string `gorm:"column:province;type:varchar(100)" json:"province"`
	City          string `gorm:"column:city;type:varchar(100)" json:"city"`
	District      string `gorm:"column:district;type:varchar(100)" json:"district"`
	Subdistrict   string `gorm:"column:subdistrict;type:varchar(100)" json:"subdistrict"`
	PostalCode    string `gorm:"column:postal_code;type:varchar(7)" json:"postal_code"`
	Note          string `gorm:"column:note;type:text" json:"note"`
	Base

	// Relation
	Products *[]Product `gorm:"foreignKey:SupplierId;references:Id" json:"products"`
}

func (Supplier) TableName() string {
	return "supplier"
}
