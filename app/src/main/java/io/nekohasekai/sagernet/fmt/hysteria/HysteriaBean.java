package io.nekohasekai.sagernet.fmt.hysteria;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.ktx.NetsKt;
import kotlin.text.StringsKt;

public class HysteriaBean extends AbstractBean {
    public Integer protocolVersion;

    // Use serverPorts instead of serverPort
    public String serverPorts;

    // HY1 & 2

    public String authPayload;
    public String obfuscation;
    public String sni;
    public String caText;
    public Integer uploadMbps;
    public Integer downloadMbps;
    public Boolean allowInsecure;
    public Integer streamReceiveWindow;
    public Integer connectionReceiveWindow;
    public Boolean disableMtuDiscovery;
    public Integer hopInterval;
    public Integer hopIntervalMax;

    // HY2 Sing-box 1.15 features
    public String bbrProfile;
    public Boolean disableChromeParrot;
    public String pinSHA256;
    public String obfsType;
    public Integer geckoMinPacketSize;
    public Integer geckoMaxPacketSize;

    // HY2 Realm (sing-box 1.15)
    public Boolean enableRealm;
    public String realmServerURL;
    public String realmToken;
    public String realmId;
    public String realmStunServers;
    public Integer realmIPVersion;
    public Boolean realmPortMapping;
    public Integer realmPortMappingTimeout;
    public Integer realmPortMappingLifetime;

    // HY1

    public String alpn;

    public static final int TYPE_NONE = 0;
    public static final int TYPE_STRING = 1;
    public static final int TYPE_BASE64 = 2;
    public Integer authPayloadType;

    public static final int PROTOCOL_UDP = 0;
    public static final int PROTOCOL_FAKETCP = 1;
    public static final int PROTOCOL_WECHAT_VIDEO = 2;
    public Integer protocol = PROTOCOL_UDP;

    @Override
    public boolean canMapping() {
        if (Boolean.TRUE.equals(enableRealm)) return false;
        return (protocol != null ? protocol : PROTOCOL_UDP) != PROTOCOL_FAKETCP;
    }

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (protocolVersion == null) protocolVersion = 2;

        if (authPayloadType == null) authPayloadType = TYPE_NONE;
        if (authPayload == null) authPayload = "";
        if (protocol == null) protocol = PROTOCOL_UDP;
        if (obfuscation == null) obfuscation = "";
        if (sni == null) sni = "";
        if (alpn == null) alpn = "";
        if (caText == null) caText = "";
        if (allowInsecure == null) allowInsecure = false;

        if (protocolVersion == 1) {
            if (uploadMbps == null) uploadMbps = 10;
            if (downloadMbps == null) downloadMbps = 50;
        } else {
            if (uploadMbps == null) uploadMbps = 0;
            if (downloadMbps == null) downloadMbps = 0;
        }

        if (streamReceiveWindow == null) streamReceiveWindow = 0;
        if (connectionReceiveWindow == null) connectionReceiveWindow = 0;
        if (disableMtuDiscovery == null) disableMtuDiscovery = false;
        if (hopInterval == null) hopInterval = 10;
        if (hopIntervalMax == null) hopIntervalMax = 0;
        if (serverPorts == null) serverPorts = "443";
        if (bbrProfile == null) bbrProfile = "";
        if (disableChromeParrot == null) disableChromeParrot = false;
        if (pinSHA256 == null) pinSHA256 = "";
        if (obfsType == null) obfsType = "salamander";
        if (geckoMinPacketSize == null) geckoMinPacketSize = 0;
        if (geckoMaxPacketSize == null) geckoMaxPacketSize = 0;

        if (enableRealm == null) enableRealm = false;
        if (realmServerURL == null) realmServerURL = "";
        if (realmToken == null) realmToken = "";
        if (realmId == null) realmId = "";
        if (realmStunServers == null) realmStunServers = "";
        if (realmIPVersion == null) realmIPVersion = 0;
        if (realmPortMapping == null) realmPortMapping = false;
        if (realmPortMappingTimeout == null) realmPortMappingTimeout = 10;
        if (realmPortMappingLifetime == null) realmPortMappingLifetime = 600;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(10);
        super.serialize(output);

        output.writeInt(protocolVersion != null ? protocolVersion : 2);

        output.writeInt(authPayloadType != null ? authPayloadType : TYPE_NONE);
        output.writeString(authPayload != null ? authPayload : "");
        output.writeInt(protocol != null ? protocol : PROTOCOL_UDP);
        output.writeString(obfuscation != null ? obfuscation : "");
        output.writeString(sni != null ? sni : "");
        output.writeString(alpn != null ? alpn : "");

        output.writeInt(uploadMbps != null ? uploadMbps : 0);
        output.writeInt(downloadMbps != null ? downloadMbps : 0);
        output.writeBoolean(allowInsecure != null ? allowInsecure : false);

