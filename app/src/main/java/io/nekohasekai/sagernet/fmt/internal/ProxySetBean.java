package io.nekohasekai.sagernet.fmt.internal;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import java.util.ArrayList;
import java.util.List;

import io.nekohasekai.sagernet.ConstantsKt;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class ProxySetBean extends InternalBean {

    public static final int MANAGEMENT_SELECTOR = 0;
    public static final int MANAGEMENT_URLTEST = 1;
    public static final int MANAGEMENT_BALANCER = 2;
    public Integer management;

    public static final int TYPE_LIST = 0;
    public static final int TYPE_GROUP = 1;
    public Integer type;

    public List<Long> proxies;
    public Long groupId;
    public String groupFilterNotRegex;

    // Selector + URLTest
    public Boolean interruptExistConnections;

    // URLTest
    public String testURL;
    public String testInterval;
    public String testIdleTimeout;
    public Integer testTolerance;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (management == null) management = MANAGEMENT_SELECTOR;
        if (name == null) name = "";
        if (type == null) type = TYPE_LIST;
        if (proxies == null) proxies = new ArrayList<>();
        if (groupId == null) groupId = 0L;
        if (groupFilterNotRegex == null) groupFilterNotRegex = "";
        if (interruptExistConnections == null) interruptExistConnections = false;
        if (testURL == null || testURL.isEmpty()) testURL = ConstantsKt.CONNECTION_TEST_URL;
        if (testInterval == null || testInterval.isEmpty()) testInterval = "3m";
        if (testIdleTimeout == null || testIdleTimeout.isEmpty()) testIdleTimeout = "3m";
        if (testTolerance == null) testTolerance = 50;
    }

    @Override
    public String displayName() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        } else {
            int hash = Math.abs(hashCode());
            int mgmt = management != null ? management : MANAGEMENT_SELECTOR;
            switch (mgmt) {
                case MANAGEMENT_SELECTOR:
                    return "Selector " + hash;
                case MANAGEMENT_URLTEST:
                    return "URLTest " + hash;
                case MANAGEMENT_BALANCER:
                    return "Balancer " + hash;
                default:
                    return "Unknown " + hash;
            }
        }
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        output.writeInt(management != null ? management : MANAGEMENT_SELECTOR);
        output.writeBoolean(interruptExistConnections != null && interruptExistConnections);
        output.writeString(testURL != null ? testURL : ConstantsKt.CONNECTION_TEST_URL);
        output.writeString(testInterval != null ? testInterval : "3m");
        output.writeString(testIdleTimeout != null ? testIdleTimeout : "3m");
        output.writeInt(testTolerance != null ? testTolerance : 50);

        output.writeInt(type != null ? type : TYPE_LIST);
        switch (type != null ? type : TYPE_LIST) {
            case TYPE_LIST: {
                int length = proxies != null ? proxies.size() : 0;
                output.writeInt(length);
                if (proxies != null) {
                    for (Long proxy : proxies) {
                        output.writeLong(proxy);
                    }
                }
                break;
            }
            case TYPE_GROUP: {
                output.writeLong(groupId != null ? groupId : 0L);
                output.writeString(groupFilterNotRegex != null ? groupFilterNotRegex : "");
                break;
            }
        }
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        management = input.readInt();
        interruptExistConnections = input.readBoolean();
        testURL = input.readString();
        testInterval = input.readString();
        testIdleTimeout = input.readString();
        testTolerance = input.readInt();

        type = input.readInt();
        switch (type) {
            case TYPE_LIST: {
                int length = input.readInt();
                proxies = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    proxies.add(input.readLong());
                }
                break;
            }
            case TYPE_GROUP: {
                groupId = input.readLong();
                if (version >= 1) {
                    groupFilterNotRegex = input.readString();
                } else {
                    groupFilterNotRegex = "";
                }
                break;
            }
        }
    }

    @NonNull
    @Override
    public ProxySetBean clone() {
        return KryoConverters.deserialize(new ProxySetBean(), KryoConverters.serialize(this));
    }

    public static final Creator<ProxySetBean> CREATOR = new CREATOR<ProxySetBean>() {
        @NonNull
        @Override
        public ProxySetBean newInstance() {
            return new ProxySetBean();
        }

        @Override
        public ProxySetBean[] newArray(int size) {
            return new ProxySetBean[size];
        }
    };

    public String displayType() {
        int mgmt = management != null ? management : MANAGEMENT_SELECTOR;
        switch (mgmt) {
            case MANAGEMENT_SELECTOR:
                return "Selector";
            case MANAGEMENT_URLTEST:
                return "URLTest";
            case MANAGEMENT_BALANCER:
                return "Balancer";
            default:
                return "Unknown";
        }
    }
}
