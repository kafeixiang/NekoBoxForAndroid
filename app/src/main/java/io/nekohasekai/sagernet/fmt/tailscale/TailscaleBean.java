package io.nekohasekai.sagernet.fmt.tailscale;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class TailscaleBean extends AbstractBean {

    public String stateDirectory;
    public String authKey;
    public String controlURL;
    public Boolean ephemeral;
    public String hostname;
    public Boolean acceptRoutes;
    public String exitNode;
    public Boolean exitNodeAllowLANAccess;
    public String advertiseRoutes;
    public Boolean advertiseExitNode;
    public String advertiseTags;
    public Integer listenPort;
    public Boolean sshServer;
    public Boolean advertiseFakeDNSRoutes;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (stateDirectory == null) stateDirectory = "";
        if (authKey == null) authKey = "";
        if (controlURL == null) controlURL = "";
        if (ephemeral == null) ephemeral = false;
        if (hostname == null) hostname = "";
        if (acceptRoutes == null) acceptRoutes = false;
        if (exitNode == null) exitNode = "";
        if (exitNodeAllowLANAccess == null) exitNodeAllowLANAccess = false;
        if (advertiseRoutes == null) advertiseRoutes = "";
        if (advertiseExitNode == null) advertiseExitNode = false;
        if (advertiseTags == null) advertiseTags = "";
        if (listenPort == null) listenPort = 0;
        if (sshServer == null) sshServer = false;
        if (advertiseFakeDNSRoutes == null) advertiseFakeDNSRoutes = false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(stateDirectory);
        output.writeString(authKey);
        output.writeString(controlURL);
        output.writeBoolean(ephemeral);
        output.writeString(hostname);
        output.writeBoolean(acceptRoutes);
        output.writeString(exitNode);
        output.writeBoolean(exitNodeAllowLANAccess);
        output.writeString(advertiseRoutes);
        output.writeBoolean(advertiseExitNode);
        output.writeString(advertiseTags);
        output.writeInt(listenPort);
        output.writeBoolean(sshServer);
        output.writeBoolean(advertiseFakeDNSRoutes);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        stateDirectory = input.readString();
        authKey = input.readString();
        controlURL = input.readString();
        ephemeral = input.readBoolean();
        hostname = input.readString();
        acceptRoutes = input.readBoolean();
        exitNode = input.readString();
        exitNodeAllowLANAccess = input.readBoolean();
        advertiseRoutes = input.readString();
        advertiseExitNode = input.readBoolean();
        advertiseTags = input.readString();
        listenPort = input.readInt();
        sshServer = input.readBoolean();
        if (version >= 1) {
            advertiseFakeDNSRoutes = input.readBoolean();
        } else {
            advertiseFakeDNSRoutes = false;
        }
    }

    @NotNull
    @Override
    public String displayName() {
        if (name != null && !name.isEmpty()) return name;
        if (hostname != null && !hostname.isEmpty()) return hostname;
        return "Tailscale";
    }

    @NotNull
    @Override
    public String displayAddress() {
        return controlURL != null ? controlURL : "";
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
    public TailscaleBean clone() {
        return KryoConverters.deserialize(new TailscaleBean(), KryoConverters.serialize(this));
    }

    public static final Creator<TailscaleBean> CREATOR = new CREATOR<TailscaleBean>() {
        @NonNull
        @Override
        public TailscaleBean newInstance() {
            return new TailscaleBean();
        }

        @Override
        public TailscaleBean[] newArray(int size) {
            return new TailscaleBean[size];
        }
    };
}
