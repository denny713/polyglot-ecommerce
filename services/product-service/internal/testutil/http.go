package testutil

import (
	"bytes"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"net/textproto"
	"strings"
	"testing"

	"github.com/labstack/echo/v5"
)

// NewContext builds an echo context around a request, with the path parameters
// the router would have filled in already set.
func NewContext(req *http.Request, params map[string]string) (*echo.Context, *httptest.ResponseRecorder) {
	recorder := httptest.NewRecorder()
	c := echo.New().NewContext(req, recorder)

	values := make(echo.PathValues, 0, len(params))
	for name, value := range params {
		values = append(values, echo.PathValue{Name: name, Value: value})
	}

	c.SetPathValues(values)

	return c, recorder
}

// JSONRequest builds a request carrying a json body.
func JSONRequest(method, target, body string) *http.Request {
	req := httptest.NewRequest(method, target, strings.NewReader(body))
	req.Header.Set(echo.HeaderContentType, echo.MIMEApplicationJSON)

	return req
}

// FilePart is an image part of a multipart request.
type FilePart struct {
	Field       string
	Filename    string
	ContentType string
	Content     []byte
	// Size overrides the reported size of the part, so a test can claim an
	// upload is larger than it really is.
	Size int64
}

// MultipartRequest builds a multipart/form-data request holding the given form
// fields and, when file is not nil, one file part.
func MultipartRequest(t *testing.T, method, target string, fields map[string]string, file *FilePart) *http.Request {
	t.Helper()

	var body bytes.Buffer
	writer := multipart.NewWriter(&body)

	for name, value := range fields {
		if err := writer.WriteField(name, value); err != nil {
			t.Fatalf("write the %s field: %v", name, err)
		}
	}

	if file != nil {
		header := make(textproto.MIMEHeader)
		header.Set("Content-Disposition", `form-data; name="`+file.Field+`"; filename="`+file.Filename+`"`)
		if file.ContentType != "" {
			header.Set("Content-Type", file.ContentType)
		}

		part, err := writer.CreatePart(header)
		if err != nil {
			t.Fatalf("create the file part: %v", err)
		}

		if _, err = part.Write(file.Content); err != nil {
			t.Fatalf("write the file part: %v", err)
		}
	}

	if err := writer.Close(); err != nil {
		t.Fatalf("close the multipart writer: %v", err)
	}

	req := httptest.NewRequest(method, target, &body)
	req.Header.Set(echo.HeaderContentType, writer.FormDataContentType())

	return req
}

// FileHeader parses a multipart request and returns the header of one of its
// file parts, so the size and the name a validator sees are the real ones.
func FileHeader(t *testing.T, req *http.Request, field string) *multipart.FileHeader {
	t.Helper()

	if err := req.ParseMultipartForm(32 << 20); err != nil {
		t.Fatalf("parse the multipart form: %v", err)
	}

	headers := req.MultipartForm.File[field]
	if len(headers) == 0 {
		t.Fatalf("the request carries no %s part", field)
	}

	return headers[0]
}
