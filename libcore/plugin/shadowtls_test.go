//go:build with_utls

package plugin_test

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"strings"
	"testing"
	"time"

	box "github.com/sagernet/sing-box"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-shadowsocks/shadowaead_2022"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/json/badoption"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/service"
)

func reserveShadowTLSPort(t *testing.T, network string) int {
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

func generateSelfSignedCert(t *testing.T) tls.Certificate {
	priv, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("generate ecdsa key: %v", err)
	}
	template := x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject: pkix.Name{
			CommonName:   "127.0.0.1",
			Organization: []string{"ShadowTLS Test"},
		},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(24 * time.Hour),
		KeyUsage:              x509.KeyUsageKeyEncipherment | x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
		IPAddresses:           []net.IP{net.ParseIP("127.0.0.1")},
		DNSNames:              []string{"localhost"},
	}
	derBytes, err := x509.CreateCertificate(rand.Reader, &template, &template, &priv.PublicKey, priv)
	if err != nil {
		t.Fatalf("create cert: %v", err)
	}
	return tls.Certificate{
		Certificate: [][]byte{derBytes},
		PrivateKey:  priv,
	}
}

func newSingBoxContext(ctx context.Context) context.Context {
	ctx = box.Context(ctx,
		include.InboundRegistry(),
		include.OutboundRegistry(),
		include.EndpointRegistry(),
		include.DNSTransportRegistry(),
		include.ServiceRegistry(),
		include.CertificateProviderRegistry(),
	)
	return service.ContextWithDefaultRegistry(ctx)
}

func startEchoServers(t *testing.T) (httpPort int, tcpPort int, udpPort int, stop func()) {
	// 1. HTTP Server
	httpListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen http: %v", err)
	}
	httpPort = httpListener.Addr().(*net.TCPAddr).Port

	mux := http.NewServeMux()
	mux.HandleFunc("/echo", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Protocol", "Shadowsocks-ShadowTLS")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("HELLO-FROM-SS-SHADOWTLS"))
	})
	httpServer := &http.Server{Handler: mux}
	go func() {
		_ = httpServer.Serve(httpListener)
	}()

	// 2. TCP Echo Server
	tcpListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen tcp: %v", err)
	}
	tcpPort = tcpListener.Addr().(*net.TCPAddr).Port

	tcpStop := make(chan struct{})
	go func() {
		for {
			conn, err := tcpListener.Accept()
			if err != nil {
				select {
				case <-tcpStop:
					return
				default:
					return
				}
			}
			go func(c net.Conn) {
				defer c.Close()
				buf := make([]byte, 1024)
				n, err := c.Read(buf)
				if err != nil {
					return
				}
				_, _ = c.Write([]byte("ECHO:" + string(buf[:n])))
			}(conn)
		}
	}()

	// 3. UDP Echo Server
	udpConn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen udp: %v", err)
	}
	udpPort = udpConn.LocalAddr().(*net.UDPAddr).Port

	udpStop := make(chan struct{})
	go func() {
		buf := make([]byte, 2048)
		for {
			n, addr, err := udpConn.ReadFrom(buf)
			if err != nil {
				select {
				case <-udpStop:
					return
				default:
					return
				}
			}
			resp := []byte("ECHO:" + string(buf[:n]))
			_, _ = udpConn.WriteTo(resp, addr)
		}
	}()

	stop = func() {
		_ = httpServer.Close()
		close(tcpStop)
		_ = tcpListener.Close()
		close(udpStop)
		_ = udpConn.Close()
	}

	return httpPort, tcpPort, udpPort, stop
}

