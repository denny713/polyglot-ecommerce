package model

import "time"

type Base struct {
	IsActive  bool      `gorm:"column:is_active" json:"is_active"`
	IsDeleted bool      `gorm:"column:is_deleted" json:"is_deleted"`
	CreatedBy int64     `gorm:"column:created_by" json:"created_by"`
	UpdatedBy int64     `gorm:"column:updated_by" json:"updated_by"`
	CreatedAt time.Time `gorm:"column:created_at" json:"created_at"`
	UpdatedAt time.Time `gorm:"column:updated_at" json:"updated_at"`
}

func PrePersist() Base {
	return Base{
		IsActive:  true,
		IsDeleted: false,
		CreatedBy: 1,
		UpdatedBy: 1,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
}

func PreUpdate() Base {
	return Base{
		UpdatedBy: 1,
		UpdatedAt: time.Now(),
	}
}
