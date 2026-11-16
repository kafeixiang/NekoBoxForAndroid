package plugin_test

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/service"
	"github.com/stretchr/testify/require"

	"libcore/plugin"
)

const (
	testCert = `-----BEGIN CERTIFICATE-----
MIIDZTCCAk2gAwIBAgIQBIBNupbq+KyHd0S1pzXEQDANBgkqhkiG9w0BAQsFADAR
MQ8wDQYDVQQDDAZmcmVnaWUwIBcNMjExMDE1MDI0NjU5WhgPMjEyMTA5MjEwMjQ2
NTlaMBQxEjAQBgNVBAMMCWxvY2FsaG9zdDCCASIwDQYJKoZIhvcNAQEBBQADggEP
ADCCAQoCggEBAK274VfScpxWVf1Ulqpn8kY00cnB21fDjSr+t5SHEC8kzii7s0Wv
goGRLKGN0e7ok5Ufvc6vdgl0LCMYHii0a0xiRRKFmy5eTpcFaIa+RqRUt696afMh
q4qalB9pIx8PRfME8VhVwF9p9hRpcQOiXT1vV4JJqNZH5QIVC6n1pxDToTEKojYl
dBAYixniZFSGqmu6tki3s5dUnTyyIkdeoIUw+GFZfBY531Yh4dLge/OLIsf8ei2L
dV17Yp+8xgEzs6AAX2BSoj7C+G9cOAJOabcKPs7e+BQmqg8JWlQL+5wt0Nuu049O
uB/f9aJNZEvkuEyM3ZX1LLyLqwvLXuzABS8CAwEAAaOBszCBsDAJBgNVHRMEAjAA
MB0GA1UdDgQWBBQrj8OKLxmucf8DDW5bpCRV1dop3TBMBgNVHSMERTBDgBTJJdOJ
89Rd2u+plS2ikWTd1JAcu6EVpBMwETEPMA0GA1UEAwwGZnJlZ2llghREgytcTE5Q
JCUuH9oYchyboT+kXDATBgNVHSUEDDAKBggrBgEFBQcDATALBgNVHQ8EBAMCBaAw
FAYDVR0RBA0wC4IJbG9jYWxob3N0MA0GCSqGSIb3DQEBCwUAA4IBAQBsGS0N1yCc
kBUaFMJ8CgiNQgLFoVJkawcT38//R+ii8/6Azt0JcTRzkcsm8Fut0ZrgilnlIGBI
3Y8dVNKDnDwlsANe/YFtIVJ7S/inK3SdZYyLec0VosTWT+T+NZLlI2ESLcSooVL6
+8cQAOcd6MArGHzOVam/pLTyatbOHv8ZFoNBT7zAEvUZJ7ZxwANLyW2XkiqC0Dlr
X/N2PHKY9Az94T4EhWJopiZA09HNAcQckAnAbHRoMulT94hxqyzV8RYDzHA9Z2TN
IfgNuFIhVyQqBp8Hc0/c8T0rBTYlbSPvggywQ9w5xQaoUePm31J/4jPSiFCci681
KnkNNFtbgo96
-----END CERTIFICATE-----`

	testKey = `-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCtu+FX0nKcVlX9
VJaqZ/JGNNHJwdtXw40q/reUhxAvJM4ou7NFr4KBkSyhjdHu6JOVH73Or3YJdCwj
GB4otGtMYkUShZsuXk6XBWiGvkakVLevemnzIauKmpQfaSMfD0XzBPFYVcBfafYU
aXEDol09b1eCSajWR+UCFQup9acQ06ExCqI2JXQQGIsZ4mRUhqprurZIt7OXVJ08
siJHXqCFMPhhWXwWOd9WIeHS4HvziyLH/Hoti3Vde2KfvMYBM7OgAF9gUqI+wvhv
XDgCTmm3Cj7O3vgUJqoPCVpUC/ucLdDbrtOPTrgf3/WiTWRL5LhMjN2V9Sy8i6sL
y17swAUvAgMBAAECggEAQtfZoI+AtzPki76C5Xdu2KIz4RtsB/1eEB/GhCffCzRu
+W8WT4ZygOVZNaM6FWB4f9Shk6cglAyVer8pw2F/MvlQOAsdpJ52QFa9W7JTvaA2
uBYyM3BN7tsAiIFMGQQoVpMdRG5hwJQlML9M0ygiFaQEGEW85wzsSHvObArux2Lz
A9+YSOLdGWjW8UPGoIaWvPYp0/apwB9WQAfrxeHktbrAAv3293z1qq4BfP/t5mEm
vzOZiLRNlyjo6wj2kdB2UyF9SIUGWu2cMSTl8AiW+idLpursF1cSPaPtJAQyV10P
W1Y4ZESOGgXexbVNQJ6U8YTWCXiXkaDdGI3B9eyPcQKBgQDVR+bm6EeOks+91lfL
L94T3M6hCc9Cvz1Bxif1Ahl1rp0FN2102P/Ovf3ArpYMFNl0liwB2QehBGvgmNw4
IowxqNJKYUwqr+CMZVB0DHpc1p9avI7ISEt8hI6wiQjvvhLhtqqwKw9zxCgLaVD4
JaNN/a5BPyT0CfwqVKGZc5jaGQKBgQDQiC901vJlFekMWgCDOwsEB3S1t8K0xyVA
pWSFw25MzYHE3sBxdk5T+KAcxBHL0dSAvMfiMGnlguo/LAbs2bMR9xnFvoWpQYqp
NjBddZ1l9XtKGpeMwwWMn6+5MfoeVv068MZU2bwMyWiIfKm/4SNBjd6GGgM/m/do
f0D5prFShwKBgENAVd2vloitYFYS1UIbiOIoePssda8tUCqCSi0miVKjSZ3QeXIj
zp2pKO9t3aBXnq31gFDg11f9Zeq/KImG00ABZLXRckvvg1WhyLmfCaJmhn78/+TF
bxAHWFrwCuJVw+xqRe4g1dGCI8JdZgupCpJYEG9Acu2EjH/oicAyRBtZAoGAF+l1
36UCOJsxlQwBAQtVMQoV1PyUZBxt5iLRCxGk5UOvfL66PIh/ZNueqI3HKKMQBg8f
sI0yp3HCoKnQxXoVMZsvJmC5fPYaC1s+YokpGlby40V3WVnHmh95i/fyIWaCNS8E
3xf0m1bBGN2KrYkIfOzitmfnNXUSAraM4dO+g6MCgYEAgk5D3ulLR7scj4MK0jLs
hnUxHrgchGCAsREHDBo+GH6yBmyxyO35Zb4fDJEX4Yc8Enu8UHGhG7++lwGMZqY9
clPb/1SkuEWD+nCLUnkA5KyqPskF2GFjT8be4TBDFtQHBydxZFDZHxMhn4JShCTT
xiGQ1YfbqPMbovNUt1m0Es8=
-----END PRIVATE KEY-----`
)

