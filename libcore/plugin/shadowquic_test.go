package plugin_test

import (
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"os"
	"os/exec"
	"testing"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/service"

	"libcore/plugin"
)

func startShadowQUICServer(t *testing.T) func() {
	sqPath := "/tmp/shadowquic"
	if _, err := os.Stat(sqPath); err != nil {
		path, err := exec.LookPath("shadowquic")
		if err != nil {
			t.Skip("shadowquic binary not found, skipping live integration test")
			return func() {}
		}
		sqPath = path
	}

	cfg := `
inbounds:
  - tag: shadowquic-inbound
    type: shadowquic
    bind-addr: "127.0.0.1:18443"
    users:
        - username: "testuser"
          password: "testpassword"
    jls-upstream:
        addr: "cloudflare.com:443"
    alpn: ["h3"]
    congestion-control: bbr
    zero-rtt: false
    gso: false
outbounds:
  - tag: direct-outbound
    type: direct
    dns-strategy: prefer-ipv4
log-level: "warn"
`
	tmpCfg, err := os.CreateTemp("", "sq-*.yaml")
	if err != nil {
		t.Fatalf("failed to create temp config: %v", err)
	}
	tmpCfg.WriteString(cfg)
	tmpCfg.Close()

	cmd := exec.Command(sqPath, "run", "-c", tmpCfg.Name())
	if err := cmd.Start(); err != nil {
		os.Remove(tmpCfg.Name())
		t.Fatalf("failed to start shadowquic: %v", err)
	}
	time.Sleep(400 * time.Millisecond)

	return func() {
		_ = cmd.Process.Kill()
		_ = os.Remove(tmpCfg.Name())
	}
}

