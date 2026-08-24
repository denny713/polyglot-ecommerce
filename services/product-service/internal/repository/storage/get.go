package storage

import (
	"net/url"
	"path"
	"strings"

	"product-service/internal/configuration"
)

// Get extracts the object name of a stored file from its URL. MinIO addresses an
// object by its full name, folder included, so the folder segments are kept and
// only the scheme, the host and the bucket are dropped.
func Get(fileURL string) string {
	fileURL = strings.TrimSpace(fileURL)
	if fileURL == "" {
		return ""
	}

	filePath := fileURL
	if parsed, err := url.Parse(fileURL); err == nil && parsed.Path != "" {
		filePath = parsed.Path
	}

	filePath = strings.Trim(path.Clean(filePath), "/")
	if bucket := configuration.MinioBucket; bucket != "" {
		filePath = strings.TrimPrefix(filePath, bucket+"/")
	}

	if filePath == "" || filePath == "." || filePath == "/" {
		return ""
	}

	unescaped, err := url.PathUnescape(filePath)
	if err != nil {
		return filePath
	}

	return unescaped
}
