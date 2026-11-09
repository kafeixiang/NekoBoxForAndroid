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
)

func ensureSSRServerScript(t *testing.T) string {
	scriptPath := "/tmp/shadowsocksr/shadowsocks/server.py"
	if _, err := os.Stat(scriptPath); err != nil {
		cmd := exec.Command("git", "clone", "--depth=1", "https://github.com/shadowsocksrr/shadowsocksr.git", "/tmp/shadowsocksr")
		if err := cmd.Run(); err != nil {
			t.Skipf("cannot clone shadowsocksr repository: %v", err)
		}
	}

	// patch util.py to prefer Homebrew openssl on macOS if needed
	patchScript := `
import os
util_path = "/tmp/shadowsocksr/shadowsocks/crypto/util.py"
if os.path.exists(util_path):
    with open(util_path, "r") as f:
        content = f.read()
    if "/opt/homebrew/opt/openssl" not in content:
        content = content.replace("paths = []", """paths = [
        "/opt/homebrew/opt/openssl/lib/libcrypto.dylib",
        "/opt/homebrew/opt/openssl@3/lib/libcrypto.dylib",
        "/usr/local/opt/openssl/lib/libcrypto.dylib",
    ]""", 1)
        with open(util_path, "w") as f:
            f.write(content)
`
	_ = exec.Command("python3", "-c", patchScript).Run()

	absPath, err := filepath.Abs(scriptPath)
	if err != nil {
		t.Fatalf("resolve ssr script path: %v", err)
	}
	return absPath
}

