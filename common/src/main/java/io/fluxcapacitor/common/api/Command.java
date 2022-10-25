package io.fluxcapacitor.common.api;

import io.fluxcapacitor.common.Guarantee;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public abstract class Command extends Request {
    public abstract Guarantee getGuarantee();
}