func TestShadowQUICLive(t *testing.T) {
	cleanupServer := startShadowQUICServer(t)
	defer cleanupServer()

	// 1. Setup local HTTP test server
	httpListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to start local http server: %v", err)
	}
	defer httpListener.Close()
	httpPort := httpListener.Addr().(*net.TCPAddr).Port

	httpServer := &http.Server{
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("X-Test", "ShadowQUIC-OK")
			fmt.Fprint(w, "Hello from ShadowQUIC backend")
		}),
	}
	go httpServer.Serve(httpListener)
	defer httpServer.Close()

	// 2. Setup local UDP echo server
	udpConn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to start local udp echo server: %v", err)
	}
	defer udpConn.Close()
	udpPort := udpConn.LocalAddr().(*net.UDPAddr).Port

	go func() {
		buf := make([]byte, 2048)
		for {
			n, addr, err := udpConn.ReadFrom(buf)
			if err != nil {
				return
			}
			resp := append([]byte("ECHO:"), buf[:n]...)
			udpConn.WriteTo(resp, addr)
		}
	}()

	// 3. Build Sing-Box instance with ShadowQUIC outbound
	registry := include.OutboundRegistry()
	plugin.RegisterPlugins(registry)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	ctx = box.Context(ctx,
		include.InboundRegistry(),
		registry,
		include.EndpointRegistry(),
		include.DNSTransportRegistry(),
		include.ServiceRegistry(),
		include.CertificateProviderRegistry(),
	)
	ctx = service.ContextWithDefaultRegistry(ctx)

	cfg := fmt.Sprintf(`{
		"log": {
			"level": "warn"
		},
		"inbounds": [
			{
				"type": "mixed",
				"tag": "mixed-in",
				"listen": "127.0.0.1",
				"listen_port": 19180
			}
		],
		"outbounds": [
			{
				"type": "shadowquic",
				"tag": "sq-out",
				"server": "127.0.0.1",
				"server_port": 18443,
				"username": "testuser",
				"password": "testpassword",
				"server_name": "cloudflare.com",
				"alpn": ["h3"],
				"congestion_control": "bbr",
				"udp_over_stream": false,
				"zero_rtt_handshake": false
			}
		]
	}`)

	var options option.Options
	if err := options.UnmarshalJSONContext(ctx, []byte(cfg)); err != nil {
		t.Fatalf("UnmarshalJSON failed: %v", err)
	}

	instance, err := box.New(box.Options{
		Options: options,
		Context: ctx,
	})
	if err != nil {
		t.Fatalf("box.New failed: %v", err)
	}
	if err := instance.Start(); err != nil {
		t.Fatalf("instance.Start failed: %v", err)
	}
	defer instance.Close()

	time.Sleep(300 * time.Millisecond)

	sqOutbound, ok := instance.Outbound().Outbound("sq-out")
	if !ok {
		t.Fatalf("outbound sq-out not found")
	}

	if keeper, ok := sqOutbound.(adapter.IdleConnectionKeeper); ok {
		keeper.SetKeepIdleConnections(true)
	}

	// 4. Test Outbound TCP DialContext through ShadowQUIC server
	t.Run("TCP_Direct_Outbound", func(t *testing.T) {
		dialCtx, dialCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer dialCancel()

		conn, err := sqOutbound.DialContext(dialCtx, "tcp", M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", httpPort)))
		if err != nil {
			t.Fatalf("DialContext TCP failed: %v", err)
		}
		defer conn.Close()

		req := fmt.Sprintf("GET /test HTTP/1.1\r\nHost: 127.0.0.1:%d\r\nConnection: close\r\n\r\n", httpPort)
		if _, err := conn.Write([]byte(req)); err != nil {
			t.Fatalf("conn.Write failed: %v", err)
		}

		reply, err := io.ReadAll(conn)
		if err != nil {
			t.Fatalf("conn.Read failed: %v", err)
		}
		if len(reply) == 0 {
			t.Fatalf("received empty response")
		}
		t.Logf("TCP_Direct_Outbound SUCCESS: received %d bytes", len(reply))
	})

	// 5. Test Outbound UDP ListenPacket through ShadowQUIC server
	t.Run("UDP_Direct_Outbound", func(t *testing.T) {
		listenCtx, listenCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer listenCancel()

		dstAddr := M.Socksaddr{Addr: netip.AddrFrom4([4]byte{127, 0, 0, 1}), Port: uint16(udpPort)}
		packetOutbound, ok := sqOutbound.(interface {
			ListenPacket(context.Context, M.Socksaddr) (net.PacketConn, error)
		})
		if !ok {
			t.Fatalf("sqOutbound does not implement ListenPacket")
		}
		packetConn, err := packetOutbound.ListenPacket(listenCtx, dstAddr)
		if err != nil {
			t.Fatalf("ListenPacket UDP failed: %v", err)
		}
		defer packetConn.Close()

		msg := "Hello ShadowQUIC UDP"
		targetUDPAddr := &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1).To4(), Port: udpPort}
		if _, err := packetConn.WriteTo([]byte(msg), targetUDPAddr); err != nil {
			t.Fatalf("packetConn.WriteTo failed: %v", err)
		}

		packetConn.SetReadDeadline(time.Now().Add(5 * time.Second))
		reply := make([]byte, 2048)
		n, from, err := packetConn.ReadFrom(reply)
		if err != nil {
			t.Fatalf("packetConn.ReadFrom failed: %v", err)
		}
		expected := "ECHO:" + msg
		if string(reply[:n]) != expected {
			t.Fatalf("unexpected reply: got %s, want %s (from %v)", string(reply[:n]), expected, from)
		}
		t.Logf("UDP_Direct_Outbound SUCCESS: received %s from %v", string(reply[:n]), from)
	})

	// 6. Test HTTP Request through sing-box Mixed Inbound (end-to-end proxy path)
	t.Run("HTTP_Via_Mixed_Inbound", func(t *testing.T) {
		proxyURL, err := url.Parse("http://127.0.0.1:19180")
		if err != nil {
			t.Fatalf("url.Parse failed: %v", err)
		}
		client := &http.Client{
			Transport: &http.Transport{
				Proxy: http.ProxyURL(proxyURL),
			},
			Timeout: 10 * time.Second,
		}

		req, err := http.NewRequest("GET", fmt.Sprintf("http://127.0.0.1:%d/test", httpPort), nil)
		if err != nil {
			t.Fatalf("NewRequest failed: %v", err)
		}
		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("client.Do failed: %v", err)
		}
		defer resp.Body.Close()

		body, err := io.ReadAll(resp.Body)
		if err != nil {
			t.Fatalf("ReadAll failed: %v", err)
		}

		expected := "Hello from ShadowQUIC backend"
		if string(body) != expected {
			t.Fatalf("unexpected body: got %s, want %s", string(body), expected)
		}
		t.Logf("HTTP_Via_Mixed_Inbound SUCCESS: received %s", string(body))
	})
}

