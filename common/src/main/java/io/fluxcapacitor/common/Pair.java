package io.fluxcapacitor.common;

import lombok.Value;

@Value
public class Pair<T, U> {
    T first;
    U second;
}
