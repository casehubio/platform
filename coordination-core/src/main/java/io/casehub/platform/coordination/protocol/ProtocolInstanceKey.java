package io.casehub.platform.coordination.protocol;

public record ProtocolInstanceKey(String protocolName, String channelId) {
    public String storeKey() {
        return protocolName + ":" + channelId;
    }
}
