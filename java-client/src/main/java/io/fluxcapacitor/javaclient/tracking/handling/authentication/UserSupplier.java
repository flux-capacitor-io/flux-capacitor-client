package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.api.Metadata;

import java.util.Iterator;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

public interface UserSupplier extends Supplier<User> {
    
    UserSupplier defaultUserSupplier = Optional.of(ServiceLoader.load(UserSupplier.class)).map(
                ServiceLoader::iterator).filter(Iterator::hasNext).map(Iterator::next).orElse(null);
    
    User getSystemUser();
    
    <U extends User> U fromMetadata(Metadata metadata);

    Metadata removeFromMetadata(Metadata metadata);

    Metadata addToMetadata(Metadata metadata, User user);

    Metadata asMetadata(User user);
}
