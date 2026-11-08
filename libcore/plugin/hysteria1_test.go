//go:build with_quic

package plugin_test

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"os"
	"path/filepath"
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

func generateHy1Cert(t *testing.T, domain string) (certPath, keyPath string) {
	priv, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate ecdsa key: %v", err)
	}

	template := x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject: pkix.Name{
			Organization: []string{"Hysteria1 Test"},
			CommonName:   domain,
		},
		NotBefore:             time.Now().Add(-1 * time.Hour),
		NotAfter:              time.Now().Add(24 * time.Hour),
		KeyUsage:              x509.KeyUsageKeyEncipherment | x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
		DNSNames:              []string{domain},
		IPAddresses:           []net.IP{net.ParseIP("127.0.0.1")},
	}

	certDer, err := x509.CreateCertificate(rand.Reader, &template, &template, &priv.PublicKey, priv)
	if err != nil {
		t.Fatalf("failed to create certificate: %v", err)
	}

	dir := t.TempDir()
	certPath = filepath.Join(dir, "cert.pem")
	keyPath = filepath.Join(dir, "key.pem")

	certFile, _ := os.Create(certPath)
	pem.Encode(certFile, &pem.Block{Type: "CERTIFICATE", Bytes: certDer})
	certFile.Close()

	keyBytes, _ := x509.MarshalECPrivateKey(priv)
	keyFile, _ := os.Create(keyPath)
	pem.Encode(keyFile, &pem.Block{Type: "EC PRIVATE KEY", Bytes: keyBytes})
	keyFile.Close()

	return certPath, keyPath
}

