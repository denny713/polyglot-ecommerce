package category

import (
	"context"
	"errors"
	"testing"
	"time"

	"product-service/internal/constant"
	"product-service/internal/dto/base"
	dto "product-service/internal/dto/category"
	"product-service/internal/exception"
	"product-service/internal/mocks"
	"product-service/internal/model"

	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

var errDatabase = errors.New("connection reset by peer")

// existingActor is the audit trail a row loaded from the database already
// carries, the writes under test have to leave it on CreatedBy.
var existingActor = uuid.MustParse("2b1f8f4a-0000-4000-8000-00000000002a")

func newService(t *testing.T) (Service, *mocks.CategoryRepository, *mocks.Database) {
	t.Helper()

	repository := &mocks.CategoryRepository{}
	database := &mocks.Database{}

	return NewService(database, repository), repository, database
}

func existingCategory() model.Category {
	created := time.Date(2024, time.January, 2, 3, 4, 5, 0, time.UTC)

	return model.Category{
		Id:          7,
		Name:        "Elektronik",
		Description: "Perangkat elektronik",
		Base: model.Base{
			IsActive:  true,
			CreatedBy: existingActor,
			UpdatedBy: existingActor,
			CreatedAt: created,
			UpdatedAt: created,
		},
	}
}

func TestCreate(t *testing.T) {
	service, repository, _ := newService(t)
	repository.CreateFn = func(_ *gorm.DB, category model.Category) (model.Category, error) {
		category.Id = 11

		return category, nil
	}

	got, err := service.Create(context.Background(), dto.CategoryCreateReq{
		Name:        "Elektronik",
		Description: "Perangkat",
	})

	require.NoError(t, err)
	require.Equal(t, int64(11), got.Id)
	require.Equal(t, "Elektronik", got.Name)
	require.True(t, got.IsActive)

	// The row handed to the repository is the request mapped onto the model.
	require.Len(t, repository.CreateCalls, 1)
	require.Equal(t, "Elektronik", repository.CreateCalls[0].Name)
	require.True(t, repository.CreateCalls[0].IsActive)
}

func TestCreateFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.CreateFn = func(*gorm.DB, model.Category) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	got, err := service.Create(context.Background(), dto.CategoryCreateReq{Name: "Elektronik"})

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, dto.CategoryCreateRes{}, got)
}

func TestDetail(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return existingCategory(), nil
	}

	got, err := service.Detail(context.Background(), dto.CategoryDetailReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Elektronik", got.Name)

	// The lookup is always made on the primary key.
	require.Equal(t, []mocks.DetailCall{{Param: "id", Value: int64(7)}}, repository.DetailCalls)
}

func TestDetailNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, gorm.ErrRecordNotFound
	}

	got, err := service.Detail(context.Background(), dto.CategoryDetailReq{Id: 7})

	// The gorm error is translated into the exception the controller reports.
	require.ErrorIs(t, err, exception.ErrNotFound)
	require.Equal(t, dto.CategoryDetailRes{}, got)
}

func TestDetailFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	_, err := service.Detail(context.Background(), dto.CategoryDetailReq{Id: 7})

	// Anything that is not a missing row is passed through untouched.
	require.ErrorIs(t, err, errDatabase)
}

func TestSearch(t *testing.T) {
	service, repository, _ := newService(t)
	repository.SearchFn = func(_ *gorm.DB, _ dto.CategorySearchFilter) ([]model.Category, error) {
		return []model.Category{existingCategory()}, nil
	}

	got, err := service.Search(context.Background(), dto.CategorySearchReq{
		Name:        "  elektronik  ",
		Description: "  perangkat  ",
		Paging:      base.Paging{SortBy: "NAME", SortOrder: "ASC", PageSize: 500},
	})

	require.NoError(t, err)
	require.Len(t, got.Data, 1)
	require.Equal(t, int64(7), got.Data[0].Id)

	// The filters reach the repository trimmed, and the paging defaults are
	// already filled in.
	require.Len(t, repository.SearchCalls, 1)
	require.Equal(t, dto.CategorySearchFilter{
		Name:        "elektronik",
		Description: "perangkat",
		Paging: base.Paging{
			SortBy:    "name",
			SortOrder: constant.SortOrderAsc,
			Page:      constant.DefaultPage,
			PageSize:  constant.MaxPageSize,
		},
	}, repository.SearchCalls[0])
}

func TestSearchFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.SearchFn = func(*gorm.DB, dto.CategorySearchFilter) ([]model.Category, error) {
		return nil, errDatabase
	}

	got, err := service.Search(context.Background(), dto.CategorySearchReq{})

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, dto.CategorySearchRes{}, got)
}

func TestUpdate(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return existingCategory(), nil
	}

	got, err := service.Update(context.Background(), dto.CategoryUpdateReq{
		Id:          7,
		Name:        "Elektronik Baru",
		Description: "Deskripsi baru",
	})

	require.NoError(t, err)
	require.Equal(t, "Elektronik Baru", got.Name)

	// The update is applied on top of the row that was read, so the flags and the
	// creation trail of the existing category survive.
	require.Len(t, repository.UpdateCalls, 1)
	written := repository.UpdateCalls[0]
	require.Equal(t, int64(7), written.Id)
	require.Equal(t, "Deskripsi baru", written.Description)
	require.True(t, written.IsActive)
	require.Equal(t, existingActor, written.CreatedBy)
	require.NotEqual(t, uuid.Nil, written.UpdatedBy)
}

func TestUpdateNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, gorm.ErrRecordNotFound
	}

	_, err := service.Update(context.Background(), dto.CategoryUpdateReq{Id: 7, Name: "Elektronik"})

	require.ErrorIs(t, err, exception.ErrNotFound)
	require.Empty(t, repository.UpdateCalls)
}

func TestUpdateFailsToRead(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	_, err := service.Update(context.Background(), dto.CategoryUpdateReq{Id: 7, Name: "Elektronik"})

	require.ErrorIs(t, err, errDatabase)
	require.Empty(t, repository.UpdateCalls)
}

func TestUpdateFailsToWrite(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return existingCategory(), nil
	}
	repository.UpdateFn = func(*gorm.DB, model.Category) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	got, err := service.Update(context.Background(), dto.CategoryUpdateReq{Id: 7, Name: "Elektronik"})

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, dto.CategoryUpdateRes{}, got)
}

func TestActivate(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		category := existingCategory()
		category.IsActive = false

		return category, nil
	}

	got, err := service.Activate(context.Background(), dto.CategoryActivateReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, constant.Active, got.Status)
	require.Equal(t, int64(7), got.Id)

	require.Len(t, repository.UpdateCalls, 1)
	require.True(t, repository.UpdateCalls[0].IsActive)
	require.NotEqual(t, uuid.Nil, repository.UpdateCalls[0].UpdatedBy)
}

func TestActivateAnAlreadyActiveCategory(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return existingCategory(), nil
	}

	_, err := service.Activate(context.Background(), dto.CategoryActivateReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrAlreadyActive)
	require.Empty(t, repository.UpdateCalls)
}

func TestActivateNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, gorm.ErrRecordNotFound
	}

	_, err := service.Activate(context.Background(), dto.CategoryActivateReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrNotFound)
}

func TestActivateFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	_, err := service.Activate(context.Background(), dto.CategoryActivateReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)

	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		category := existingCategory()
		category.IsActive = false

		return category, nil
	}
	repository.UpdateFn = func(*gorm.DB, model.Category) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	_, err = service.Activate(context.Background(), dto.CategoryActivateReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)
}

func TestDeactivate(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return existingCategory(), nil
	}

	got, err := service.Deactivate(context.Background(), dto.CategoryDeactivateReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, constant.Inactive, got.Status)

	require.Len(t, repository.UpdateCalls, 1)
	require.False(t, repository.UpdateCalls[0].IsActive)
}

func TestDeactivateAnAlreadyInactiveCategory(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		category := existingCategory()
		category.IsActive = false

		return category, nil
	}

	_, err := service.Deactivate(context.Background(), dto.CategoryDeactivateReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrAlreadyInactive)
	require.Empty(t, repository.UpdateCalls)
}

func TestDeactivateNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, gorm.ErrRecordNotFound
	}

	_, err := service.Deactivate(context.Background(), dto.CategoryDeactivateReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrNotFound)
}

func TestDeactivateFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	_, err := service.Deactivate(context.Background(), dto.CategoryDeactivateReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)

	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return existingCategory(), nil
	}
	repository.UpdateFn = func(*gorm.DB, model.Category) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	_, err = service.Deactivate(context.Background(), dto.CategoryDeactivateReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)
}

func TestDelete(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return existingCategory(), nil
	}

	got, err := service.Delete(context.Background(), dto.CategoryDeleteReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, constant.Delete, got.Status)

	// The row is flagged rather than removed, and it keeps the active flag it had.
	require.Len(t, repository.UpdateCalls, 1)
	require.True(t, repository.UpdateCalls[0].IsDeleted)
	require.True(t, repository.UpdateCalls[0].IsActive)
}

func TestDeleteNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, gorm.ErrRecordNotFound
	}

	_, err := service.Delete(context.Background(), dto.CategoryDeleteReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrNotFound)
	require.Empty(t, repository.UpdateCalls)
}

func TestDeleteFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	_, err := service.Delete(context.Background(), dto.CategoryDeleteReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)

	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return existingCategory(), nil
	}
	repository.UpdateFn = func(*gorm.DB, model.Category) (model.Category, error) {
		return model.Category{}, errDatabase
	}

	_, err = service.Delete(context.Background(), dto.CategoryDeleteReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)
}

func TestTheOrmIsTakenFromTheRequestContext(t *testing.T) {
	service, repository, database := newService(t)

	type key struct{}
	ctx := context.WithValue(context.Background(), key{}, "request")

	var seen context.Context
	database.OrmFn = func(ctx context.Context) *gorm.DB {
		seen = ctx

		return nil
	}
	repository.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return existingCategory(), nil
	}

	_, err := service.Detail(ctx, dto.CategoryDetailReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, "request", seen.Value(key{}))
}
