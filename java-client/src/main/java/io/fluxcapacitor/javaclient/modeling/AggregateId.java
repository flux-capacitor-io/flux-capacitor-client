package io.fluxcapacitor.javaclient.modeling;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Object that represents the identifier of a specific aggregate. This object allows aggregate ids to be prefixed with
 * a value to prevent clashes with other aggregates that may share the same functional id.
 *
 * @param <T> the aggregate type.
 */
public abstract class AggregateId<T> {
    /**
     * Returns the functional id of the aggregate
     */
    public abstract String getId();

    /**
     * Returns the prefix that is prepended to {@link #getId()} to create the full id under which this aggregate will be
     * stored. Eg, if the prefix of an AggregateId is "user-", and the id is "pete123", the aggregate will be stored
     * under "user-pete123".
     */
    public String getPrefix() {
        return "";
    }

    /**
     * Returns the aggregate's type. This may be a superclass of the actual aggregate.
     */
    public abstract Class<T> getType();

    /**
     * Returns true if the of this aggregate is case-sensitive. Defaults to true.
     */
    public boolean isCaseSensitiveId() {
        return true;
    }

    /**
     * Returns the id under which the aggregate will be stored.
     */
    @JsonIgnore
    public String getCompleteId() {
        return isCaseSensitiveId() ? getPrefix() + getId() : getPrefix() + getId().toLowerCase();
    }
}
