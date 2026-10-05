package testutil

import (
	"io"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"

	"product-service/internal/configuration"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

// StorageServer is an object storage answering the requests minio-go makes, so
// the storage repository can be exercised over the real client.
type StorageServer struct {
	Server *httptest.Server
	Bucket string

	mu       sync.Mutex
	requests []StorageRequest
	status   int
	missing  bool
}

// StorageRequest is a request the fake storage was asked to serve.
type StorageRequest struct {
	Method string
	Path   string
}

// NewStorageServer starts a fake object storage. The bucket it reports is
// bucketName, and every object request succeeds until Fail is called.
func NewStorageServer(t *testing.T, bucketName string) *StorageServer {
	t.Helper()

	fake := &StorageServer{Bucket: bucketName, status: http.StatusOK}
	fake.Server = httptest.NewServer(http.HandlerFunc(fake.serve))
	t.Cleanup(fake.Server.Close)

	return fake
}

// MissingBucket makes the fake report that its bucket does not exist yet, so the
// caller has to create it.
func (f *StorageServer) MissingBucket() {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.missing = true
}

// Fail makes every following object request answer with the given status.
func (f *StorageServer) Fail(status int) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.status = status
}

// Requests returns the requests the fake served, in order.
func (f *StorageServer) Requests() []StorageRequest {
	f.mu.Lock()
	defer f.mu.Unlock()

	return append([]StorageRequest(nil), f.requests...)
}

// Client builds a minio client and the configuration pointing at the fake.
func (f *StorageServer) Client(t *testing.T) (*minio.Client, configuration.StorageConfig) {
	t.Helper()

	config := configuration.StorageConfig{Endpoint: f.Endpoint(), Bucket: f.Bucket}
	client, err := minio.New(config.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4("key", "secret", ""),
		Secure: false,
	})
	if err != nil {
		t.Fatalf("build the fake storage client: %v", err)
	}

	return client, config
}

// Endpoint is the host:port the fake listens on.
func (f *StorageServer) Endpoint() string {
	return f.Server.Listener.Addr().String()
}

func (f *StorageServer) serve(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	f.requests = append(f.requests, StorageRequest{Method: r.Method, Path: r.URL.Path})
	status, missing := f.status, f.missing
	f.mu.Unlock()

	_, _ = io.Copy(io.Discard, r.Body)

	// The client asks for the region of the bucket before it addresses an
	// object, that lookup is answered whatever the object requests do.
	if r.URL.Query().Has("location") {
		w.Header().Set("Content-Type", "application/xml")
		_, _ = io.WriteString(w, `<?xml version="1.0" encoding="UTF-8"?>`+
			`<LocationConstraint xmlns="http://s3.amazonaws.com/doc/2006-03-01/">us-east-1</LocationConstraint>`)

		return
	}

	if status != http.StatusOK {
		// The code has to match the status, the client retries a server error but
		// gives up straight away on a refusal.
		code := "InternalError"
		if status < http.StatusInternalServerError {
			code = "AccessDenied"
		}

		w.Header().Set("Content-Type", "application/xml")
		w.WriteHeader(status)
		_, _ = io.WriteString(w, `<?xml version="1.0" encoding="UTF-8"?><Error>`+
			`<Code>`+code+`</Code><Message>the fake storage was told to fail</Message></Error>`)

		return
	}

	switch r.Method {
	case http.MethodHead:
		if missing {
			w.Header().Set("Content-Type", "application/xml")
			w.WriteHeader(http.StatusNotFound)

			return
		}

		w.WriteHeader(http.StatusOK)
	case http.MethodPut:
		w.Header().Set("ETag", `"d41d8cd98f00b204e9800998ecf8427e"`)
		w.WriteHeader(http.StatusOK)
	case http.MethodDelete:
		w.WriteHeader(http.StatusNoContent)
	default:
		w.WriteHeader(http.StatusOK)
	}
}
