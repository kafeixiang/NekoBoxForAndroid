package plugin

import (
	"github.com/sagernet/sing-box/adapter/outbound"

	"libcore/plugin/juicity"
)

// RegisterPlugins registers bridged outbound protocols into the sing-box outbound registry.
// This allows adding and extending protocols seamlessly without modifying sing-box upstream code.
func RegisterPlugins(registry *outbound.Registry) {
	juicity.RegisterOutbound(registry)
}
