/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.mieru;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class MieruBean extends AbstractBean {

    public String protocol;
    public String username;
    public String password;
    public Integer mtu;
    public String serverPorts;
    public String multiplexing;
    public String trafficPattern;
    public String handshakeMode;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (protocol == null) protocol = "TCP";
        if (username == null) username = "";
        if (password == null) password = "";
        if (mtu == null) mtu = 1400;
        if (serverPorts == null) serverPorts = "";
        if (multiplexing == null) multiplexing = "MULTIPLEXING_LOW";
        if (trafficPattern == null) trafficPattern = "";
        if (handshakeMode == null) handshakeMode = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(protocol);
        output.writeString(username);
        output.writeString(password);
        if (protocol.equals("UDP")) {
            output.writeInt(mtu);
        }
        output.writeString(serverPorts);
        output.writeString(multiplexing);
        output.writeString(trafficPattern);
        output.writeString(handshakeMode);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        protocol = input.readString();
        username = input.readString();
        password = input.readString();
        if (protocol.equals("UDP")) {
            mtu = input.readInt();
        }
        if (version >= 1) {
            serverPorts = input.readString();
            multiplexing = input.readString();
            trafficPattern = input.readString();
            handshakeMode = input.readString();
        } else {
            serverPorts = "";
            multiplexing = "MULTIPLEXING_LOW";
            trafficPattern = "";
            handshakeMode = "";
        }
    }

    @Override
    public boolean canTCPing() {
        return "TCP".equalsIgnoreCase(protocol);
    }

    @NotNull
    @Override
    public String displayAddress() {
        if (serverPorts != null && !serverPorts.trim().isEmpty()) {
            return serverAddress + ":" + serverPorts;
        }
        return super.displayAddress();
    }

    @NotNull
    @Override
    public MieruBean clone() {
        return KryoConverters.deserialize(new MieruBean(), KryoConverters.serialize(this));
    }

    public static final Creator<MieruBean> CREATOR = new CREATOR<MieruBean>() {
        @NonNull
        @Override
        public MieruBean newInstance() {
            return new MieruBean();
        }

        @Override
        public MieruBean[] newArray(int size) {
            return new MieruBean[size];
        }
    };
}
