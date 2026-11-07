package io.nekohasekai.sagernet.fmt.direct;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class DirectBean extends AbstractBean {

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (name == null) name = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        input.readInt();
        super.deserialize(input);
    }

    @NotNull
    @Override
    public String displayName() {
        if (name == null || name.isEmpty()) {
            return "Direct";
        }
        return name;
    }

    @NotNull
    @Override
    public String displayAddress() {
        return "";
    }

    @Override
    public boolean canICMPing() {
        return true;
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
    public DirectBean clone() {
        return KryoConverters.deserialize(new DirectBean(), KryoConverters.serialize(this));
    }

    public static final Creator<DirectBean> CREATOR = new CREATOR<DirectBean>() {
        @NonNull
        @Override
        public DirectBean newInstance() {
            return new DirectBean();
        }

        @Override
        public DirectBean[] newArray(int size) {
            return new DirectBean[size];
        }
    };
}
