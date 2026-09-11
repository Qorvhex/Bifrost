package updater

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

var (
	// Default manifest endpoints (Primary CDN + Direct VPS fallback)
	DefaultManifestURLs = []string{
		"https://hermes.miladiran.online/f/bifrost-version.json",
		"https://monitor.bluecats.ir/f/bifrost-version.json",
	}

	// Common local proxy endpoints used by users in Iran
	knownLocalProxies = []string{
		"http://127.0.0.1:7890",   // Clash / Clash Verge / Mihomo
		"http://127.0.0.1:10809",  // v2rayN HTTP
		"socks5://127.0.0.1:10808", // v2rayN SOCKS
		"http://127.0.0.1:20811",  // NekoBox / Nekoray HTTP
		"socks5://127.0.0.1:2080",  // NekoBox SOCKS
		"socks5://127.0.0.1:5050",  // Bifrost SOCKS5 itself
	}
)

// UpdateInfo holds version and download details
type UpdateInfo struct {
	Version     string `json:"version"`
	ReleaseDate string `json:"release_date"`
	DownloadURL string `json:"download_url"`
	SetupURL    string `json:"setup_url"`
	Changelog   string `json:"changelog"`
	HasUpdate   bool   `json:"has_update"`
}

// CheckUpdate queries all known manifest URLs using smart transports
func CheckUpdate(currentVersion string, customURL string) (*UpdateInfo, error) {
	urls := DefaultManifestURLs
	if customURL != "" {
		urls = []string{customURL}
	}

	var lastErr error
	for _, targetURL := range urls {
		info, err := fetchManifest(targetURL)
		if err == nil && info != nil && info.Version != "" {
			info.HasUpdate = isNewerVersion(info.Version, currentVersion)
			return info, nil
		}
		lastErr = err
	}

	return nil, fmt.Errorf("خطا در برقراری ارتباط با سرور به‌روزرسانی: %v", lastErr)
}

func fetchManifest(targetURL string) (*UpdateInfo, error) {
	// Try 1: Direct or System Proxy client
	client := getSmartHTTPClient(15 * time.Second, nil)
	resp, err := client.Get(targetURL)
	if err == nil && resp.StatusCode == http.StatusOK {
		defer resp.Body.Close()
		var info UpdateInfo
		if err := json.NewDecoder(resp.Body).Decode(&info); err == nil {
			return &info, nil
		}
	}
	if resp != nil {
		_ = resp.Body.Close()
	}

	// Try 2: If failed, probe active local proxies
	for _, pStr := range knownLocalProxies {
		pURL, err := url.Parse(pStr)
		if err != nil {
			continue
		}
		pClient := getSmartHTTPClient(7*time.Second, pURL)
		r, err := pClient.Get(targetURL)
		if err == nil && r.StatusCode == http.StatusOK {
			defer r.Body.Close()
			var info UpdateInfo
			if err := json.NewDecoder(r.Body).Decode(&info); err == nil {
				return &info, nil
			}
		}
		if r != nil {
			_ = r.Body.Close()
		}
	}

	if err != nil {
		return nil, err
	}
	return nil, fmt.Errorf("response status %v", resp.Status)
}

// isNewerVersion compares semver strings like "2.2.0" > "2.1.0"
func isNewerVersion(remote, current string) bool {
	cleanRemote := strings.TrimPrefix(strings.TrimSpace(remote), "v")
	cleanCurrent := strings.TrimPrefix(strings.TrimSpace(current), "v")

	if cleanRemote == "" || cleanCurrent == "" {
		return false
	}

	rParts := strings.Split(cleanRemote, ".")
	cParts := strings.Split(cleanCurrent, ".")

	for i := 0; i < len(rParts) && i < len(cParts); i++ {
		rNum, err1 := strconv.Atoi(rParts[i])
		cNum, err2 := strconv.Atoi(cParts[i])
		if err1 == nil && err2 == nil {
			if rNum > cNum {
				return true
			}
			if rNum < cNum {
				return false
			}
		}
	}

	return len(rParts) > len(cParts)
}

// CleanupOldBinary removes any leftover .old binary from a previous update
func CleanupOldBinary() {
	execPath, err := os.Executable()
	if err != nil {
		return
	}
	oldPath := execPath + ".old"
	if _, err := os.Stat(oldPath); err == nil {
		_ = os.Remove(oldPath)
		log.Printf("[Updater] Cleaned up temporary binary: %s\n", oldPath)
	}
}

