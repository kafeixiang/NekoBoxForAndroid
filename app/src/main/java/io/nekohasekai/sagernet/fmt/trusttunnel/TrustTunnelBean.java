package io.nekohasekai.sagernet.fmt.trusttunnel;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class TrustTunnelBean extends AbstractBean {

    public String username;
    public String password;
    public String sni;
    public Boolean quic;
    public String quicCongestionControl;
    public Boolean healthCheck;
    public Boolean allowInsecure;
    public String certificates;
    public String certificateSha256;
    public String certPublicKeySha256;
    public String alpn;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (username == null) username = "";
        if (password == null) password = "";
        if (sni == null) sni = "";
        if (quic == null) quic = false;
        if (quicCongestionControl == null) quicCongestionControl = "bbr";
        if (healthCheck == null) healthCheck = false;
        if (allowInsecure == null) allowInsecure = false;
        if (certificates == null) certificates = "";
        if (certificateSha256 == null) certificateSha256 = "";
        if (certPublicKeySha256 == null) certPublicKeySha256 = "";
        if (alpn == null) alpn = "";
        if (serverPort == null || serverPort == 0) serverPort = 443;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(username);
        output.writeString(password);
        output.writeString(sni);
        output.writeBoolean(quic);
        output.writeString(quicCongestionControl);
        output.writeBoolean(healthCheck);
        output.writeBoolean(allowInsecure);
        output.writeString(certificates);
        output.writeString(certificateSha256);
        output.writeString(certPublicKeySha256);
        output.writeString(alpn);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        username = input.readString();
        password = input.readString();
        sni = input.readString();
        quic = input.readBoolean();
        quicCongestionControl = input.readString();
        healthCheck = input.readBoolean();
        allowInsecure = input.readBoolean();
        certificates = input.readString();
        certificateSha256 = input.readString();
        certPublicKeySha256 = input.readString();
        alpn = input.readString();
    }

    @Override
    public boolean canTCPing() {
        return !Boolean.TRUE.equals(quic);
    }

    @NotNull
    @Override
    public TrustTunnelBean clone() {
        return KryoConverters.deserialize(new TrustTunnelBean(), KryoConverters.serialize(this));
    }

    public static final Creator<TrustTunnelBean> CREATOR = new CREATOR<TrustTunnelBean>() {
        @NonNull
        @Override
        public TrustTunnelBean newInstance() {
            return new TrustTunnelBean();
        }

        @Override
        public TrustTunnelBean[] newArray(int size) {
            return new TrustTunnelBean[size];
        }
    };
}
