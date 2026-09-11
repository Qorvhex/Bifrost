//go:build windows
// +build windows

package web

import (
	"os"
	"runtime"

	webview2 "github.com/jchv/go-webview2"
	"golang.org/x/sys/windows"
)

var (
	modUser32        = windows.NewLazySystemDLL("user32.dll")
	procGetWindowLong = modUser32.NewProc("GetWindowLongPtrW")
	procSetWindowLong = modUser32.NewProc("SetWindowLongPtrW")
)

const (
	wsMaximizeBox = 0x00010000
)

// RunNativeWindow opens a true native Windows desktop window using Microsoft WebView2.
// It enforces a standard fixed width of 430px (cannot be stretched horizontally),
// while allowing smooth vertical resizing between 450px and 880px.
func RunNativeWindow(url string, title string, width, height int) bool {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()

	standardWidth := 430
	standardHeight := 490
	if height > 0 {
		standardHeight = height
	}

	w := webview2.NewWithOptions(webview2.WebViewOptions{
		Debug:     false,
		AutoFocus: true,
		WindowOptions: webview2.WindowOptions{
			Title:  title,
			Width:  uint(standardWidth),
			Height: uint(standardHeight),
			Center: true,
		},
	})
	if w == nil {
		return false
	}
	defer func() {
		w.Destroy()
		os.Exit(0)
	}()

	_ = w.Bind("exitProcess", func() {
		os.Exit(0)
	})

	// 1. Set standard initial size
	w.SetSize(standardWidth, standardHeight, webview2.HintNone)

	// 2. Lock horizontal width strictly: min width = 430, max width = 430
	// This prevents the user from stretching the window sideways.
	// Allow vertical height to be adjusted between 450px and 880px.
	w.SetSize(standardWidth, 450, webview2.HintMin)
	w.SetSize(standardWidth, 880, webview2.HintMax)

	// 3. Disable Maximize button in Win32 window style
	hwnd := uintptr(w.Window())
	if hwnd != 0 {
		gwlStyleIndex := -16
		style, _, _ := procGetWindowLong.Call(hwnd, uintptr(gwlStyleIndex))
		if style != 0 {
			newStyle := style &^ uintptr(wsMaximizeBox)
			_, _, _ = procSetWindowLong.Call(hwnd, uintptr(gwlStyleIndex), newStyle)
		}
	}

	w.Navigate(url)
	w.Run()

	return true
}
