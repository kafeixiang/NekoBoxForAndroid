package libcore

import (
	"context"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/certificate"
	"github.com/sagernet/sing-box/adapter/endpoint"
	"github.com/sagernet/sing-box/adapter/inbound"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/adapter/service"
	"github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	protocolDns "github.com/sagernet/sing-box/protocol/dns"

	"libcore/plugin"

	_ "github.com/sagernet/sing-box/experimental/clashapi"
	_ "github.com/sagernet/sing-box/transport/v2rayquic"
)

func nekoboxAndroidInboundRegistry() *inbound.Registry {
	return include.InboundRegistry()
}

func nekoboxAndroidOutboundRegistry() *outbound.Registry {
	registry := include.OutboundRegistry()
	protocolDns.RegisterOutbound(registry)
	plugin.RegisterPlugins(registry)
	return registry
}

func nekoboxAndroidEndpointRegistry() *endpoint.Registry {
	return include.EndpointRegistry()
}

func nekoboxAndroidDNSTransportRegistry(localTransport LocalDNSTransport) *dns.TransportRegistry {
	registry := include.DNSTransportRegistry()
	if localTransport != nil {
		dns.RegisterTransport(registry, "local", func(ctx context.Context, logger log.ContextLogger, tag string, options option.LocalDNSServerOptions) (adapter.DNSTransport, error) {
			return newPlatformTransport(localTransport, tag, options), nil
		})
	}
	return registry
}

func nekoboxAndroidServiceRegistry() *service.Registry {
	return include.ServiceRegistry()
}

func nekoboxAndroidCertificateProviderRegistry() *certificate.Registry {
	return include.CertificateProviderRegistry()
}
