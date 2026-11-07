package io.nekohasekai.sagernet.fmt.openconnect;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class OpenConnectBean extends AbstractBean {

    public static class FormEntry {
        public String formId;
        public String submissionKey;
        public String name;
        public String value;

        public FormEntry() {
            formId = "";
            submissionKey = "";
            name = "";
            value = "";
        }

        public FormEntry(String formId, String submissionKey, String name, String value) {
            this.formId = formId != null ? formId : "";
            this.submissionKey = submissionKey != null ? submissionKey : "";
            this.name = name != null ? name : "";
            this.value = value != null ? value : "";
        }

        public void serialize(ByteBufferOutput output) {
            output.writeString(formId);
            output.writeString(submissionKey);
            output.writeString(name);
            output.writeString(value);
        }

        public static FormEntry deserialize(ByteBufferInput input) {
            FormEntry entry = new FormEntry();
            entry.formId = input.readString();
            entry.submissionKey = input.readString();
            entry.name = input.readString();
            entry.value = input.readString();
            return entry;
        }
    }

    public String server;
    public String flavor;
    public String username;
    public String password;
    public String authGroup;
    public String reportedOS;
    public String userAgent;
    public String localHostname;
    public Boolean allowInsecureCrypto;
    public Boolean tlsInsecure;
    public String tlsServerName;
    public String tlsPeerFingerprint;
    public String certificateAuthority;
    public String clientCertificate;
    public String clientKey;
    public String clientKeyPassword;
    public String mcaCertificate;
    public String mcaKey;
    public String mcaKeyPassword;
    public List<FormEntry> formEntries;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (server == null) server = "https://127.0.0.1";
        if (flavor == null) flavor = "";
        if (username == null) username = "";
        if (password == null) password = "";
        if (authGroup == null) authGroup = "";
        if (reportedOS == null) reportedOS = "";
        if (userAgent == null) userAgent = "";
        if (localHostname == null) localHostname = "";
        if (allowInsecureCrypto == null) allowInsecureCrypto = false;
        if (tlsInsecure == null) tlsInsecure = false;
        if (tlsServerName == null) tlsServerName = "";
        if (tlsPeerFingerprint == null) tlsPeerFingerprint = "";
        if (certificateAuthority == null) certificateAuthority = "";
        if (clientCertificate == null) clientCertificate = "";
        if (clientKey == null) clientKey = "";
        if (clientKeyPassword == null) clientKeyPassword = "";
        if (mcaCertificate == null) mcaCertificate = "";
        if (mcaKey == null) mcaKey = "";
        if (mcaKeyPassword == null) mcaKeyPassword = "";
        if (formEntries == null) formEntries = new ArrayList<>();
        if (serverPort == 0) serverPort = 443;
        parseServerAddress();
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(2);
        super.serialize(output);
        output.writeString(server);
        output.writeString(flavor);
        output.writeString(username);
        output.writeString(password);
        output.writeString(authGroup);
        output.writeString(reportedOS);
        output.writeString(userAgent);
        output.writeBoolean(allowInsecureCrypto);
        output.writeString(certificateAuthority);
        output.writeString(clientCertificate);
        output.writeString(clientKey);
        output.writeString(clientKeyPassword);
        output.writeString(mcaCertificate);
        output.writeString(mcaKey);
        output.writeString(mcaKeyPassword);

        // version 1
        output.writeInt(formEntries.size());
        for (FormEntry entry : formEntries) {
            entry.serialize(output);
        }

        // version 2
        output.writeString(localHostname);
        output.writeBoolean(tlsInsecure);
        output.writeString(tlsServerName);
        output.writeString(tlsPeerFingerprint);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        server = input.readString();
        flavor = input.readString();
        username = input.readString();
        password = input.readString();
        authGroup = input.readString();
        reportedOS = input.readString();
        userAgent = input.readString();
        allowInsecureCrypto = input.readBoolean();
        certificateAuthority = input.readString();
        clientCertificate = input.readString();
        clientKey = input.readString();
        clientKeyPassword = input.readString();
        mcaCertificate = input.readString();
        mcaKey = input.readString();
        mcaKeyPassword = input.readString();

        formEntries = new ArrayList<>();
        if (version >= 1) {
            int count = input.readInt();
            for (int i = 0; i < count; i++) {
                formEntries.add(FormEntry.deserialize(input));
            }
        }

        if (version >= 2) {
            localHostname = input.readString();
            tlsInsecure = input.readBoolean();
            tlsServerName = input.readString();
            tlsPeerFingerprint = input.readString();
        }
        parseServerAddress();
    }

    public void parseServerAddress() {
        if (server != null && !server.isEmpty()) {
            try {
                String s = server.trim();
                if (!s.contains("://")) {
                    s = "https://" + s;
                }
                java.net.URI uri = java.net.URI.create(s);
                if (uri.getHost() != null && !uri.getHost().isEmpty()) {
                    serverAddress = uri.getHost();
                }
                if (uri.getPort() > 0) {
                    serverPort = uri.getPort();
                } else {
                    serverPort = 443;
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean canTCPing() {
        return true;
    }

    @NotNull
    @Override
    public String displayAddress() {
        return server != null ? server : "";
    }

    @NotNull
    @Override
    public OpenConnectBean clone() {
        return KryoConverters.deserialize(new OpenConnectBean(), KryoConverters.serialize(this));
    }

    public static final Creator<OpenConnectBean> CREATOR = new CREATOR<OpenConnectBean>() {
        @NonNull
        @Override
        public OpenConnectBean newInstance() {
            return new OpenConnectBean();
        }

        @Override
        public OpenConnectBean[] newArray(int size) {
            return new OpenConnectBean[size];
        }
    };
}
