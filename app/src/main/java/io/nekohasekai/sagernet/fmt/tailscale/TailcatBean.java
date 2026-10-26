package io.nekohasekai.sagernet.fmt.tailscale;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class TailcatBean extends AbstractBean {

    public String privateKey;
    public String serverPublicKey;
    public String serverDiscoKey;
    public String preSharedKey;
    public String derpMapURL;
    public Integer derpRegion;
    public String derpServers;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (privateKey == null) privateKey = "";
        if (serverPublicKey == null) serverPublicKey = "";
        if (serverDiscoKey == null) serverDiscoKey = "";
        if (preSharedKey == null) preSharedKey = "";
        if (derpMapURL == null) derpMapURL = "";
        if (derpRegion == null) derpRegion = 0;
        if (derpServers == null) derpServers = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(privateKey);
        output.writeString(serverPublicKey);
        output.writeString(serverDiscoKey);
        output.writeString(preSharedKey);
        output.writeString(derpMapURL);
        output.writeInt(derpRegion);
        output.writeString(derpServers);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        input.readInt();
        super.deserialize(input);
        privateKey = input.readString();
        serverPublicKey = input.readString();
        serverDiscoKey = input.readString();
        preSharedKey = input.readString();
        derpMapURL = input.readString();
        derpRegion = input.readInt();
        derpServers = input.readString();
    }

    @NotNull
    @Override
    public String displayName() {
        if (name != null && !name.isEmpty()) return name;
        return "Tailcat";
    }

    @NotNull
    @Override
    public String displayAddress() {
        if (serverPublicKey != null && !serverPublicKey.isEmpty()) return serverPublicKey;
        if (derpMapURL != null && !derpMapURL.isEmpty()) return derpMapURL;
        return "";
    }

    @Override
    public boolean canICMPing() {
        return false;
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @Override
    public boolean canMapping() {
        return false;
    }

    @NotNull
    @Override
    public TailcatBean clone() {
        return KryoConverters.deserialize(new TailcatBean(), KryoConverters.serialize(this));
    }

    public static final Creator<TailcatBean> CREATOR = new CREATOR<TailcatBean>() {
        @NonNull
        @Override
        public TailcatBean newInstance() {
            return new TailcatBean();
        }

        @Override
        public TailcatBean[] newArray(int size) {
            return new TailcatBean[size];
        }
    };
}