func TestTrojanGo_LiveE2E(t *testing.T) {
	trojanGoBin := ""
	for _, c := range []string{"/opt/homebrew/bin/trojan-go", "trojan-go"} {
		if path, err := exec.LookPath(c); err == nil {
			trojanGoBin = path
			break
		}
	}
	if trojanGoBin == "" {
		t.Skip("trojan-go binary not found, skipping TestTrojanGo_LiveE2E")
	}

	// 1. Start target HTTP server
	httpTargetListener, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(t, err)
	defer httpTargetListener.Close()
	httpTargetPort := httpTargetListener.Addr().(*net.TCPAddr).Port

	httpServer := &http.Server{
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "text/plain")
			_, _ = w.Write([]byte("HELLO-FROM-TROJAN-GO"))
		}),
	}
	go func() { _ = httpServer.Serve(httpTargetListener) }()
	defer httpServer.Close()

	// 2. Start target UDP server
	udpConn, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.ParseIP("127.0.0.1"), Port: 0})
	require.NoError(t, err)
	defer udpConn.Close()
	udpTargetPort := udpConn.LocalAddr().(*net.UDPAddr).Port

	go func() {
		buf := make([]byte, 1024)
		for {
			n, addr, err := udpConn.ReadFrom(buf)
			if err != nil {
				return
			}
			_, _ = udpConn.WriteTo(append([]byte("ECHO:"), buf[:n]...), addr)
		}
	}()

	// 3. Start local trojan-go server
	trojanGoListener, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(t, err)
	trojanGoPort := trojanGoListener.Addr().(*net.TCPAddr).Port
	_ = trojanGoListener.Close()

	tmpDir := t.TempDir()
	certPath := filepath.Join(tmpDir, "server.crt")
	keyPath := filepath.Join(tmpDir, "server.key")
	require.NoError(t, os.WriteFile(certPath, []byte(testCert), 0o600))
	require.NoError(t, os.WriteFile(keyPath, []byte(testKey), 0o600))

	serverConfig := fmt.Sprintf(`{
  "run_type": "server",
  "local_addr": "127.0.0.1",
  "local_port": %d,
  "remote_addr": "127.0.0.1",
  "remote_port": %d,
  "password": ["MyTrojanGoSecretPass123"],
  "ssl": {
    "cert": "%s",
    "key": "%s",
    "sni": "localhost"
  },
  "websocket": {
    "enabled": true,
    "path": "/trojango-ws",
    "host": "localhost"
  },
  "mux": {
    "enabled": true,
    "concurrency": 8
  },
  "shadowsocks": {
    "enabled": true,
    "method": "aes-128-gcm",
    "password": "secondary-ss-secret"
  }
}`, trojanGoPort, httpTargetPort, certPath, keyPath)

	cfgFile := filepath.Join(tmpDir, "server.json")
	require.NoError(t, os.WriteFile(cfgFile, []byte(serverConfig), 0o600))

	cmd := exec.Command(trojanGoBin, "-config", cfgFile)
	var outputBuf bytes.Buffer
	cmd.Stdout = &outputBuf
	cmd.Stderr = &outputBuf
	require.NoError(t, cmd.Start())
	defer func() {
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		if t.Failed() {
			t.Logf("trojan-go output:\n%s", outputBuf.String())
		}
	}()

	// Wait for server to bind port
	var connected bool
	for range 50 {
		time.Sleep(50 * time.Millisecond)
		c, dialErr := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", trojanGoPort), 100*time.Millisecond)
		if dialErr == nil {
			c.Close()
			connected = true
			break
		}
	}
	require.True(t, connected, "trojan-go server failed to start within 2.5s")

	// 4. Start NekoBox client box instance using plugin registry
	mixedListener, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(t, err)
	mixedPort := mixedListener.Addr().(*net.TCPAddr).Port
	_ = mixedListener.Close()

	rawConfigJSON := fmt.Sprintf(`{
		"inbounds": [
			{
				"type": "mixed",
				"tag": "mixed-in",
				"listen": "127.0.0.1",
				"listen_port": %d
			}
		],
		"outbounds": [
			{
				"type": "trojan-go",
				"tag": "trojango-out",
				"server": "127.0.0.1",
				"server_port": %d,
				"password": "MyTrojanGoSecretPass123",
				"tls": {
					"enabled": true,
					"server_name": "localhost",
					"insecure": true
				},
				"transport": {
					"type": "ws",
					"path": "/trojango-ws",
					"headers": {
						"Host": ["localhost"]
					}
				},
				"mux": {
					"enabled": true,
					"concurrency": 8
				},
				"shadowsocks": {
					"enabled": true,
					"method": "aes-128-gcm",
					"password": "secondary-ss-secret"
				}
			}
		],
		"route": {
			"rules": [
				{
					"inbound": ["mixed-in"],
					"action": "route",
					"outbound": "trojango-out"
				}
			]
		}
	}`, mixedPort, trojanGoPort)

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

	var options option.Options
	require.NoError(t, options.UnmarshalJSONContext(ctx, []byte(rawConfigJSON)))

	instance, err := box.New(box.Options{
		Context: ctx,
		Options: options,
	})
	require.NoError(t, err)
	require.NoError(t, instance.Start())
	defer instance.Close()

	time.Sleep(300 * time.Millisecond)

	// 5. Test HTTP request through client mixed inbound
	proxyURL, err := url.Parse(fmt.Sprintf("http://127.0.0.1:%d", mixedPort))
	require.NoError(t, err)

	httpClient := &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyURL(proxyURL),
		},
		Timeout: 5 * time.Second,
	}

	targetURL := fmt.Sprintf("http://127.0.0.1:%d/ping", httpTargetPort)
	resp, err := httpClient.Get(targetURL)
	require.NoError(t, err)
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	require.NoError(t, err)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	require.Equal(t, "HELLO-FROM-TROJAN-GO", string(body))
	t.Logf("Trojan-Go Live E2E Success: status=200, body=%s", string(body))

	// 6. Test UDP packet via Outbound directly
	outboundTag, ok := instance.Outbound().Outbound("trojango-out")
	require.True(t, ok)
	udpDest := M.ParseSocksaddrHostPort("127.0.0.1", uint16(udpTargetPort))
	pktConn, err := outboundTag.ListenPacket(ctx, udpDest)
	require.NoError(t, err)
	defer pktConn.Close()

	_, err = pktConn.WriteTo([]byte("ping-trojango-udp"), udpDest.UDPAddr())
	require.NoError(t, err)

	respBuf := make([]byte, 1024)
	_ = pktConn.SetReadDeadline(time.Now().Add(2 * time.Second))
	n, _, err := pktConn.ReadFrom(respBuf)
	require.NoError(t, err)
	require.Equal(t, "ECHO:ping-trojango-udp", string(respBuf[:n]))
	t.Logf("Trojan-Go Live UDP Success: %s", string(respBuf[:n]))
}