func reserveHy1Port(t *testing.T, network string) int {
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

func TestHysteria1LiveE2E(t *testing.T) {
	// 1. Generate TLS certificate for Hysteria 1
	certPath, keyPath := generateHy1Cert(t, "hy1.example.org")

	// 2. Setup local backend HTTP test server (TCP destination)
	httpListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to start local http server: %v", err)
	}
	defer httpListener.Close()
	httpPort := httpListener.Addr().(*net.TCPAddr).Port

	httpServer := &http.Server{
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			t.Logf("HTTP server received request: %s %s", r.Method, r.URL.Path)
			w.Header().Set("X-Protocol", "Hysteria1")
			fmt.Fprint(w, "Hello from Hysteria 1 backend")
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

	// 3. Setup local UDP echo server (UDP destination)
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

	// 4. Start Hysteria 1 Server instance
	hyServerPort := reserveHy1Port(t, "udp")
	loopback := badoption.Addr(netip.MustParseAddr("127.0.0.1"))

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
		Inbounds: []option.Inbound{{
			Type: C.TypeHysteria,
			Tag:  "hy1-in",
			Options: &option.HysteriaInboundOptions{
				ListenOptions: option.ListenOptions{
					Listen:     &loopback,
					ListenPort: uint16(hyServerPort),
				},
				UpMbps:   100,
				DownMbps: 100,
				Obfs:     "hy1-test-obfs",
				Users: []option.HysteriaUser{
					{
						AuthString: "hy1-test-auth",
					},
				},
				InboundTLSOptionsContainer: option.InboundTLSOptionsContainer{
					TLS: &option.InboundTLSOptions{
						Enabled:         true,
						ServerName:      "hy1.example.org",
						CertificatePath: certPath,
						KeyPath:         keyPath,
					},
				},
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
		t.Fatalf("failed to create hysteria 1 server: %v", err)
	}
	if err := serverBox.Start(); err != nil {
		t.Fatalf("failed to start hysteria 1 server: %v", err)
	}
	defer serverBox.Close()

	// 5. Start Hysteria 1 Client instance (with Mixed Inbound and Hysteria Outbound)
	clientPort := reserveHy1Port(t, "tcp")

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
					ListenPort: uint16(clientPort),
				},
			},
		}},
		Outbounds: []option.Outbound{
			{
				Type:    C.TypeDirect,
				Tag:     "direct",
				Options: &option.DirectOutboundOptions{},
			},
			{
				Type: C.TypeHysteria,
				Tag:  "hy1-out",
				Options: &option.HysteriaOutboundOptions{
					ServerOptions: option.ServerOptions{
						Server:     "127.0.0.1",
						ServerPort: uint16(hyServerPort),
					},
					UpMbps:     100,
					DownMbps:   100,
					AuthString: "hy1-test-auth",
					Obfs:       "hy1-test-obfs",
					OutboundTLSOptionsContainer: option.OutboundTLSOptionsContainer{
						TLS: &option.OutboundTLSOptions{
							Enabled:         true,
							ServerName:      "hy1.example.org",
							CertificatePath: certPath,
							Insecure:        true,
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
								Outbound: "hy1-out",
							},
						},
					},
				},
			},
			Final: "hy1-out",
		},
	}

	clientBox, err := box.New(box.Options{
		Options: clientOpts,
		Context: clientCtx,
	})
	if err != nil {
		t.Fatalf("failed to create hysteria 1 client: %v", err)
	}
	if err := clientBox.Start(); err != nil {
		t.Fatalf("failed to start hysteria 1 client: %v", err)
	}
	defer clientBox.Close()

	// Wait briefly for tunnel handshake readiness
	time.Sleep(300 * time.Millisecond)

	// 6. Test End-to-End via Client Mixed Inbound (HTTP proxy mode)
	proxyURL, err := url.Parse(fmt.Sprintf("http://127.0.0.1:%d", clientPort))
	if err != nil {
		t.Fatalf("parse mixed proxy url failed: %v", err)
	}

	httpClient := &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyURL(proxyURL),
		},
		Timeout: 5 * time.Second,
	}

	resp, err := httpClient.Get(fmt.Sprintf("http://127.0.0.1:%d", httpPort))
	if err != nil {
		t.Fatalf("HTTP request through client mixed inbound failed: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK via mixed proxy, got: %d", resp.StatusCode)
	}
	body, _ := io.ReadAll(resp.Body)
	if string(body) != "Hello from Hysteria 1 backend" {
		t.Fatalf("unexpected body via mixed inbound: %s", string(body))
	}
	if resp.Header.Get("X-Protocol") != "Hysteria1" {
		t.Fatalf("expected X-Protocol header 'Hysteria1', got: %s", resp.Header.Get("X-Protocol"))
	}
	t.Logf("Mixed Inbound End-to-End Success: verified full proxy path through sing-box Hysteria 1 tunnel")

	// 7. Test direct Outbound DialContext for TCP (Echo test)
	outboundMgr := clientBox.Outbound()
	hyOutbound, ok := outboundMgr.Outbound("hy1-out")
	if !ok {
		t.Fatalf("hy1-out outbound not found in client")
	}

	destTCP := M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", tcpEchoPort))
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	tcpConn, err := hyOutbound.DialContext(ctx, "tcp", destTCP)
	if err != nil {
		t.Fatalf("hy1 outbound DialContext TCP failed: %v", err)
	}
	defer tcpConn.Close()

	if _, err := tcpConn.Write([]byte("ping-tcp-hy1")); err != nil {
		t.Fatalf("write to tcpConn failed: %v", err)
	}
	replyBuf := make([]byte, 1024)
	nTCP, err := tcpConn.Read(replyBuf)
	if err != nil {
		t.Fatalf("read from tcpConn failed: %v", err)
	}
	expectedTCP := "ECHO:ping-tcp-hy1"
	if string(replyBuf[:nTCP]) != expectedTCP {
		t.Fatalf("unexpected TCP echo response: got %q, want %q", string(replyBuf[:nTCP]), expectedTCP)
	}
	t.Logf("TCP Direct Dial Success: received expected echo response %s", string(replyBuf[:nTCP]))

	// 8. Test direct Outbound DialContext for UDP (Echo test)

	destUDP := M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", udpPort))
	udpCtx, udpCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer udpCancel()

	udpClientConn, err := hyOutbound.DialContext(udpCtx, "udp", destUDP)
	if err != nil {
		t.Fatalf("hy1 outbound DialContext UDP failed: %v", err)
	}
	defer udpClientConn.Close()

	udpPayload := []byte("ping-hysteria1-live-data")
	_ = udpClientConn.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err := udpClientConn.Write(udpPayload); err != nil {
		t.Fatalf("write UDP packet through hy1 outbound failed: %v", err)
	}

	udpBuf := make([]byte, 1024)
	n, err := udpClientConn.Read(udpBuf)
	if err != nil {
		t.Fatalf("read UDP response through hy1 outbound failed: %v", err)
	}
	expectedResp := "ECHO:ping-hysteria1-live-data"
	if string(udpBuf[:n]) != expectedResp {
		t.Fatalf("unexpected UDP echo response: got %q, want %q", string(udpBuf[:n]), expectedResp)
	}
	t.Logf("UDP Direct Dial Success: received expected echo response %s", string(udpBuf[:n]))
}

