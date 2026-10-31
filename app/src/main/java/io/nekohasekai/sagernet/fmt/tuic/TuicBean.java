package io.nekohasekai.sagernet.fmt.tuic;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class TuicBean extends AbstractBean {

    public String token;
    public String caText;
    public String udpRelayMode;
    public String congestionController;
    public String alpn;
    public Boolean disableSNI;
    public Boolean reduceRTT;
    public Integer mtu;
    public String sni;

    // TUIC zep

    public Boolean fastConnect;
    public Boolean allowInsecure;

    // TUIC v5

    public String customJSON;
    public Integer protocolVersion;
    public String uuid;
    public Boolean udpOverStream;
    public Integer heartbeat;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (token == null) token = "";
        if (caText == null) caText = "";
        if (udpRelayMode == null) udpRelayMode = "native";
        if (congestionController == null) congestionController = "cubic";
        if (alpn == null) alpn = "";
        if (disableSNI == null) disableSNI = false;
        if (reduceRTT == null) reduceRTT = false;
        if (mtu == null) mtu = 1400;
        if (sni == null) sni = "";
        if (fastConnect == null) fastConnect = false;
        if (allowInsecure == null) allowInsecure = false;
        if (customJSON == null) customJSON = "";
        if (protocolVersion == null) protocolVersion = 5;
        if (uuid == null) uuid = "";
        if (udpOverStream == null) udpOverStream = false;
        if (heartbeat == null) heartbeat = 10;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(3);
        super.serialize(output);
        output.writeString(token);
        output.writeString(caText);
        output.writeString(udpRelayMode);
        output.writeString(congestionController);
        output.writeString(alpn);
        output.writeBoolean(disableSNI);
        output.writeBoolean(reduceRTT);
        output.writeInt(mtu);
        output.writeString(sni);
        output.writeBoolean(fastConnect);
        output.writeBoolean(allowInsecure);
        output.writeString(customJSON);
        output.writeInt(protocolVersion);
        output.writeString(uuid);
        // v3
        output.writeBoolean(udpOverStream != null ? udpOverStream : false);
        output.writeInt(heartbeat != null ? heartbeat : 0);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        token = input.readString();
        caText = input.readString();
        udpRelayMode = input.readString();
        congestionController = input.readString();
        alpn = input.readString();
        disableSNI = input.readBoolean();
        reduceRTT = input.readBoolean();
        mtu = input.readInt();
        sni = input.readString();
        if (version >= 1) {
            fastConnect = input.readBoolean();
            allowInsecure = input.readBoolean();
        }
        if (version >= 2) {
            customJSON = input.readString();
            protocolVersion = input.readInt();
            uuid = input.readString();
        } else {
            protocolVersion = 4;
        }
        if (version >= 3) {
            udpOverStream = input.readBoolean();
            heartbeat = input.readInt();
        } else {
            udpOverStream = false;
            heartbeat = 0;
        }
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @NotNull
    @Override
    public TuicBean clone() {
        return KryoConverters.deserialize(new TuicBean(), KryoConverters.serialize(this));
    }

    public static final Creator<TuicBean> CREATOR = new CREATOR<TuicBean>() {
        @NonNull
        @Override
        public TuicBean newInstance() {
            return new TuicBean();
        }

        @Override
        public TuicBean[] newArray(int size) {
            return new TuicBean[size];
        }
    };
}
