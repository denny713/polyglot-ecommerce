package model

type Category struct {
	Id          int64  `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	Name        string `gorm:"column:name;type:varchar(50);not null" json:"name"`
	Description string `gorm:"column:description;type:text" json:"description"`
	Base

	// Relation
	Products *[]Product `gorm:"foreignKey:CategoryId;references:Id" json:"products"`
}

func (Category) TableName() string {
	return "category"
}
