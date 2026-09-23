package io.casehub.yaml.core.orchestration;

public class ChannelClosedException extends RuntimeException {

    public ChannelClosedException(String channelName) {
        super("Channel '" + channelName + "' is closed");
    }

    public ChannelClosedException(String channelName, Throwable cause) {
        super("Channel '" + channelName + "' was closed due to error: " + cause.getMessage(), cause);
    }
}