        output.writeString(caText != null ? caText : "");
        output.writeInt(streamReceiveWindow != null ? streamReceiveWindow : 0);
        output.writeInt(connectionReceiveWindow != null ? connectionReceiveWindow : 0);
        output.writeBoolean(disableMtuDiscovery != null ? disableMtuDiscovery : false);
        output.writeInt(hopInterval != null ? hopInterval : 10);
        output.writeString(serverPorts != null ? serverPorts : "");

        output.writeString(bbrProfile != null ? bbrProfile : "");
        output.writeBoolean(disableChromeParrot != null ? disableChromeParrot : false);
        output.writeString(pinSHA256 != null ? pinSHA256 : "");
        output.writeString(obfsType != null ? obfsType : "salamander");
        output.writeInt(geckoMinPacketSize != null ? geckoMinPacketSize : 0);
        output.writeInt(geckoMaxPacketSize != null ? geckoMaxPacketSize : 0);
        // v9
        output.writeInt(hopIntervalMax != null ? hopIntervalMax : 0);
        // v10 Realm
        output.writeBoolean(enableRealm != null ? enableRealm : false);
        output.writeString(realmServerURL != null ? realmServerURL : "");
        output.writeString(realmToken != null ? realmToken : "");
        output.writeString(realmId != null ? realmId : "");
        output.writeString(realmStunServers != null ? realmStunServers : "");
        output.writeInt(realmIPVersion != null ? realmIPVersion : 0);
        output.writeBoolean(realmPortMapping != null ? realmPortMapping : false);
        output.writeInt(realmPortMappingTimeout != null ? realmPortMappingTimeout : 10);
        output.writeInt(realmPortMappingLifetime != null ? realmPortMappingLifetime : 600);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        if (version >= 7) {
            protocolVersion = input.readInt();
        } else {
            protocolVersion = 1;
        }
        authPayloadType = input.readInt();
        authPayload = input.readString();
        if (version >= 3) {
            protocol = input.readInt();
        } else {
            protocol = PROTOCOL_UDP;
        }
        if (protocol == null) {
            protocol = PROTOCOL_UDP;
        }
        obfuscation = input.readString();
        sni = input.readString();
        if (version >= 2) {
            alpn = input.readString();
        }
        uploadMbps = input.readInt();
        downloadMbps = input.readInt();
        allowInsecure = input.readBoolean();
        if (version >= 1) {
            caText = input.readString();
            streamReceiveWindow = input.readInt();
            connectionReceiveWindow = input.readInt();
            if (version != 4) disableMtuDiscovery = input.readBoolean(); // note: skip 4
        }
        if (version >= 5) {
            hopInterval = input.readInt();
        }
        if (version >= 6) {
            serverPorts = input.readString();
        } else {
            // old update to new
            if (HysteriaFmtKt.isMultiPort(serverAddress)) {
                serverPorts = StringsKt.substringAfterLast(serverAddress, ":", serverAddress);
                serverAddress = StringsKt.substringBeforeLast(serverAddress, ":", serverAddress);
            } else {
                serverPorts = serverPort.toString();
            }
        }
        if (version >= 8) {
            bbrProfile = input.readString();
            disableChromeParrot = input.readBoolean();
            pinSHA256 = input.readString();
            obfsType = input.readString();
            geckoMinPacketSize = input.readInt();
            geckoMaxPacketSize = input.readInt();
        } else {
            bbrProfile = "";
            disableChromeParrot = false;
            pinSHA256 = "";
            obfsType = "salamander";
            geckoMinPacketSize = 0;
            geckoMaxPacketSize = 0;
        }
        if (version >= 9) {
            hopIntervalMax = input.readInt();
        } else {
            hopIntervalMax = 0;
        }
        if (version >= 10) {
            enableRealm = input.readBoolean();
            realmServerURL = input.readString();
            realmToken = input.readString();
            realmId = input.readString();
            realmStunServers = input.readString();
            realmIPVersion = input.readInt();
            realmPortMapping = input.readBoolean();
            realmPortMappingTimeout = input.readInt();
            realmPortMappingLifetime = input.readInt();
        } else {
            enableRealm = false;
            realmServerURL = "";
            realmToken = "";
            realmId = "";
            realmStunServers = "";
            realmIPVersion = 0;
            realmPortMapping = false;
            realmPortMappingTimeout = 10;
            realmPortMappingLifetime = 600;
        }
    }

    @Override
    public String displayAddress() {
        if (Boolean.TRUE.equals(enableRealm) && realmServerURL != null && !realmServerURL.isEmpty()) {
            return (realmId != null && !realmId.isEmpty() ? realmId + "@" : "") + realmServerURL;
        }
        return NetsKt.wrapIPV6Host(serverAddress) + ":" + serverPorts;
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @NotNull
    @Override
    public HysteriaBean clone() {
        return KryoConverters.deserialize(new HysteriaBean(), KryoConverters.serialize(this));
    }

    public static final Creator<HysteriaBean> CREATOR = new CREATOR<HysteriaBean>() {
        @NonNull
        @Override
        public HysteriaBean newInstance() {
            return new HysteriaBean();
        }

        @Override
        public HysteriaBean[] newArray(int size) {
            return new HysteriaBean[size];
        }
    };
}
