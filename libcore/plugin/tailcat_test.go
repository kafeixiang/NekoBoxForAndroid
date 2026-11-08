//go:build with_tailscale

package plugin_test

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
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
	"github.com/sagernet/sing-box/protocol/tailscale"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/json/badoption"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/service"

	"libcore/plugin"
)

func generateSelfSignedCert(t *testing.T, domain string) (certPath, keyPath string, certRaw []byte) {
	priv, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("failed to generate ecdsa key: %v", err)
	}

	template := x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject: pkix.Name{
			Organization: []string{"Tailcat Test"},
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

	return certPath, keyPath, certDer
}

func reservePort(t *testing.T, network string) int {
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

func TestTailcatLiveE2E(t *testing.T) {
	loopback := netip.MustParseAddr("127.0.0.1")
	derpPort := reservePort(t, "tcp")
	stunPort := reservePort(t, "udp")
	mixedPort := reservePort(t, "tcp")

	certPath, keyPath, certDer := generateSelfSignedCert(t, "localhost")
	certHash := sha256.Sum256(certDer)
	certName := "sha256-raw:" + hex.EncodeToString(certHash[:])

	serverKey := tailscale.NewTailcatPrivateKey()
	clientKey := tailscale.NewTailcatPrivateKey()
	var presharedKey [32]byte
	_, _ = rand.Read(presharedKey[:])

	derpServers := []option.TailcatDERPServer{{
		Host:     "localhost",
		IPv4:     loopback.String(),
		IPv6:     "none",
		DERPPort: uint16(derpPort),
		STUNPort: uint16(stunPort),
		CertName: certName,
	}}

	// 1. Setup local backend HTTP server for testing TCP
	httpListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to start local http server: %v", err)
	}
	defer httpListener.Close()
	httpPort := httpListener.Addr().(*net.TCPAddr).Port

	httpServer := &http.Server{
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("X-Protocol", "Tailcat")
			fmt.Fprint(w, "Hello from Tailcat backend")
		}),
	}
	go httpServer.Serve(httpListener)
	defer httpServer.Close()

	// 2. Setup local backend UDP echo server
	udpConn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to start local udp server: %v", err)
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

	// 3. Build & start Server instance (DERP + Tailcat-In + Direct Out)
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
		Services: []option.Service{{
			Type: C.TypeDERP,
			Tag:  "derp",
			Options: &option.DERPServiceOptions{
				ListenOptions: option.ListenOptions{
					Listen:     (*badoption.Addr)(common.Ptr(loopback)),
					ListenPort: uint16(derpPort),
				},
				InboundTLSOptionsContainer: option.InboundTLSOptionsContainer{
					TLS: &option.InboundTLSOptions{
						Enabled:         true,
						CertificatePath: certPath,
						KeyPath:         keyPath,
					},
				},
				ConfigPath:          filepath.Join(t.TempDir(), "derp.key"),
				VerifyClientInbound: []string{"tailcat-in"},
				STUN: &option.DERPSTUNListenOptions{
					Enabled: true,
					ListenOptions: option.ListenOptions{
						Listen:     (*badoption.Addr)(common.Ptr(loopback)),
						ListenPort: uint16(stunPort),
					},
				},
			},
		}},
		Inbounds: []option.Inbound{{
			Type: C.TypeTailcat,
			Tag:  "tailcat-in",
			Options: &option.TailcatInboundOptions{
				PrivateKey:   tailscale.EncodeTailcatKey(serverKey),
				PreSharedKey: tailscale.EncodeTailcatKey(presharedKey),
				Users: []option.TailcatUser{{
					Name:      "client",
					PublicKey: tailscale.EncodeTailcatKey(tailscale.TailcatPublicKey(clientKey)),
				}},
				TailcatDERPOptions: option.TailcatDERPOptions{DERPServers: derpServers},
			},
		}},
		Outbounds: []option.Outbound{{Type: C.TypeDirect, Tag: "direct", Options: &option.DirectOutboundOptions{}}},
		Route:     &option.RouteOptions{Final: "direct"},
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

	// 4. Build & start Client instance (Mixed In + Tailcat Out)
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
			Type: "mixed",
			Tag:  "mixed-in",
			Options: &option.HTTPMixedInboundOptions{
				ListenOptions: option.ListenOptions{
					Listen:     (*badoption.Addr)(common.Ptr(loopback)),
					ListenPort: uint16(mixedPort),
				},
			},
		}},
		Outbounds: []option.Outbound{{
			Type: C.TypeTailcat,
			Tag:  "tailcat-out",
			Options: &option.TailcatOutboundOptions{
				PrivateKey:         tailscale.EncodeTailcatKey(clientKey),
				ServerPublicKey:    tailscale.EncodeTailcatKey(tailscale.TailcatPublicKey(serverKey)),
				ServerDiscoKey:     tailscale.EncodeTailcatKey(tailscale.TailcatPublicKey(tailscale.TailcatDiscoPrivateKey(serverKey))),
				PreSharedKey:       tailscale.EncodeTailcatKey(presharedKey),
				TailcatDERPOptions: option.TailcatDERPOptions{DERPServers: derpServers},
			},
		}},
		Route: &option.RouteOptions{Final: "tailcat-out"},
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

	time.Sleep(500 * time.Millisecond)

	tailcatOutbound, ok := clientBox.Outbound().Outbound("tailcat-out")
	if !ok {
		t.Fatalf("tailcat-out outbound not found")
	}

	// 5. Test Direct Outbound TCP DialContext
	t.Run("TCP_Direct_Outbound", func(t *testing.T) {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		conn, err := tailcatOutbound.DialContext(ctx, "tcp", M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", httpPort)))
		if err != nil {
			t.Fatalf("DialContext TCP failed: %v", err)
		}
		defer conn.Close()

		req := fmt.Sprintf("GET /test HTTP/1.1\r\nHost: 127.0.0.1:%d\r\nConnection: close\r\n\r\n", httpPort)
		if _, err := conn.Write([]byte(req)); err != nil {
			t.Fatalf("conn.Write failed: %v", err)
		}

		resp, err := io.ReadAll(conn)
		if err != nil {
			t.Fatalf("conn.Read failed: %v", err)
		}
		expected := "Hello from Tailcat backend"
		if !contains(string(resp), expected) {
			t.Fatalf("unexpected body: got %s, want to contain %s", string(resp), expected)
		}
		t.Logf("TCP_Direct_Outbound SUCCESS: %s", string(resp))
	})

	// 6. Test Direct Outbound UDP Packet DialContext
	t.Run("UDP_Direct_Outbound", func(t *testing.T) {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		dstAddr := M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", udpPort))
		conn, err := tailcatOutbound.DialContext(ctx, "udp", dstAddr)
		if err != nil {
			t.Fatalf("DialContext UDP failed: %v", err)
		}
		defer conn.Close()

		msg := "Hello Tailcat UDP"
		if _, err := conn.Write([]byte(msg)); err != nil {
			t.Fatalf("conn.Write failed: %v", err)
		}

		reply := make([]byte, 2048)
		conn.SetReadDeadline(time.Now().Add(5 * time.Second))
		n, err := conn.Read(reply)
		if err != nil {
			t.Fatalf("conn.Read failed: %v", err)
		}
		expected := "ECHO:" + msg
		if string(reply[:n]) != expected {
			t.Fatalf("unexpected UDP reply: got %s, want %s", string(reply[:n]), expected)
		}
		t.Logf("UDP_Direct_Outbound SUCCESS: %s", string(reply[:n]))
	})

	// 7. Test End-to-End HTTP through Client Mixed Inbound
	t.Run("HTTP_Via_Mixed_Inbound", func(t *testing.T) {
		proxyURL, err := url.Parse(fmt.Sprintf("http://127.0.0.1:%d", mixedPort))
		if err != nil {
			t.Fatalf("url.Parse failed: %v", err)
		}
		client := &http.Client{
			Transport: &http.Transport{
				Proxy: http.ProxyURL(proxyURL),
			},
			Timeout: 10 * time.Second,
		}

		resp, err := client.Get(fmt.Sprintf("http://127.0.0.1:%d/test", httpPort))
		if err != nil {
			t.Fatalf("HTTP GET via mixed-in failed: %v", err)
		}
		defer resp.Body.Close()

		body, err := io.ReadAll(resp.Body)
		if err != nil {
			t.Fatalf("ReadAll failed: %v", err)
		}

		expected := "Hello from Tailcat backend"
		if string(body) != expected {
			t.Fatalf("unexpected HTTP body: got %s, want %s", string(body), expected)
		}
		t.Logf("HTTP_Via_Mixed_Inbound SUCCESS: %s", string(body))
	})
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(substr) == 0 || (len(s) > 0 && len(substr) > 0 && commonSubstr(s, substr)))
}

func commonSubstr(s, substr string) bool {
	for i := 0; i+len(substr) <= len(s); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
