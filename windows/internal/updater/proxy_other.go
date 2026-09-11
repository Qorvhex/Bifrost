//go:build !windows
// +build !windows

package updater

import "net/url"

func getSystemProxy() *url.URL {
	return nil
}
