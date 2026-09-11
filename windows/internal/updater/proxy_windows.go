//go:build windows
// +build windows

package updater

import (
	"net/url"
	"strings"

	"golang.org/x/sys/windows/registry"
)

// getSystemProxy detects active proxy from Windows WinINET Internet Settings
func getSystemProxy() *url.URL {
	k, err := registry.OpenKey(registry.CURRENT_USER, `Software\Microsoft\Windows\CurrentVersion\Internet Settings`, registry.QUERY_VALUE)
	if err != nil {
		return nil
	}
	defer k.Close()

	enable, _, err := k.GetIntegerValue("ProxyEnable")
	if err != nil || enable == 0 {
		return nil
	}

	server, _, err := k.GetStringValue("ProxyServer")
	if err != nil || server == "" {
		return nil
	}

	// Format can be "127.0.0.1:7890" or "http=127.0.0.1:7890;https=127.0.0.1:7890"
	if strings.Contains(server, "=") {
		parts := strings.Split(server, ";")
		for _, part := range parts {
			kv := strings.SplitN(strings.TrimSpace(part), "=", 2)
			if len(kv) == 2 && (kv[0] == "https" || kv[0] == "http") {
				server = kv[1]
				break
			}
		}
	}

	server = strings.TrimSpace(server)
	if !strings.HasPrefix(server, "http://") && !strings.HasPrefix(server, "socks5://") {
		server = "http://" + server
	}

	u, err := url.Parse(server)
	if err != nil {
		return nil
	}
	return u
}