func TestShadowsocksWithShadowTLS_LiveE2E(t *testing.T) {
	httpPort, tcpPort, udpPort, stopEcho := startEchoServers(t)
	defer stopEcho()

	stlsServerPort := reserveShadowTLSPort(t, "tcp")
	ssInboundPort := reserveShadowTLSPort(t, "tcp")
	mixedClientPort := reserveShadowTLSPort(t, "tcp")

	stlsPassword := "shadowtls-secret-pass-123"
	ssPasswordBytes := make([]byte, 16)
	_, _ = rand.Read(ssPasswordBytes)
	ssPassword := base64.StdEncoding.EncodeToString(ssPasswordBytes)
	ssMethod := shadowaead_2022.List[0] // 2022-blake3-aes-128-gcm

	// 1. Build Server sing-box instance
	// Inbound: ShadowTLS (tag: "stls-in", detour: "ss-in")
	// Inbound: Shadowsocks (tag: "ss-in")
	// Outbound: Direct
	// Route: Inbound "ss-in" -> "direct"
	serverOptions := option.Options{
		Inbounds: []option.Inbound{
			{
				Type: C.TypeShadowTLS,
				Tag:  "stls-in",
				Options: &option.ShadowTLSInboundOptions{
					ListenOptions: option.ListenOptions{
						Listen:     common.Ptr(badoption.Addr(netip.MustParseAddr("127.0.0.1"))),
						ListenPort: uint16(stlsServerPort),
						Detour:     "ss-in",
					},
					Handshake: option.ShadowTLSHandshakeOptions{
						ServerOptions: option.ServerOptions{
							Server:     "gateway.icloud.com",
							ServerPort: 443,
						},
					},
					Version:  3,
					Password: stlsPassword,
					Users: []option.ShadowTLSUser{
						{Password: stlsPassword},
					},
				},
			},
			{
				Type: C.TypeShadowsocks,
				Tag:  "ss-in",
				Options: &option.ShadowsocksInboundOptions{
					ListenOptions: option.ListenOptions{
						Listen:     common.Ptr(badoption.Addr(netip.MustParseAddr("127.0.0.1"))),
						ListenPort: uint16(ssInboundPort),
					},
					Method:   ssMethod,
					Password: ssPassword,
				},
			},
		},
		Outbounds: []option.Outbound{
			{
				Type: C.TypeDirect,
				Tag:  "direct",
			},
		},
		Route: &option.RouteOptions{
			Rules: []option.Rule{
				{
					Type: C.RuleTypeDefault,
					DefaultOptions: option.DefaultRule{
						RawDefaultRule: option.RawDefaultRule{
							Inbound: []string{"ss-in"},
						},
						RuleAction: option.RuleAction{
							Action: C.RuleActionTypeRoute,
							RouteOptions: option.RouteActionOptions{
								Outbound: "direct",
							},
						},
					},
				},
			},
		},
	}

	serverCtx, serverCancel := context.WithCancel(context.Background())
	defer serverCancel()
	serverCtx = newSingBoxContext(serverCtx)
	serverBox, err := box.New(box.Options{
		Context: serverCtx,
		Options: serverOptions,
	})
	if err != nil {
		t.Fatalf("create server sing-box: %v", err)
	}
	if err := serverBox.Start(); err != nil {
		t.Fatalf("start server sing-box: %v", err)
	}
	defer serverBox.Close()

	// 2. Build Client sing-box instance (mirroring NekoBox ConfigBuilder output)
	// Inbound: Mixed (tag: "mixed-in")
	// Outbound: Shadowsocks (tag: "ss-out", detour: "stls-out", UDPOverTCP: enabled)
	// Outbound: ShadowTLS (tag: "stls-out", server: 127.0.0.1:stlsServerPort, version: 3, tls: {enabled: true, server_name: "gateway.icloud.com", utls: {enabled: true, fingerprint: "chrome"}})
	clientOptions := option.Options{
		Inbounds: []option.Inbound{
			{
				Type: C.TypeMixed,
				Tag:  "mixed-in",
				Options: &option.HTTPMixedInboundOptions{
					ListenOptions: option.ListenOptions{
						Listen:     common.Ptr(badoption.Addr(netip.MustParseAddr("127.0.0.1"))),
						ListenPort: uint16(mixedClientPort),
					},
				},
			},
		},
		Outbounds: []option.Outbound{
			{
				Type: C.TypeShadowsocks,
				Tag:  "ss-out",
				Options: &option.ShadowsocksOutboundOptions{
					ServerOptions: option.ServerOptions{
						Server:     "127.0.0.1",
						ServerPort: uint16(stlsServerPort),
					},
					Method:   ssMethod,
					Password: ssPassword,
					UDPOverTCP: &option.UDPOverTCPOptions{
						Enabled: true,
						Version: 2,
					},
					DialerOptions: option.DialerOptions{
						Detour: "stls-out",
					},
				},
			},
			{
				Type: C.TypeShadowTLS,
				Tag:  "stls-out",
				Options: &option.ShadowTLSOutboundOptions{
					ServerOptions: option.ServerOptions{
						Server:     "127.0.0.1",
						ServerPort: uint16(stlsServerPort),
					},
					Version:  3,
					Password: stlsPassword,
					OutboundTLSOptionsContainer: option.OutboundTLSOptionsContainer{
						TLS: &option.OutboundTLSOptions{
							Enabled:    true,
							ServerName: "gateway.icloud.com",
							UTLS: &option.OutboundUTLSOptions{
								Enabled:     true,
								Fingerprint: "chrome",
							},
						},
					},
				},
			},
		},
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
								Outbound: "ss-out",
							},
						},
					},
				},
			},
		},
	}

	clientCtx, clientCancel := context.WithCancel(context.Background())
	defer clientCancel()
	clientCtx = newSingBoxContext(clientCtx)
	clientBox, err := box.New(box.Options{
		Context: clientCtx,
		Options: clientOptions,
	})
	if err != nil {
		t.Fatalf("create client sing-box: %v", err)
	}
	if err := clientBox.Start(); err != nil {
		t.Fatalf("start client sing-box: %v", err)
	}
	defer clientBox.Close()

	// 3. Test Mixed Inbound HTTP Proxy end-to-end
	proxyUrl, _ := url.Parse(fmt.Sprintf("http://127.0.0.1:%d", mixedClientPort))
	httpClient := &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyURL(proxyUrl),
		},
		Timeout: 5 * time.Second,
	}

	targetUrl := fmt.Sprintf("http://127.0.0.1:%d/echo", httpPort)
	resp, err := httpClient.Get(targetUrl)
	if err != nil {
		t.Fatalf("HTTP request through Mixed -> SS -> ShadowTLS failed: %v", err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected status 200, got %d", resp.StatusCode)
	}
	if string(body) != "HELLO-FROM-SS-SHADOWTLS" {
		t.Fatalf("unexpected body: %s", string(body))
	}
	if resp.Header.Get("X-Protocol") != "Shadowsocks-ShadowTLS" {
		t.Fatalf("unexpected X-Protocol header: %s", resp.Header.Get("X-Protocol"))
	}
	t.Logf("Mixed Inbound End-to-End Success: status=200, body=%s, proto=%s", string(body), resp.Header.Get("X-Protocol"))

	// 4. Test TCP Dial directly via Shadowsocks Outbound
	ssOutbound, loaded := clientBox.Outbound().Outbound("ss-out")
	if !loaded {
		t.Fatalf("ss-out not loaded")
	}

	tcpDst := M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", tcpPort))
	conn, err := ssOutbound.DialContext(context.Background(), "tcp", tcpDst)
	if err != nil {
		t.Fatalf("TCP dial via ss-out failed: %v", err)
	}
	defer conn.Close()

	testMsg := "ping-shadowtls-tcp"
	if _, err := conn.Write([]byte(testMsg)); err != nil {
		t.Fatalf("write to tcp conn failed: %v", err)
	}
	readBuf := make([]byte, 1024)
	n, err := conn.Read(readBuf)
	if err != nil {
		t.Fatalf("read from tcp conn failed: %v", err)
	}
	expected := "ECHO:" + testMsg
	if string(readBuf[:n]) != expected {
		t.Fatalf("expected %q, got %q", expected, string(readBuf[:n]))
	}
	t.Logf("TCP Direct Dial Success: received %s", string(readBuf[:n]))

	// 5. Test UDP Packet Dial via Shadowsocks Outbound (UoT over ShadowTLS)
	udpDst := M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", udpPort))
	packetConn, err := ssOutbound.ListenPacket(context.Background(), udpDst)
	if err != nil {
		t.Fatalf("ListenPacket via ss-out failed: %v", err)
	}
	defer packetConn.Close()

	udpMsg := []byte("ping-shadowtls-udp")
	if _, err := packetConn.WriteTo(udpMsg, udpDst.UDPAddr()); err != nil {
		t.Fatalf("WriteTo udp via ss-out (UoT) failed: %v", err)
	}
	udpReadBuf := make([]byte, 1024)
	_ = packetConn.SetReadDeadline(time.Now().Add(5 * time.Second))
	nUDP, _, err := packetConn.ReadFrom(udpReadBuf)
	if err != nil {
		t.Fatalf("ReadFrom udp via ss-out (UoT) failed: %v", err)
	}
	expectedUDP := "ECHO:ping-shadowtls-udp"
	if string(udpReadBuf[:nUDP]) != expectedUDP {
		t.Fatalf("expected UDP echo %q, got %q", expectedUDP, string(udpReadBuf[:nUDP]))
	}
	t.Logf("UDP Packet (UoT) Dial Success: received %s", string(udpReadBuf[:nUDP]))
}