// ApplyUpdate downloads the new executable, renames current, and restarts
func ApplyUpdate(downloadURL string) error {
	if downloadURL == "" {
		return fmt.Errorf("آدرس دانلود نسخه جدید نامعتبر است")
	}

	execPath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("عدم امکان تشخیص مسیر اجرای برنامه: %w", err)
	}

	execPath, err = filepath.EvalSymlinks(execPath)
	if err != nil {
		return fmt.Errorf("خطا در دسترسی به فایل اصلی: %w", err)
	}

	dir := filepath.Dir(execPath)
	newPath := filepath.Join(dir, "Bifrost.exe.new")
	oldPath := execPath + ".old"

	// Prepare candidate URLs (original + direct fallback)
	candidateURLs := []string{downloadURL}
	if strings.Contains(downloadURL, "hermes.miladiran.online") {
		candidateURLs = append(candidateURLs, strings.Replace(downloadURL, "hermes.miladiran.online", "monitor.bluecats.ir", 1))
	}

	var downloadErr error
	downloadSuccess := false

	for _, u := range candidateURLs {
		// Try downloading with smart client
		if err := downloadBinaryToFile(u, newPath); err == nil {
			downloadSuccess = true
			break
		} else {
			downloadErr = err
		}
	}

	if !downloadSuccess {
		return fmt.Errorf("خطا در دانلود نسخه جدید: %w", downloadErr)
	}

	// 2. Remove any previous .old file
	_ = os.Remove(oldPath)

	// 3. Rename current running executable to .old (Windows allows renaming running .exe!)
	if err := os.Rename(execPath, oldPath); err != nil {
		_ = os.Remove(newPath)
		return fmt.Errorf("خطا در جایگزینی فایل اجرایی: %w", err)
	}

	// 4. Move .new to original path
	if err := os.Rename(newPath, execPath); err != nil {
		// Rollback
		_ = os.Rename(oldPath, execPath)
		_ = os.Remove(newPath)
		return fmt.Errorf("خطا در فعال‌سازی نسخه جدید: %w", err)
	}

	// 5. Spawn new process detached
	cmd := exec.Command(execPath)
	cmd.Dir = dir
	if err := cmd.Start(); err != nil {
		log.Printf("[Updater] Failed to auto-restart: %v\n", err)
	}

	// 6. Terminate this old process cleanly
	go func() {
		time.Sleep(300 * time.Millisecond)
		os.Exit(0)
	}()

	return nil
}

func downloadBinaryToFile(downloadURL string, destPath string) error {
	clients := []*http.Client{
		getSmartHTTPClient(90*time.Second, nil),
	}

	for _, pStr := range knownLocalProxies {
		if pURL, err := url.Parse(pStr); err == nil {
			clients = append(clients, getSmartHTTPClient(90*time.Second, pURL))
		}
	}

	var lastErr error
	for _, c := range clients {
		resp, err := c.Get(downloadURL)
		if err != nil {
			lastErr = err
			continue
		}
		if resp.StatusCode != http.StatusOK {
			_ = resp.Body.Close()
			lastErr = fmt.Errorf("status code %d", resp.StatusCode)
			continue
		}

		outFile, err := os.OpenFile(destPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0755)
		if err != nil {
			_ = resp.Body.Close()
			return err
		}

		written, err := io.Copy(outFile, resp.Body)
		outFile.Close()
		_ = resp.Body.Close()

		if err != nil {
			_ = os.Remove(destPath)
			lastErr = err
			continue
		}

		if written < 1024*1024 {
			_ = os.Remove(destPath)
			lastErr = fmt.Errorf("فایل دانلودی ناقص است")
			continue
		}

		return nil
	}

	return lastErr
}

// getSmartHTTPClient constructs an HTTP client that respects Windows system proxy and IPv4 prioritization
func getSmartHTTPClient(timeout time.Duration, forceProxy *url.URL) *http.Client {
	dialer := &net.Dialer{
		Timeout:   12 * time.Second,
		KeepAlive: 30 * time.Second,
	}

	transport := &http.Transport{
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			// Prefer IPv4 to avoid broken IPv6 hangs in Iran
			c, err := dialer.DialContext(ctx, "tcp4", addr)
			if err == nil {
				return c, nil
			}
			return dialer.DialContext(ctx, network, addr)
		},
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: false,
		},
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          10,
		IdleConnTimeout:       30 * time.Second,
		TLSHandshakeTimeout:   12 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
	}

	if forceProxy != nil {
		transport.Proxy = http.ProxyURL(forceProxy)
	} else if sysProxy := getSystemProxy(); sysProxy != nil {
		transport.Proxy = http.ProxyURL(sysProxy)
	} else {
		transport.Proxy = http.ProxyFromEnvironment
	}

	return &http.Client{
		Transport: transport,
		Timeout:   timeout,
	}
}
