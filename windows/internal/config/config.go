package config

import (
	"encoding/json"
	"fmt"
	"log"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

// ProxyConfig represents a Cloudflare Worker Proxy configuration
type ProxyConfig struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	WorkerHost string `json:"worker_host"`
	CleanIP    string `json:"clean_ip,omitempty"`
	Secret     string `json:"secret,omitempty"`
	Port       int    `json:"port"`
	IsActive   bool   `json:"is_active"`
	CreatedAt  int64  `json:"created_at"`
}

// AppConfig represents global application settings and saved workers
type AppConfig struct {
	LocalPort     int           `json:"local_port"`
	BridgeEnabled bool          `json:"bridge_enabled"`
	ActiveID      string        `json:"active_id"`
	AutoConnectTG bool          `json:"auto_connect_tg"`
	Proxies       []ProxyConfig `json:"proxies"`
}

// ConfigManager handles loading and persisting configurations safely
type ConfigManager struct {
	mu       sync.RWMutex
	filePath string
	config   AppConfig
}

const (
	DefaultLocalPort = 5050
	DefaultHttpPort  = 5055
	DefaultWorkerPort = 443
)

// NewConfigManager initializes and loads the configuration file
func NewConfigManager(customPath string) (*ConfigManager, error) {
	path := customPath
	if path == "" {
		// 1. Preferred persistent path: %APPDATA%/Bifrost/bifrost_config.json
		if uDir, err := os.UserConfigDir(); err == nil && uDir != "" {
			appFolder := filepath.Join(uDir, "Bifrost")
			_ = os.MkdirAll(appFolder, 0755)
			permanentPath := filepath.Join(appFolder, "bifrost_config.json")

			// Check if permanent config already exists
			if _, err := os.Stat(permanentPath); err == nil {
				path = permanentPath
			} else {
				// Search for legacy config files to automatically migrate
				legacyCandidates := []string{}
				if execPath, err := os.Executable(); err == nil {
					legacyCandidates = append(legacyCandidates, filepath.Join(filepath.Dir(execPath), "bifrost_config.json"))
				}
				legacyCandidates = append(legacyCandidates, "bifrost_config.json")

				migrated := false
				for _, legPath := range legacyCandidates {
					if data, err := os.ReadFile(legPath); err == nil && len(data) > 0 {
						if err := os.WriteFile(permanentPath, data, 0644); err == nil {
							path = permanentPath
							migrated = true
							log.Printf("[Config] Migrated legacy config from %s to permanent store %s\n", legPath, permanentPath)
							break
						}
					}
				}
				if !migrated {
					path = permanentPath
				}
			}
		} else {
			// Fallback if UserConfigDir is not supported
			if execPath, err := os.Executable(); err == nil {
				path = filepath.Join(filepath.Dir(execPath), "bifrost_config.json")
			} else {
				path = "bifrost_config.json"
			}
		}
	}

	cm := &ConfigManager{
		filePath: path,
		config: AppConfig{
			LocalPort:     DefaultLocalPort,
			BridgeEnabled: true,
			AutoConnectTG: true,
			Proxies:       make([]ProxyConfig, 0),
		},
	}

	if err := cm.load(); err != nil && !os.IsNotExist(err) {
		// If load failed with syntax error, try to keep default
	}

	return cm, nil
}

func (cm *ConfigManager) load() error {
	data, err := os.ReadFile(cm.filePath)
	if err != nil {
		return err
	}

	var cfg AppConfig
	if err := json.Unmarshal(data, &cfg); err != nil {
		return err
	}

	if cfg.LocalPort <= 0 || cfg.LocalPort > 65535 {
		cfg.LocalPort = DefaultLocalPort
	}

	cm.config = cfg
	return nil
}

// Save persists the current configuration to disk
func (cm *ConfigManager) Save() error {
	cm.mu.RLock()
	defer cm.mu.RUnlock()

	data, err := json.MarshalIndent(cm.config, "", "  ")
	if err != nil {
		return err
	}

	return os.WriteFile(cm.filePath, data, 0644)
}

// GetConfig returns a copy of current AppConfig
func (cm *ConfigManager) GetConfig() AppConfig {
	cm.mu.RLock()
	defer cm.mu.RUnlock()
	return cm.config
}

