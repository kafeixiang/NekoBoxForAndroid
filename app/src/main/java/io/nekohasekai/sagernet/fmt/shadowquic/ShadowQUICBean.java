package io.nekohasekai.sagernet.fmt.shadowquic;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class ShadowQUICBean extends AbstractBean {

    public String username;
    public String password;
    public String sni;
    public String alpn;
    public String congestionControl;
    public Boolean udpOverStream;
    public Boolean zeroRttHandshake;
    public Boolean sunnyQuic;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (username == null) username = "";
        if (password == null) password = "";
        if (sni == null) sni = "";
        if (alpn == null) alpn = "";
        if (congestionControl == null) congestionControl = "bbr";
        if (udpOverStream == null) udpOverStream = false;
        if (zeroRttHandshake == null) zeroRttHandshake = false;
        if (sunnyQuic == null) sunnyQuic = false;
        if (serverPort == null || serverPort == 0) serverPort = 443;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(username != null ? username : "");
        output.writeString(password != null ? password : "");
        output.writeString(sni != null ? sni : "");
        output.writeString(alpn != null ? alpn : "");
        output.writeString(congestionControl != null ? congestionControl : "bbr");
        output.writeBoolean(udpOverStream != null ? udpOverStream : false);
        output.writeBoolean(zeroRttHandshake != null ? zeroRttHandshake : false);
        output.writeBoolean(sunnyQuic != null ? sunnyQuic : false);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        username = input.readString();
        password = input.readString();
        sni = input.readString();
        alpn = input.readString();
        congestionControl = input.readString();
        udpOverStream = input.readBoolean();
        zeroRttHandshake = input.readBoolean();
        sunnyQuic = input.readBoolean();
    }

    @Override
    public String displayAddress() {
        if (serverAddress == null || serverAddress.isEmpty()) {
            return "127.0.0.1:" + (serverPort != null ? serverPort : 443);
        }
        return super.displayAddress();
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @NotNull
    @Override
    public ShadowQUICBean clone() {
        return KryoConverters.deserialize(new ShadowQUICBean(), KryoConverters.serialize(this));
    }

    public static final Creator<ShadowQUICBean> CREATOR = new CREATOR<ShadowQUICBean>() {
        @NonNull
        @Override
        public ShadowQUICBean newInstance() {
            return new ShadowQUICBean();
        }

        @Override
        public ShadowQUICBean[] newArray(int size) {
            return new ShadowQUICBean[size];
        }
    };
}
