package updater

import (
	"testing"
)

func TestIsNewerVersion(t *testing.T) {
	cases := []struct {
		remote   string
		current  string
		expected bool
	}{
		{"2.2.0", "2.1.0", true},
		{"2.1.1", "2.1.0", true},
		{"3.0.0", "2.9.9", true},
		{"2.1.0", "2.1.0", false},
		{"2.0.9", "2.1.0", false},
		{"v2.3.0", "2.2.0", true},
		{"2.2.0", "v2.2.0", false},
	}

	for _, tc := range cases {
		got := isNewerVersion(tc.remote, tc.current)
		if got != tc.expected {
			t.Errorf("isNewerVersion(%q, %q) = %v; want %v", tc.remote, tc.current, got, tc.expected)
		}
	}
}
