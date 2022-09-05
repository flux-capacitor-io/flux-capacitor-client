package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

@Value
public class ScheduledCommand {
    SerializedMessage command;
}
