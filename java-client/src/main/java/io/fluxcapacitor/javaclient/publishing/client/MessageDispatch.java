package io.fluxcapacitor.javaclient.publishing.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

import java.util.List;

@Value
public class MessageDispatch {
    List<SerializedMessage> messages;
    MessageType messageType;
}