func TestHysteria1AuthBytes(t *testing.T) {
	certPath, keyPath := generateHy1Cert(t, "hy1-authbytes.example.org")

	// Local TCP echo server
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

	hyServerPort := reserveHy1Port(t, "udp")
	loopback := badoption.Addr(netip.MustParseAddr("127.0.0.1"))

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

	authBytes := []byte("binary-secret-key-12345")

	serverOpts := option.Options{
		Inbounds: []option.Inbound{{
			Type: C.TypeHysteria,
			Tag:  "hy1-in",
			Options: &option.HysteriaInboundOptions{
				ListenOptions: option.ListenOptions{
					Listen:     &loopback,
					ListenPort: uint16(hyServerPort),
				},
				UpMbps:   100,
				DownMbps: 100,
				Obfs:     "hy1-custom-obfs",
				Users: []option.HysteriaUser{
					{
						Auth: authBytes,
					},
				},
				InboundTLSOptionsContainer: option.InboundTLSOptionsContainer{
					TLS: &option.InboundTLSOptions{
						Enabled:         true,
						ServerName:      "hy1-authbytes.example.org",
						CertificatePath: certPath,
						KeyPath:         keyPath,
					},
				},
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
		t.Fatalf("failed to create server: %v", err)
	}
	if err := serverBox.Start(); err != nil {
		t.Fatalf("failed to start server: %v", err)
	}
	defer serverBox.Close()

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
		Outbounds: []option.Outbound{
			{
				Type: C.TypeHysteria,
				Tag:  "hy1-out",
				Options: &option.HysteriaOutboundOptions{
					ServerOptions: option.ServerOptions{
						Server:     "127.0.0.1",
						ServerPort: uint16(hyServerPort),
					},
					UpMbps:   100,
					DownMbps: 100,
					Auth:     authBytes,
					Obfs:     "hy1-custom-obfs",
					OutboundTLSOptionsContainer: option.OutboundTLSOptionsContainer{
						TLS: &option.OutboundTLSOptions{
							Enabled:         true,
							ServerName:      "hy1-authbytes.example.org",
							CertificatePath: certPath,
							Insecure:        true,
						},
					},
				},
			},
		},
	}

	clientBox, err := box.New(box.Options{
		Options: clientOpts,
		Context: clientCtx,
	})
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}
	if err := clientBox.Start(); err != nil {
		t.Fatalf("failed to start client: %v", err)
	}
	defer clientBox.Close()

	time.Sleep(200 * time.Millisecond)

	hyOutbound, ok := clientBox.Outbound().Outbound("hy1-out")
	if !ok {
		t.Fatalf("hy1-out not found")
	}

	destTCP := M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", tcpEchoPort))
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	tcpConn, err := hyOutbound.DialContext(ctx, "tcp", destTCP)
	if err != nil {
		t.Fatalf("hy1 authBytes DialContext TCP failed: %v", err)
	}
	defer tcpConn.Close()

	if _, err := tcpConn.Write([]byte("ping-authbytes")); err != nil {
		t.Fatalf("write to tcpConn failed: %v", err)
	}
	replyBuf := make([]byte, 1024)
	nTCP, err := tcpConn.Read(replyBuf)
	if err != nil {
		t.Fatalf("read from tcpConn failed: %v", err)
	}
	expectedTCP := "ECHO:ping-authbytes"
	if string(replyBuf[:nTCP]) != expectedTCP {
		t.Fatalf("unexpected TCP echo response: got %q, want %q", string(replyBuf[:nTCP]), expectedTCP)
	}
	t.Logf("AuthBytes Test Success: received %s", string(replyBuf[:nTCP]))
}
