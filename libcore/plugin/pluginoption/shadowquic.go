package pluginoption

import "github.com/sagernet/sing-box/option"

type ShadowQUICOutboundOptions struct {
	option.DialerOptions
	option.ServerOptions
	Username          string   `json:"username,omitempty"`
	Password          string   `json:"password,omitempty"`
	ServerName        string   `json:"server_name,omitempty"`
	ALPN              []string `json:"alpn,omitempty"`
	CongestionControl string   `json:"congestion_control,omitempty"`
	UDPOverStream     bool     `json:"udp_over_stream,omitempty"`
	ZeroRTTHandshake  bool     `json:"zero_rtt_handshake,omitempty"`
	SunnyQUIC         bool     `json:"sunny_quic,omitempty"`
}
