package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestParseTwpLink(t *testing.T) {
	tests := []struct {
		name       string
		link       string
		wantHost   string
		wantPort   int
		wantClean  string
		wantSecret string
		wantName   string
	}{
		{
			name:       "Standard TWP with clean IP and Name",
			link:       "twp://my-proxy.workers.dev?clean_ip=104.16.132.229#FastServer",
			wantHost:   "my-proxy.workers.dev",
			wantPort:   443,
			wantClean:  "104.16.132.229",
			wantSecret: "",
			wantName:   "FastServer",
		},
		{
			name:       "TWP with secret, custom port, clean IP and Name",
			link:       "twp://mysecret@proxy2.workers.dev:8443?clean_ip=1music.cc#VIP",
			wantHost:   "proxy2.workers.dev",
			wantPort:   8443,
			wantClean:  "1music.cc",
			wantSecret: "mysecret",
			wantName:   "VIP",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cfg, err := ParseTwpLink(tt.link)
			if err != nil {
				t.Fatalf("ParseTwpLink failed: %v", err)
			}
			if cfg.WorkerHost != tt.wantHost {
				t.Errorf("WorkerHost = %v, want %v", cfg.WorkerHost, tt.wantHost)
			}
			if cfg.Port != tt.wantPort {
				t.Errorf("Port = %v, want %v", cfg.Port, tt.wantPort)
			}
			if cfg.CleanIP != tt.wantClean {
				t.Errorf("CleanIP = %v, want %v", cfg.CleanIP, tt.wantClean)
			}
			if cfg.Secret != tt.wantSecret {
				t.Errorf("Secret = %v, want %v", cfg.Secret, tt.wantSecret)
			}
			if cfg.Name != tt.wantName {
				t.Errorf("Name = %v, want %v", cfg.Name, tt.wantName)
			}

			// Test round-trip generation
			gen := GenerateTwpLink(*cfg)
			cfg2, err := ParseTwpLink(gen)
			if err != nil {
				t.Fatalf("Parse generated link failed: %v", err)
			}
			if cfg2.WorkerHost != cfg.WorkerHost || cfg2.CleanIP != cfg.CleanIP || cfg2.Secret != cfg.Secret {
				t.Errorf("Roundtrip mismatch: original %+v vs reconstructed %+v", cfg, cfg2)
			}
		})
	}
}

func TestConfigManagerPersistence(t *testing.T) {
	tmpDir := t.TempDir()
	configPath := filepath.Join(tmpDir, "test_config.json")

	cm, err := NewConfigManager(configPath)
	if err != nil {
		t.Fatalf("NewConfigManager failed: %v", err)
	}

	testProxy := ProxyConfig{
		ID:         "p1",
		Name:       "Worker 1",
		WorkerHost: "test.workers.dev",
		CleanIP:    "1.2.3.4",
		Port:       443,
	}

	if err := cm.AddOrUpdateProxy(testProxy, true); err != nil {
		t.Fatalf("AddOrUpdateProxy failed: %v", err)
	}

	active := cm.GetActiveProxy()
	if active == nil || active.ID != "p1" {
		t.Fatalf("Expected active proxy p1, got %+v", active)
	}

	// Reload from file
	cm2, err := NewConfigManager(configPath)
	if err != nil {
		t.Fatalf("Reload ConfigManager failed: %v", err)
	}

	active2 := cm2.GetActiveProxy()
	if active2 == nil || active2.ID != "p1" {
		t.Fatalf("Reloaded active proxy mismatch: %+v", active2)
	}

	_ = os.Remove(configPath)
}
