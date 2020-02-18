package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.api.Metadata;

import java.util.Iterator;
import java.util.Optional;
import java.util.ServiceLoader;

public interface UserProvider {
    
    UserProvider defaultUserSupplier = Optional.of(ServiceLoader.load(UserProvider.class)).map(
                ServiceLoader::iterator).filter(Iterator::hasNext).map(Iterator::next).orElse(null);
    
    User getActiveUser();
    
    User getSystemUser();
    
    User fromMetadata(Metadata metadata);
    
    boolean containsUser(Metadata metadata);

    Metadata removeFromMetadata(Metadata metadata);

    Metadata addToMetadata(Metadata metadata, User user);

}
