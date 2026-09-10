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

// Touch stamps the audit fields of a record that was loaded from the database
// and is about to be written back. Unlike PreUpdate it keeps the status flags
// and the creation trail already held by the record, so it is the one to use
// when only a flag such as IsActive or IsDeleted changes.
func (b Base) Touch() Base {
	b.UpdatedBy = 1
	b.UpdatedAt = time.Now()

	return b
}
