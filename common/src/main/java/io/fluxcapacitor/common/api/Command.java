package io.fluxcapacitor.common.api;

import io.fluxcapacitor.common.Guarantee;

public abstract class Command extends Request {
    public abstract Guarantee getGuarantee();
}