// GetActiveProxy returns the currently selected proxy configuration
func (cm *ConfigManager) GetActiveProxy() *ProxyConfig {
	cm.mu.RLock()
	defer cm.mu.RUnlock()

	for _, p := range cm.config.Proxies {
		if p.ID == cm.config.ActiveID {
			cpy := p
			return &cpy
		}
	}
	if len(cm.config.Proxies) > 0 {
		cpy := cm.config.Proxies[0]
		return &cpy
	}
	return nil
}

// SetLocalPort updates the local SOCKS5 listen port
func (cm *ConfigManager) SetLocalPort(port int) error {
	cm.mu.Lock()
	if port < 1024 || port > 65535 {
		cm.mu.Unlock()
		return fmt.Errorf("invalid port number: %d (must be 1024-65535)", port)
	}
	cm.config.LocalPort = port
	cm.mu.Unlock()
	return cm.Save()
}

// SetBridgeEnabled toggles the proxy bridge
func (cm *ConfigManager) SetBridgeEnabled(enabled bool) error {
	cm.mu.Lock()
	cm.config.BridgeEnabled = enabled
	cm.mu.Unlock()
	return cm.Save()
}

// SetActiveProxy switches the active proxy by ID
func (cm *ConfigManager) SetActiveProxy(id string) error {
	cm.mu.Lock()
	cm.config.ActiveID = id
	for i := range cm.config.Proxies {
		cm.config.Proxies[i].IsActive = (cm.config.Proxies[i].ID == id)
	}
	cm.mu.Unlock()
	return cm.Save()
}

// CleanHost strips protocol prefixes, paths and trailing slashes
func CleanHost(h string) string {
	h = strings.TrimSpace(h)
	h = strings.TrimPrefix(h, "https://")
	h = strings.TrimPrefix(h, "http://")
	h = strings.TrimPrefix(h, "wss://")
	h = strings.TrimPrefix(h, "ws://")
	h = strings.TrimRight(h, "/")
	if idx := strings.Index(h, "/"); idx != -1 {
		h = h[:idx]
	}
	return h
}

// AddOrUpdateProxy adds a new proxy or updates an existing one
func (cm *ConfigManager) AddOrUpdateProxy(p ProxyConfig, makeActive bool) error {
	cm.mu.Lock()
	p.WorkerHost = CleanHost(p.WorkerHost)
	p.CleanIP = strings.TrimSpace(p.CleanIP)
	p.Secret = strings.TrimSpace(p.Secret)
	if p.ID == "" {
		p.ID = fmt.Sprintf("proxy_%d", time.Now().UnixNano())
	}
	if p.Port <= 0 {
		p.Port = DefaultWorkerPort
	}
	if p.CreatedAt == 0 {
		p.CreatedAt = time.Now().UnixMilli()
	}

	found := false
	for i, existing := range cm.config.Proxies {
		if existing.ID == p.ID {
			cm.config.Proxies[i] = p
			found = true
			break
		}
	}
	if !found {
		cm.config.Proxies = append([]ProxyConfig{p}, cm.config.Proxies...)
	}

	if makeActive || len(cm.config.Proxies) == 1 || cm.config.ActiveID == "" {
		cm.config.ActiveID = p.ID
		for i := range cm.config.Proxies {
			cm.config.Proxies[i].IsActive = (cm.config.Proxies[i].ID == p.ID)
		}
	}

	cm.mu.Unlock()
	return cm.Save()
}

// DeleteProxy removes a proxy configuration
func (cm *ConfigManager) DeleteProxy(id string) error {
	cm.mu.Lock()
	newList := make([]ProxyConfig, 0, len(cm.config.Proxies))
	for _, p := range cm.config.Proxies {
		if p.ID != id {
			newList = append(newList, p)
		}
	}
	cm.config.Proxies = newList

	if cm.config.ActiveID == id {
		if len(newList) > 0 {
			cm.config.ActiveID = newList[0].ID
			for i := range cm.config.Proxies {
				cm.config.Proxies[i].IsActive = (cm.config.Proxies[i].ID == cm.config.ActiveID)
			}
		} else {
			cm.config.ActiveID = ""
		}
	}

	cm.mu.Unlock()
	return cm.Save()
}

