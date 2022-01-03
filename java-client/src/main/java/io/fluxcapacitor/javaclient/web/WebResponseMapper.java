package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.api.Metadata;

@FunctionalInterface
public interface WebResponseMapper {

    WebResponse map(Object payload, Metadata metadata);

}
