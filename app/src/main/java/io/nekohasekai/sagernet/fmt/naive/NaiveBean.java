package io.nekohasekai.sagernet.fmt.naive;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class NaiveBean extends AbstractBean {

    /**
     * Available proto: https, quic.
     */
    public String proto;
    public String username;
    public String password;
    public String extraHeaders;
    public String sni;
    public String certificates;
    public Integer insecureConcurrency;

    // sing-box socks
    public Boolean sUoT;
    public String quicCongestionControl;

    @Override
    public void initializeDefaultValues() {
        if (serverPort == null) serverPort = 443;
        super.initializeDefaultValues();
        if (proto == null) proto = "https";
        if (username == null) username = "";
        if (password == null) password = "";
        if (extraHeaders == null) extraHeaders = "";
        if (certificates == null) certificates = "";
        if (sni == null) sni = "";
        if (insecureConcurrency == null) insecureConcurrency = 0;
        if (sUoT == null) sUoT = false;
        if (quicCongestionControl == null) quicCongestionControl = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(4);
        super.serialize(output);
        output.writeString(proto);
        output.writeString(username);
        output.writeString(password);
        // note: sequence is different from SagerNet,,,
        output.writeString(extraHeaders);
        output.writeString(certificates);
        output.writeString(sni);
        output.writeInt(insecureConcurrency);
        output.writeBoolean(sUoT);
        output.writeString(quicCongestionControl);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        proto = input.readString();
        username = input.readString();
        password = input.readString();
        extraHeaders = input.readString();
        if (version >= 2) {
            certificates = input.readString();
            sni = input.readString();
        }
        if (version >= 1) {
            insecureConcurrency = input.readInt();
        }
        if (version >= 3) {
            sUoT = input.readBoolean();
        }
        if (version >= 4) {
            quicCongestionControl = input.readString();
        } else {
            quicCongestionControl = "";
        }
    }

    @NotNull
    @Override
    public NaiveBean clone() {
        return KryoConverters.deserialize(new NaiveBean(), KryoConverters.serialize(this));
    }

    public static final Creator<NaiveBean> CREATOR = new CREATOR<NaiveBean>() {
        @NonNull
        @Override
        public NaiveBean newInstance() {
            return new NaiveBean();
        }

        @Override
        public NaiveBean[] newArray(int size) {
            return new NaiveBean[size];
        }
    };
}
