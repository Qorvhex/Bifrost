//go:build !windows

package web

// RunNativeWindow stub for non-Windows platforms
func RunNativeWindow(url string, title string, width, height int) bool {
	return false
}
