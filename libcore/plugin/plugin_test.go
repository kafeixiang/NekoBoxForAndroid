package plugin_test

import (
	"context"
	"testing"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/service"

	"libcore/plugin"
)

func TestPluginRegistration(t *testing.T) {
	registry := include.OutboundRegistry()
	plugin.RegisterPlugins(registry)

	ctx := context.Background()
	ctx = box.Context(ctx,
		include.InboundRegistry(),
		registry,
		include.EndpointRegistry(),
		include.DNSTransportRegistry(),
		include.ServiceRegistry(),
		include.CertificateProviderRegistry(),
	)
	ctx = service.ContextWithDefaultRegistry(ctx)

	configs := []string{
		`{
			"outbounds": [
				{
					"type": "trusttunnel",
					"tag": "tt-out",
					"server": "127.0.0.1",
					"server_port": 443,
					"username": "user",
					"password": "pass",
					"tls": {
						"enabled": true
					}
				}
			]
		}`,
		`{
			"outbounds": [
				{
					"type": "juicity",
					"tag": "juicity-out",
					"server": "127.0.0.1",
					"server_port": 443,
					"uuid": "a61b8f59-6799-470a-8bf8-2b814a66a1a4",
					"password": "pass",
					"tls": {
						"enabled": true
					}
				}
			]
		}`,
	}

	for _, cfg := range configs {
		var options option.Options
		err := options.UnmarshalJSONContext(ctx, []byte(cfg))
		if err != nil {
			t.Fatalf("Failed to parse config %s: %v", cfg, err)
		}
	}
}