func reserveSSRPort(t *testing.T, network string) int {
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

func startSSREchoServers(t *testing.T) (httpPort int, tcpPort int, udpPort int, stop func()) {
	// 1. HTTP Server
	httpListener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen http: %v", err)
	}
	httpPort = httpListener.Addr().(*net.TCPAddr).Port

	mux := http.NewServeMux()
	mux.HandleFunc("/echo", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Protocol", "ShadowsocksR")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("HELLO-FROM-SSR"))
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

func newSSRBoxContext(ctx context.Context) context.Context {
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

func TestShadowsocksR_LiveE2E(t *testing.T) {
	serverScript := ensureSSRServerScript(t)
	httpPort, tcpPort, udpPort, stopEcho := startSSREchoServers(t)
	defer stopEcho()

	ssrPort := reserveSSRPort(t, "tcp")
	mixedClientPort := reserveSSRPort(t, "tcp")

	ssrPassword := "TestSSRPass123!"
	ssrMethod := "aes-128-cfb"
	ssrProtocol := "auth_aes128_md5"
	ssrObfs := "http_simple"

	// 1. Launch ShadowsocksR server process
	cmd := exec.Command("python3", serverScript,
		"-s", "127.0.0.1",
		"-p", fmt.Sprintf("%d", ssrPort),
		"-k", ssrPassword,
		"-m", ssrMethod,
		"-O", ssrProtocol,
		"-o", ssrObfs,
		"--forbidden-ip", "",
	)
	var outputBuf bytes.Buffer
	cmd.Stdout = &outputBuf
	cmd.Stderr = &outputBuf
	if err := cmd.Start(); err != nil {
		t.Fatalf("failed to start Python SSR server: %v", err)
	}
	defer func() {
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		if t.Failed() {
			t.Logf("Python SSR output:\n%s", outputBuf.String())
		}
	}()

	// Wait for server to bind port
	for i := 0; i < 40; i++ {
		c, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", ssrPort), 100*time.Millisecond)
		if err == nil {
			c.Close()
			break
		}
		time.Sleep(100 * time.Millisecond)
	}

	// 2. Launch Client sing-box instance parsed directly from JSON (mirroring NekoBox ConfigBuilder output)
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
				"type": "shadowsocksr",
				"tag": "ssr-out",
				"server": "127.0.0.1",
				"server_port": %d,
				"method": %q,
				"password": %q,
				"protocol": %q,
				"protocol_param": "",
				"obfs": %q,
				"obfs_param": ""
			}
		],
		"route": {
			"rules": [
				{
					"inbound": ["mixed-in"],
					"action": "route",
					"outbound": "ssr-out"
				}
			]
		}
	}`, mixedClientPort, ssrPort, ssrMethod, ssrPassword, ssrProtocol, ssrObfs)

	clientCtx, clientCancel := context.WithCancel(context.Background())
	defer clientCancel()
	clientCtx = newSSRBoxContext(clientCtx)

	var clientOptions option.Options
	if err := clientOptions.UnmarshalJSONContext(clientCtx, []byte(rawConfigJSON)); err != nil {
		t.Fatalf("unmarshal NekoBox JSON config with context: %v", err)
	}

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
		t.Fatalf("HTTP request through Mixed -> SSR failed: %v", err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected status 200, got %d", resp.StatusCode)
	}
	if string(body) != "HELLO-FROM-SSR" {
		t.Fatalf("unexpected body: %s", string(body))
	}
	if resp.Header.Get("X-Protocol") != "ShadowsocksR" {
		t.Fatalf("unexpected X-Protocol header: %s", resp.Header.Get("X-Protocol"))
	}
	t.Logf("Mixed Inbound End-to-End Success: status=200, body=%s, proto=%s", string(body), resp.Header.Get("X-Protocol"))

	// 4. Test TCP Dial directly via ShadowsocksR Outbound
	ssrOutbound, loaded := clientBox.Outbound().Outbound("ssr-out")
	if !loaded {
		t.Fatalf("ssr-out not loaded")
	}

	tcpDst := M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", tcpPort))
	conn, err := ssrOutbound.DialContext(context.Background(), "tcp", tcpDst)
	if err != nil {
		t.Fatalf("TCP dial via ssr-out failed: %v", err)
	}
	defer conn.Close()

	testMsg := "ping-ssr-tcp"
	if _, err := conn.Write([]byte(testMsg)); err != nil {
		t.Fatalf("write to tcp conn failed: %v", err)
	}
	expected := "ECHO:" + testMsg
	readBuf := make([]byte, 1024)
	var totalRead int
	for totalRead < len(expected) {
		n, err := conn.Read(readBuf[totalRead:])
		if err != nil {
			t.Fatalf("read from tcp conn failed: %v (totalRead=%d)", err, totalRead)
		}
		totalRead += n
	}
	if string(readBuf[:totalRead]) != expected {
		t.Fatalf("expected %q, got %q", expected, string(readBuf[:totalRead]))
	}
	t.Logf("TCP Direct Dial Success: received %s", string(readBuf[:totalRead]))

	// 5. Test UDP Packet Dial via ShadowsocksR Outbound
	udpDst := M.ParseSocksaddr(fmt.Sprintf("127.0.0.1:%d", udpPort))
	packetConn, err := ssrOutbound.ListenPacket(context.Background(), udpDst)
	if err != nil {
		t.Fatalf("ListenPacket via ssr-out failed: %v", err)
	}
	defer packetConn.Close()

	udpMsg := []byte("ping-ssr-udp")
	if _, err := packetConn.WriteTo(udpMsg, udpDst.UDPAddr()); err != nil {
		t.Fatalf("WriteTo udp via ssr-out failed: %v", err)
	}
	udpReadBuf := make([]byte, 1024)
	_ = packetConn.SetReadDeadline(time.Now().Add(5 * time.Second))
	nUDP, _, err := packetConn.ReadFrom(udpReadBuf)
	if err != nil {
		t.Fatalf("ReadFrom udp via ssr-out failed: %v", err)
	}
	expectedUDP := "ECHO:ping-ssr-udp"
	if string(udpReadBuf[:nUDP]) != expectedUDP {
		t.Fatalf("expected UDP echo %q, got %q", expectedUDP, string(udpReadBuf[:nUDP]))
	}
	t.Logf("UDP Packet Dial Success: received %s", string(udpReadBuf[:nUDP]))
}
