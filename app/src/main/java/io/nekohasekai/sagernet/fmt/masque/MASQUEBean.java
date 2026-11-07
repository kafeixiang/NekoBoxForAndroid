package io.nekohasekai.sagernet.fmt.masque;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class MASQUEBean extends AbstractBean {

    public static final int DEFAULT_MTU = 1280;

    public String username;
    public String password;
    public String path;
    public String headers;
    public Integer httpVersion;
    public Boolean disableVersionFallback;
    public Boolean enableTLS;
    public String serverName;
    public String alpn;
    public String certificates;
    public String utlsFingerprint;
    public Boolean allowInsecure;
    public Boolean disableSNI;
    public Integer mtu;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (username == null) username = "";
        if (password == null) password = "";
        if (path == null) path = "";
        if (headers == null) headers = "";
        if (httpVersion == null || httpVersion < 1 || httpVersion > 3) httpVersion = 3;
        if (disableVersionFallback == null) disableVersionFallback = false;
        if (enableTLS == null) enableTLS = true;
        if (serverName == null) serverName = "";
        if (alpn == null) alpn = "";
        if (certificates == null) certificates = "";
        if (utlsFingerprint == null) utlsFingerprint = "";
        if (allowInsecure == null) allowInsecure = false;
        if (disableSNI == null) disableSNI = false;
        if (mtu == null) mtu = DEFAULT_MTU;
        if (serverPort == 0) serverPort = 443;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(username);
        output.writeString(password);
        output.writeString(path);
        output.writeString(headers);
        output.writeInt(httpVersion);
        output.writeBoolean(disableVersionFallback);
        output.writeBoolean(enableTLS);
        output.writeString(serverName);
        output.writeString(alpn);
        output.writeString(certificates);
        output.writeString(utlsFingerprint);
        output.writeBoolean(allowInsecure);
        output.writeBoolean(disableSNI);
        output.writeInt(mtu);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        input.readInt();
        super.deserialize(input);
        username = input.readString();
        password = input.readString();
        path = input.readString();
        headers = input.readString();
        httpVersion = input.readInt();
        disableVersionFallback = input.readBoolean();
        enableTLS = input.readBoolean();
        serverName = input.readString();
        alpn = input.readString();
        certificates = input.readString();
        utlsFingerprint = input.readString();
        allowInsecure = input.readBoolean();
        disableSNI = input.readBoolean();
        mtu = input.readInt();
    }

    @Override
    public boolean canMapping() {
        return false;
    }

    @NotNull
    @Override
    public MASQUEBean clone() {
        return KryoConverters.deserialize(new MASQUEBean(), KryoConverters.serialize(this));
    }

    public static final Creator<MASQUEBean> CREATOR = new CREATOR<MASQUEBean>() {
        @NonNull
        @Override
        public MASQUEBean newInstance() {
            return new MASQUEBean();
        }

        @Override
        public MASQUEBean[] newArray(int size) {
            return new MASQUEBean[size];
        }
    };
}
