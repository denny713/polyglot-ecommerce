package storage

import (
	"net/url"
	"path"
	"strings"
)

// Get extracts the image file name from a file URL.
func Get(fileURL string) string {
	fileURL = strings.TrimSpace(fileURL)
	if fileURL == "" {
		return ""
	}

	filePath := fileURL
	if parsed, err := url.Parse(fileURL); err == nil && parsed.Path != "" {
		filePath = parsed.Path
	}

	name := path.Base(strings.TrimRight(filePath, "/"))
	if name == "." || name == "/" {
		return ""
	}

	result, err := url.PathUnescape(name)
	if err != nil {
		return name
	}

	return result
}
