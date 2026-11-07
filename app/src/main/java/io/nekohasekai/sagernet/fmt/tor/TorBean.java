package io.nekohasekai.sagernet.fmt.tor;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class TorBean extends AbstractBean {

    public String executablePath;
    public String extraArgs;
    public String dataDirectory;
    public String torrc;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (executablePath == null) executablePath = "";
        if (extraArgs == null) extraArgs = "";
        if (dataDirectory == null) dataDirectory = "";
        if (torrc == null) torrc = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(executablePath);
        output.writeString(extraArgs);
        output.writeString(dataDirectory);
        output.writeString(torrc);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        input.readInt();
        super.deserialize(input);
        executablePath = input.readString();
        extraArgs = input.readString();
        dataDirectory = input.readString();
        torrc = input.readString();
    }

    @NotNull
    @Override
    public String displayName() {
        if (name != null && !name.isEmpty()) return name;
        return "Tor";
    }

    @NotNull
    @Override
    public String displayAddress() {
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
    public TorBean clone() {
        return KryoConverters.deserialize(new TorBean(), KryoConverters.serialize(this));
    }

    public static final Creator<TorBean> CREATOR = new CREATOR<TorBean>() {
        @NonNull
        @Override
        public TorBean newInstance() {
            return new TorBean();
        }

        @Override
        public TorBean[] newArray(int size) {
            return new TorBean[size];
        }
    };
}
