package libcore

import (
	"net"
	"net/netip"
	"slices"
	"strings"
	"sync"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

var (
	defaultInterfaceMu  sync.RWMutex
	currentDefaultIface *control.Interface
	currentNetIfaces    []adapter.NetworkInterface
	activeMonitors      []*platformDefaultInterfaceMonitor
)

// UpdateDefaultInterface pushes the active Android network metadata from
// ConnectivityManager / LinkProperties into sing-box's platform monitor so
// Android 15 SELinux restrictions on netlink route sockets do not leave sing-box
// without a default interface.
func UpdateDefaultInterface(name string, index int32, mtu int32, netType int32, expensive bool, constrained bool, addresses string) {
	if index < 0 || name == "" {
		defaultInterfaceMu.Lock()
		changed := currentDefaultIface != nil || len(currentNetIfaces) > 0
		currentDefaultIface = nil
		currentNetIfaces = nil
		monitors := slices.Clone(activeMonitors)
		defaultInterfaceMu.Unlock()
		if changed {
			for _, m := range monitors {
				m.notifyUpdated(nil)
			}
		}
		return
	}

	iface := control.Interface{
		Index: int(index),
		MTU:   int(mtu),
		Name:  name,
		Flags: net.FlagUp | net.FlagMulticast,
	}
	if netIface, err := net.InterfaceByName(name); err == nil && netIface != nil {
		if iface.Index <= 0 && netIface.Index > 0 {
			iface.Index = netIface.Index
		}
		if iface.MTU <= 0 && netIface.MTU > 0 {
			iface.MTU = netIface.MTU
		}
		iface.HardwareAddr = netIface.HardwareAddr
		if netIface.Flags != 0 {
			iface.Flags = netIface.Flags | net.FlagUp
		}
	}
	if iface.Index <= 0 {
		iface.Index = 1
	}
	if iface.MTU <= 0 {
		iface.MTU = 1500
	}
	for _, raw := range strings.Split(addresses, ",") {
		s := strings.TrimSpace(raw)
		if s == "" {
			continue
		}
		if prefix, err := netip.ParsePrefix(s); err == nil {
			iface.Addresses = append(iface.Addresses, prefix)
		} else if addr, err := netip.ParseAddr(s); err == nil {
			iface.Addresses = append(iface.Addresses, netip.PrefixFrom(addr, addr.BitLen()))
		}
	}

	var ifType C.InterfaceType
	switch netType {
	case 0:
		ifType = C.InterfaceTypeWIFI
	case 1:
		ifType = C.InterfaceTypeCellular
	case 2:
		ifType = C.InterfaceTypeEthernet
	default:
		ifType = C.InterfaceTypeOther
	}

	netEntry := adapter.NetworkInterface{
		Interface:   iface,
		Type:        ifType,
		Expensive:   expensive,
		Constrained: constrained,
	}

	defaultInterfaceMu.Lock()
	changed := currentDefaultIface == nil ||
		!currentDefaultIface.Equals(iface) ||
		len(currentNetIfaces) != 1 ||
		currentNetIfaces[0].Type != ifType ||
		currentNetIfaces[0].Expensive != expensive ||
		currentNetIfaces[0].Constrained != constrained
	ifaceCopy := iface
	currentDefaultIface = &ifaceCopy
	currentNetIfaces = []adapter.NetworkInterface{netEntry}
	monitors := slices.Clone(activeMonitors)
	defaultInterfaceMu.Unlock()

	if changed {
		for _, m := range monitors {
			m.notifyUpdated(&ifaceCopy)
		}
	}
}

func currentNetworkInterfacesCopy() []adapter.NetworkInterface {
	defaultInterfaceMu.RLock()
	defer defaultInterfaceMu.RUnlock()
	if len(currentNetIfaces) == 0 {
		return nil
	}
	out := make([]adapter.NetworkInterface, len(currentNetIfaces))
	copy(out, currentNetIfaces)
	return out
}

type platformDefaultInterfaceMonitor struct {
	mu             sync.Mutex
	logger         logger.Logger
	networkManager adapter.NetworkManager
	callbacks      list.List[tun.DefaultInterfaceUpdateCallback]
	myInterfaces   []string
}

func newPlatformDefaultInterfaceMonitor(networkManager adapter.NetworkManager, logger logger.Logger) *platformDefaultInterfaceMonitor {
	return &platformDefaultInterfaceMonitor{
		logger:         logger,
		networkManager: networkManager,
	}
}

func (m *platformDefaultInterfaceMonitor) setNetworkManager(networkManager adapter.NetworkManager) {
	m.mu.Lock()
	m.networkManager = networkManager
	m.mu.Unlock()
}

func (m *platformDefaultInterfaceMonitor) Start() error {
	defaultInterfaceMu.Lock()
	if !slices.Contains(activeMonitors, m) {
		activeMonitors = append(activeMonitors, m)
	}
	hasDefault := currentDefaultIface != nil
	defaultInterfaceMu.Unlock()

	if hasDefault {
		m.mu.Lock()
		nm := m.networkManager
		m.mu.Unlock()
		if nm != nil {
			_ = nm.UpdateInterfaces()
		}
	}
	return nil
}

func (m *platformDefaultInterfaceMonitor) Close() error {
	defaultInterfaceMu.Lock()
	activeMonitors = slices.DeleteFunc(activeMonitors, func(it *platformDefaultInterfaceMonitor) bool {
		return it == m
	})
	defaultInterfaceMu.Unlock()

	m.mu.Lock()
	m.networkManager = nil
	m.mu.Unlock()
	return nil
}

func (m *platformDefaultInterfaceMonitor) notifyUpdated(iface *control.Interface) {
	m.mu.Lock()
	nm := m.networkManager
	callbacks := m.callbacks.Array()
	m.mu.Unlock()

	if nm != nil {
		_ = nm.UpdateInterfaces()
	}
	for _, callback := range callbacks {
		callback(iface, 0)
	}
}

func (m *platformDefaultInterfaceMonitor) DefaultInterface() *control.Interface {
	defaultInterfaceMu.RLock()
	defer defaultInterfaceMu.RUnlock()
	if currentDefaultIface == nil {
		return nil
	}
	copyIface := *currentDefaultIface
	return &copyIface
}

func (m *platformDefaultInterfaceMonitor) OverrideAndroidVPN() bool {
	return false
}

func (m *platformDefaultInterfaceMonitor) AndroidVPNEnabled() bool {
	return false
}

func (m *platformDefaultInterfaceMonitor) RegisterCallback(callback tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.callbacks.PushBack(callback)
}

func (m *platformDefaultInterfaceMonitor) UnregisterCallback(element *list.Element[tun.DefaultInterfaceUpdateCallback]) {
	if element == nil {
		return
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	m.callbacks.Remove(element)
}

func (m *platformDefaultInterfaceMonitor) RegisterMyInterface(interfaceName string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if !slices.Contains(m.myInterfaces, interfaceName) {
		m.myInterfaces = append(m.myInterfaces, interfaceName)
	}
}

func (m *platformDefaultInterfaceMonitor) MyInterface() string {
	m.mu.Lock()
	defer m.mu.Unlock()
	if len(m.myInterfaces) == 0 {
		return ""
	}
	return m.myInterfaces[0]
}

func (m *platformDefaultInterfaceMonitor) MyInterfaces() []string {
	m.mu.Lock()
	defer m.mu.Unlock()
	return slices.Clone(m.myInterfaces)
}
