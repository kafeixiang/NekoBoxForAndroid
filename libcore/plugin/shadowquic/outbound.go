package shadowquic

import (
	"context"
	"net"

	"github.com/exclavenetwork/sing-shadowquic"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/common/dialer"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	"libcore/plugin/pluginoption"
)

func RegisterOutbound(registry *outbound.Registry) {
	outbound.Register[pluginoption.ShadowQUICOutboundOptions](registry, pluginoption.TypeShadowQUIC, NewOutbound)
}

var (
	_ adapter.Outbound                = (*Outbound)(nil)
	_ adapter.OutboundWithMultiplex   = (*Outbound)(nil)
	_ adapter.InterfaceUpdateListener = (*Outbound)(nil)
	_ adapter.IdleConnectionKeeper    = (*Outbound)(nil)
)

type Outbound struct {
	outbound.Adapter
	logger logger.ContextLogger
	client *shadowquic.Client
}

func NewOutbound(ctx context.Context, router adapter.Router, logger log.ContextLogger, tag string, options pluginoption.ShadowQUICOutboundOptions) (adapter.Outbound, error) {
	outboundDialer, err := dialer.NewWithOptions(dialer.Options{
		Context:        ctx,
		Options:        options.DialerOptions,
		RemoteIsDomain: options.ServerIsDomain(),
	})
	if err != nil {
		return nil, err
	}
	serverName := options.ServerName
	if serverName == "" {
		serverName = options.Server
	}
	client, err := shadowquic.NewClient(shadowquic.ClientOptions{
		Context:           ctx,
		Dialer:            outboundDialer,
		ServerAddress:     options.Build(),
		Username:          options.Username,
		Password:          options.Password,
		ServerName:        serverName,
		NextProtos:        options.ALPN,
		CongestionControl: options.CongestionControl,
		UDPOverStream:     options.UDPOverStream,
		ZeroRTTHandshake:  options.ZeroRTTHandshake,
		SunnyQUIC:         options.SunnyQUIC,
	})
	if err != nil {
		return nil, err
	}
	return &Outbound{
		Adapter: outbound.NewAdapterWithDialerOptions(pluginoption.TypeShadowQUIC, tag, []string{N.NetworkTCP, N.NetworkUDP}, options.DialerOptions),
		logger:  logger,
		client:  client,
	}, nil
}

func (o *Outbound) Close() error {
	return o.client.Close()
}

func (o *Outbound) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	switch network {
	case N.NetworkTCP:
		ctx, metadata := adapter.ExtendContext(ctx)
		metadata.Outbound = o.Tag()
		metadata.Destination = destination
		o.logger.InfoContext(ctx, "outbound connection to ", destination)
		return o.client.DialConn(ctx, destination)
	case N.NetworkUDP:
		ctx, metadata := adapter.ExtendContext(ctx)
		metadata.Outbound = o.Tag()
		metadata.Destination = destination
		o.logger.InfoContext(ctx, "outbound packet connection to ", destination)
		packetConn, err := o.ListenPacket(ctx, destination)
		if err != nil {
			return nil, err
		}
		return bufio.NewBindPacketConn(packetConn, destination), nil
	default:
		return nil, E.Extend(N.ErrUnknownNetwork, network)
	}
}

func (o *Outbound) ListenPacket(ctx context.Context, destination M.Socksaddr) (net.PacketConn, error) {
	return o.client.ListenPacket(ctx)
}

func (o *Outbound) InterfaceUpdated(ctx context.Context) {
	_ = o.client.Close()
}

func (o *Outbound) MultiplexEnabled() bool {
	return true
}

func (o *Outbound) SetKeepIdleConnections(keep bool) {
	o.client.SetKeepIdleConnections(keep)
}

func (o *Outbound) CloseIdleConnections() {
	o.client.CloseIdleConnections()
}

func (o *Outbound) Start(stage adapter.StartStage, scope *adapter.Scope) error {
	if stage != adapter.StartStateInitialize {
		return nil
	}
	scope.Add(func() error {
		return o.client.Close()
	})
	return nil
}