// ParseTwpLink parses RFC 3986 twp:// links matching the Bifrost Android standard
func ParseTwpLink(rawInput string) (*ProxyConfig, error) {
	raw := strings.TrimSpace(rawInput)
	if raw == "" {
		return nil, fmt.Errorf("empty link")
	}

	// Support twp://, tg://, https://, or bare strings
	parsedURL, err := url.Parse(raw)
	if err != nil {
		return nil, fmt.Errorf("invalid URL: %w", err)
	}

	var workerHost string
	var secret string
	var cleanIP string
	port := DefaultWorkerPort
	name := ""

	// Extract UserInfo (secret@)
	if parsedURL.User != nil {
		secret = parsedURL.User.Username()
	}

	// Extract Host and Port
	if parsedURL.Host != "" {
		hostPart := parsedURL.Hostname()
		if hostPart != "proxy" && hostPart != "worker" && hostPart != "twp" {
			workerHost = hostPart
		}
		if pStr := parsedURL.Port(); pStr != "" {
			if p, err := strconv.Atoi(pStr); err == nil && p > 0 {
				port = p
			}
		}
	}

	// Query parameters
	query := parsedURL.Query()
	if workerHost == "" {
		workerHost = query.Get("server")
		if workerHost == "" {
			workerHost = query.Get("host")
		}
		if workerHost == "" {
			workerHost = query.Get("worker")
		}
	}

	if secret == "" {
		secret = query.Get("secret")
		if secret == "" {
			secret = query.Get("token")
		}
	}

	cleanIP = query.Get("clean_ip")
	if cleanIP == "" {
		cleanIP = query.Get("cleanip")
	}
	if cleanIP == "" {
		cleanIP = query.Get("clean-ip")
	}
	if cleanIP == "" {
		cleanIP = query.Get("ip")
	}
	if cleanIP == "" {
		cleanIP = query.Get("cdn_ip")
	}

	if pStr := query.Get("port"); pStr != "" && port == DefaultWorkerPort {
		if p, err := strconv.Atoi(pStr); err == nil && p > 0 {
			port = p
		}
	}

	// Fragment or query for name
	if parsedURL.Fragment != "" {
		name, _ = url.QueryUnescape(parsedURL.Fragment)
	} else if qName := query.Get("name"); qName != "" {
		name = qName
	}

	workerHost = strings.TrimSpace(workerHost)
	if workerHost == "" {
		// Fallback for custom schemes where net/url parses twp:// differently
		ssp := strings.TrimPrefix(raw, "twp://")
		atIdx := strings.Index(ssp, "@")
		candidate := ssp
		if atIdx != -1 {
			if secret == "" {
				secret = ssp[:atIdx]
			}
			candidate = ssp[atIdx+1:]
		}
		qIdx := strings.IndexAny(candidate, "?#")
		if qIdx != -1 {
			candidate = candidate[:qIdx]
		}
		colonIdx := strings.Index(candidate, ":")
		if colonIdx != -1 {
			workerHost = candidate[:colonIdx]
			if p, err := strconv.Atoi(candidate[colonIdx+1:]); err == nil {
				port = p
			}
		} else {
			workerHost = candidate
		}
	}

	if workerHost == "" {
		return nil, fmt.Errorf("could not resolve worker host from link")
	}

	if name == "" {
		name = workerHost
	}

	return &ProxyConfig{
		ID:         fmt.Sprintf("proxy_%d", time.Now().UnixNano()),
		Name:       name,
		WorkerHost: workerHost,
		CleanIP:    strings.TrimSpace(cleanIP),
		Secret:     strings.TrimSpace(secret),
		Port:       port,
		IsActive:   false,
		CreatedAt:  time.Now().UnixMilli(),
	}, nil
}

// GenerateTwpLink converts a ProxyConfig into a standard twp:// URI
func GenerateTwpLink(p ProxyConfig) string {
	var sb strings.Builder
	sb.WriteString("twp://")

	if p.Secret != "" {
		sb.WriteString(url.QueryEscape(p.Secret))
		sb.WriteString("@")
	}

	sb.WriteString(p.WorkerHost)

	if p.Port > 0 && p.Port != DefaultWorkerPort {
		sb.WriteString(fmt.Sprintf(":%d", p.Port))
	}

	query := url.Values{}
	if p.CleanIP != "" {
		query.Set("clean_ip", p.CleanIP)
	}

	queryString := query.Encode()
	if queryString != "" {
		sb.WriteString("?")
		sb.WriteString(queryString)
	}

	if p.Name != "" && p.Name != p.WorkerHost {
		sb.WriteString("#")
		sb.WriteString(url.QueryEscape(p.Name))
	}

	return sb.String()
}
