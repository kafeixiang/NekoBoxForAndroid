//go:build with_wireguard

package plugin_test

import (
	"context"
	"crypto/ecdh"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"testing"
	"time"

	box "github.com/sagernet/sing-box"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json/badoption"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/service"

	"libcore/plugin"
)

func generateWgKey(t *testing.T) (privBase64, pubBase64 string) {
	key, err := ecdh.X25519().GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate wg key: %v", err)
	}
	priv := base64.StdEncoding.EncodeToString(key.Bytes())
	pub := base64.StdEncoding.EncodeToString(key.PublicKey().Bytes())
	return priv, pub
}

func reserveWgPort(t *testing.T, network string) int {
	if network == "tcp" {
		l, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			t.Fatalf("reserve tcp port: %v", err)
		}
		port := l.Addr().(*net.TCPAddr).Port
		l.Close()
		return port
	}
	l, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("reserve udp port: %v", err)
	}
	port := l.LocalAddr().(*net.UDPAddr).Port
	l.Close()
	return port
}

func TestWireGuardLiveE2E(t *testing.T) {
	// 1. Generate WireGuard keys for server and client
	serverPriv, serverPub := generateWgKey(t)
	clientPriv, clientPub := generateWgKey(t)

	// Pre-shared key for extra security verification
	pskBytes := make([]byte, 32)
	rand.Read(pskBytes)
	presharedKey := base64.StdEncoding.EncodeToString(pskBytes)

	// 2. Setup local HTTP backend server
	httpListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to start local http server: %v", err)
	}
	defer httpListener.Close()
	httpPort := httpListener.Addr().(*net.TCPAddr).Port

	httpServer := &http.Server{
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("X-Protocol", "WireGuard")
			fmt.Fprint(w, "Hello from WireGuard backend")
		}),
	}
	go httpServer.Serve(httpListener)
	defer httpServer.Close()

	// 2b. Setup local TCP echo server
	tcpEchoListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to start local tcp echo server: %v", err)
	}
	defer tcpEchoListener.Close()
	tcpEchoPort := tcpEchoListener.Addr().(*net.TCPAddr).Port

	go func() {
		for {
			conn, err := tcpEchoListener.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				defer c.Close()
				buf := make([]byte, 1024)
				for {
					n, err := c.Read(buf)
					if err != nil {
						return
					}
					if _, err := c.Write(append([]byte("ECHO:"), buf[:n]...)); err != nil {
						return
					}
				}
			}(conn)
		}
	}()

	// 2c. Setup local UDP echo server
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

	// 3. Start WireGuard Server Instance
	serverListenPort := reserveWgPort(t, "udp")
	serverAddresses := []netip.Prefix{netip.MustParsePrefix("10.0.0.1/32")}
	clientAddresses := []netip.Prefix{netip.MustParsePrefix("10.0.0.2/32")}

	registry := include.OutboundRegistry()
	plugin.RegisterPlugins(registry)

	serverCtx, serverCancel := context.WithCancel(context.Background())
	defer serverCancel()
	serverCtx = box.Context(serverCtx,
		include.InboundRegistry(),
		registry,
		include.EndpointRegistry(),
		include.DNSTransportRegistry(),
		include.ServiceRegistry(),
		include.CertificateProviderRegistry(),
	)
	serverCtx = service.ContextWithDefaultRegistry(serverCtx)

	serverOpts := option.Options{
		Endpoints: []option.Endpoint{{
			Type: C.TypeWireGuard,
			Tag:  "wg-server",
			Options: &option.WireGuardEndpointOptions{
				MTU:        1420,
				Address:    serverAddresses,
				Workers:    2,
				PrivateKey: serverPriv,
				ListenPort: uint16(serverListenPort),
				Peers: []option.WireGuardPeer{{
					PublicKey:    clientPub,
					PreSharedKey: presharedKey,
					AllowedIPs:   clientAddresses,
					Reserved:     []uint8{1, 2, 3},
				}},
			},
		}},
		Outbounds: []option.Outbound{{
			Type:    C.TypeDirect,
			Tag:     "direct",
			Options: &option.DirectOutboundOptions{},
		}},
		Route: &option.RouteOptions{
			Final: "direct",
		},
	}

	serverBox, err := box.New(box.Options{
		Options: serverOpts,
		Context: serverCtx,
	})
	if err != nil {
		t.Fatalf("failed to create wireguard server: %v", err)
	}
	if err := serverBox.Start(); err != nil {
		t.Fatalf("failed to start wireguard server: %v", err)
	}
	defer serverBox.Close()

	// 4. Start WireGuard Client Instance (with Mixed Inbound and WireGuard Endpoint)
	clientMixedPort := reserveWgPort(t, "tcp")
	loopback := badoption.Addr(netip.MustParseAddr("127.0.0.1"))

	clientCtx, clientCancel := context.WithCancel(context.Background())
	defer clientCancel()
	clientCtx = box.Context(clientCtx,
		include.InboundRegistry(),
		registry,
		include.EndpointRegistry(),
		include.DNSTransportRegistry(),
		include.ServiceRegistry(),
		include.CertificateProviderRegistry(),
	)
	clientCtx = service.ContextWithDefaultRegistry(clientCtx)

	clientOpts := option.Options{
		Inbounds: []option.Inbound{{
			Type: C.TypeMixed,
			Tag:  "mixed-in",
			Options: &option.HTTPMixedInboundOptions{
				ListenOptions: option.ListenOptions{
					Listen:     &loopback,
					ListenPort: uint16(clientMixedPort),
				},
			},
		}},
		Endpoints: []option.Endpoint{{
			Type: C.TypeWireGuard,
			Tag:  "wg-client",
			Options: &option.WireGuardEndpointOptions{
				MTU:        1420,
				Address:    clientAddresses,
				Workers:    2,
				PrivateKey: clientPriv,
				Peers: []option.WireGuardPeer{{
					Address:      "127.0.0.1",
					Port:         uint16(serverListenPort),
					PublicKey:    serverPub,
					PreSharedKey: presharedKey,
					AllowedIPs:   []netip.Prefix{netip.MustParsePrefix("0.0.0.0/0"), netip.MustParsePrefix("::/0")},
					Reserved:     []uint8{1, 2, 3},
				}},
			},
		}},
		Outbounds: []option.Outbound{{
			Type:    C.TypeDirect,
			Tag:     "direct",
			Options: &option.DirectOutboundOptions{},
		}},
		Route: &option.RouteOptions{
			Rules: []option.Rule{
				{
					Type: C.RuleTypeDefault,
					DefaultOptions: option.DefaultRule{
						RawDefaultRule: option.RawDefaultRule{
							Inbound: []string{"mixed-in"},
						},
						RuleAction: option.RuleAction{
							Action: C.RuleActionTypeRoute,
							RouteOptions: option.RouteActionOptions{
								Outbound: "wg-client",
							},
						},
					},
				},
			},
			Final: "wg-client",
		},
	}

	clientBox, err := box.New(box.Options{
		Options: clientOpts,
		Context: clientCtx,
	})
	if err != nil {
		t.Fatalf("failed to create wireguard client: %v", err)
	}
	if err := clientBox.Start(); err != nil {
		t.Fatalf("failed to start wireguard client: %v", err)
	}
	defer clientBox.Close()

	// Wait briefly for WireGuard handshake
	time.Sleep(300 * time.Millisecond)

	// 5. Test End-to-End via Client Mixed Inbound (HTTP proxy mode through WireGuard tunnel)
	proxyURL, err := url.Parse(fmt.Sprintf("http://127.0.0.1:%d", clientMixedPort))
	if err != nil {
		t.Fatalf("parse mixed proxy url failed: %v", err)
	}

	httpClient := &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyURL(proxyURL),
		},
		Timeout: 5 * time.Second,
	}

	// Requesting http://10.0.0.1:<httpPort> through the WireGuard client proxy
	resp, err := httpClient.Get(fmt.Sprintf("http://10.0.0.1:%d/", httpPort))
	if err != nil {
		t.Fatalf("HTTP request through wireguard proxy failed: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK via wireguard proxy, got: %d", resp.StatusCode)
	}
	body, _ := io.ReadAll(resp.Body)
	if string(body) != "Hello from WireGuard backend" {
		t.Fatalf("unexpected body via wireguard: %s", string(body))
	}
	if resp.Header.Get("X-Protocol") != "WireGuard" {
		t.Fatalf("expected X-Protocol header 'WireGuard', got: %s", resp.Header.Get("X-Protocol"))
	}
	t.Logf("Mixed Inbound End-to-End Success: verified full proxy path through WireGuard tunnel")

	// 6. Test Direct TCP DialContext on WireGuard Endpoint
	endpoint, loaded := clientBox.Endpoint().Get("wg-client")
	if !loaded {
		t.Fatalf("wg-client endpoint not found")
	}

	destTCP := M.ParseSocksaddr(fmt.Sprintf("10.0.0.1:%d", tcpEchoPort))
	dialCtx, dialCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer dialCancel()

	tcpConn, err := endpoint.DialContext(dialCtx, "tcp", destTCP)
	if err != nil {
		t.Fatalf("wireguard endpoint DialContext TCP failed: %v", err)
	}
	defer tcpConn.Close()

	if _, err := tcpConn.Write([]byte("ping-wg-tcp")); err != nil {
		t.Fatalf("write to wg tcpConn failed: %v", err)
	}
	replyBuf := make([]byte, 1024)
	nTCP, err := tcpConn.Read(replyBuf)
	if err != nil {
		t.Fatalf("read from wg tcpConn failed: %v", err)
	}
	expectedTCP := "ECHO:ping-wg-tcp"
	if string(replyBuf[:nTCP]) != expectedTCP {
		t.Fatalf("unexpected TCP echo response: got %q, want %q", string(replyBuf[:nTCP]), expectedTCP)
	}
	t.Logf("TCP Direct Dial Success: received expected echo response %s", string(replyBuf[:nTCP]))

	// 7. Test Direct UDP ListenPacket on WireGuard Endpoint
	destUDP := M.ParseSocksaddr(fmt.Sprintf("10.0.0.1:%d", udpPort))
	udpClientConn, err := endpoint.DialContext(dialCtx, "udp", destUDP)
	if err != nil {
		t.Fatalf("wireguard endpoint DialContext UDP failed: %v", err)
	}
	defer udpClientConn.Close()

	udpPayload := []byte("ping-wg-udp")
	_ = udpClientConn.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err := udpClientConn.Write(udpPayload); err != nil {
		t.Fatalf("write UDP packet through wg failed: %v", err)
	}

	udpBuf := make([]byte, 1024)
	nUDP, err := udpClientConn.Read(udpBuf)
	if err != nil {
		t.Fatalf("read UDP response through wg failed: %v", err)
	}
	expectedUDP := "ECHO:ping-wg-udp"
	if string(udpBuf[:nUDP]) != expectedUDP {
		t.Fatalf("unexpected UDP echo response: got %q, want %q", string(udpBuf[:nUDP]), expectedUDP)
	}
	t.Logf("UDP Direct Dial Success: received expected echo response %s", string(udpBuf[:nUDP]))
}