func TestShadowQUICLiveUDPOverStream(t *testing.T) {
	cleanupServer := startShadowQUICServer(t)
	defer cleanupServer()

	// Setup local UDP echo server
	udpConn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to start local udp echo server: %v", err)
	}
	defer udpConn.Close()
	udpPort := udpConn.LocalAddr().(*net.UDPAddr).Port

	go func() {
		buf := make([]byte, 2048)
		for {
			n, addr, err := udpConn.ReadFrom(buf)
			if err != nil {
				return
			}
			resp := append([]byte("STREAM_ECHO:"), buf[:n]...)
			udpConn.WriteTo(resp, addr)
		}
	}()

	registry := include.OutboundRegistry()
	plugin.RegisterPlugins(registry)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	ctx = box.Context(ctx,
		include.InboundRegistry(),
		registry,
		include.EndpointRegistry(),
		include.DNSTransportRegistry(),
		include.ServiceRegistry(),
		include.CertificateProviderRegistry(),
	)
	ctx = service.ContextWithDefaultRegistry(ctx)

	cfg := fmt.Sprintf(`{
		"outbounds": [
			{
				"type": "shadowquic",
				"tag": "sq-stream-out",
				"server": "127.0.0.1",
				"server_port": 18443,
				"username": "testuser",
				"password": "testpassword",
				"server_name": "cloudflare.com",
				"alpn": ["h3"],
				"congestion_control": "bbr",
				"udp_over_stream": true,
				"zero_rtt_handshake": false
			}
		]
	}`)

	var options option.Options
	if err := options.UnmarshalJSONContext(ctx, []byte(cfg)); err != nil {
		t.Fatalf("UnmarshalJSON failed: %v", err)
	}

	instance, err := box.New(box.Options{
		Options: options,
		Context: ctx,
	})
	if err != nil {
		t.Fatalf("box.New failed: %v", err)
	}
	if err := instance.Start(); err != nil {
		t.Fatalf("instance.Start failed: %v", err)
	}
	defer instance.Close()

	sqOutbound, ok := instance.Outbound().Outbound("sq-stream-out")
	if !ok {
		t.Fatalf("outbound sq-stream-out not found")
	}

	listenCtx, listenCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer listenCancel()

	dstAddr := M.Socksaddr{Addr: netip.AddrFrom4([4]byte{127, 0, 0, 1}), Port: uint16(udpPort)}
	packetOutbound, ok := sqOutbound.(interface {
		ListenPacket(context.Context, M.Socksaddr) (net.PacketConn, error)
	})
	if !ok {
		t.Fatalf("sqOutbound does not implement ListenPacket")
	}
	packetConn, err := packetOutbound.ListenPacket(listenCtx, dstAddr)
	if err != nil {
		t.Fatalf("ListenPacket UDP failed: %v", err)
	}
	defer packetConn.Close()

	msg := "Hello Stream UDP"
	targetUDPAddr := &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1).To4(), Port: udpPort}
	if _, err := packetConn.WriteTo([]byte(msg), targetUDPAddr); err != nil {
		t.Fatalf("packetConn.WriteTo failed: %v", err)
	}

	packetConn.SetReadDeadline(time.Now().Add(5 * time.Second))
	reply := make([]byte, 2048)
	n, from, err := packetConn.ReadFrom(reply)
	if err != nil {
		t.Fatalf("packetConn.ReadFrom failed: %v", err)
	}
	expected := "STREAM_ECHO:" + msg
	if string(reply[:n]) != expected {
		t.Fatalf("unexpected reply: got %s, want %s (from %v)", string(reply[:n]), expected, from)
	}
	t.Logf("UDP_Over_Stream SUCCESS: received %s from %v", string(reply[:n]), from)
}