func TestShadowTLS_FallbackProbeDefense(t *testing.T) {
	// Verify that an invalid ShadowTLS handshake triggers fallback, protecting the server.
	fallbackPort := reserveShadowTLSPort(t, "tcp")
	stlsPort := reserveShadowTLSPort(t, "tcp")

	// Start local TLS fallback server
	tlsCert := generateSelfSignedCert(t)
	tlsConfig := &tls.Config{
		Certificates: []tls.Certificate{tlsCert},
	}
	fallbackListener, err := tls.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", fallbackPort), tlsConfig)
	if err != nil {
		t.Fatalf("listen fallback tls: %v", err)
	}
	defer fallbackListener.Close()

	go func() {
		for {
			conn, err := fallbackListener.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				defer c.Close()
				buf := make([]byte, 1024)
				_, _ = c.Read(buf)
				_, _ = c.Write([]byte("HTTP/1.1 200 OK\r\nContent-Length: 16\r\n\r\nFALLBACK-DEFENSE"))
			}(conn)
		}
	}()

	// Start ShadowTLS server instance with Handshake server pointing to our fallback server
	serverOptions := option.Options{
		Inbounds: []option.Inbound{
			{
				Type: C.TypeShadowTLS,
				Tag:  "stls-in",
				Options: &option.ShadowTLSInboundOptions{
					ListenOptions: option.ListenOptions{
						Listen:     common.Ptr(badoption.Addr(netip.MustParseAddr("127.0.0.1"))),
						ListenPort: uint16(stlsPort),
						Detour:     "direct",
					},
					Handshake: option.ShadowTLSHandshakeOptions{
						ServerOptions: option.ServerOptions{
							Server:     "127.0.0.1",
							ServerPort: uint16(fallbackPort),
						},
					},
					Version:  3,
					Password: "real-strong-password",
					Users: []option.ShadowTLSUser{
						{Password: "real-strong-password"},
					},
				},
			},
		},
		Outbounds: []option.Outbound{
			{
				Type: C.TypeDirect,
				Tag:  "direct",
			},
		},
	}

	serverCtx, serverCancel := context.WithCancel(context.Background())
	defer serverCancel()
	serverCtx = newSingBoxContext(serverCtx)
	serverBox, err := box.New(box.Options{
		Context: serverCtx,
		Options: serverOptions,
	})
	if err != nil {
		t.Fatalf("create server: %v", err)
	}
	if err := serverBox.Start(); err != nil {
		t.Fatalf("start server: %v", err)
	}
	defer serverBox.Close()

	// Now act as an active prober (or standard TLS client with no knowledge of ShadowTLS password)
	proberTLSConfig := &tls.Config{
		InsecureSkipVerify: true,
	}
	proberConn, err := tls.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", stlsPort), proberTLSConfig)
	if err != nil {
		t.Fatalf("prober dial to ShadowTLS port failed: %v", err)
	}
	defer proberConn.Close()

	_, _ = proberConn.Write([]byte("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n"))
	respBuf := make([]byte, 1024)
	n, err := proberConn.Read(respBuf)
	if err != nil {
		t.Fatalf("prober read failed: %v", err)
	}
	if !strings.Contains(string(respBuf[:n]), "FALLBACK-DEFENSE") {
		t.Fatalf("expected fallback response, got: %s", string(respBuf[:n]))
	}
	t.Logf("Fallback Defense Success: unauthenticated probe cleanly forwarded to fallback TLS server: %s", string(respBuf[:n]))
}
