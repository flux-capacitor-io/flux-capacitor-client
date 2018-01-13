package io.fluxcapacitor.common.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class MetadataTest {

    @Test(expected = UnsupportedOperationException.class)
    public void metadataIsImmutable() {
        Metadata metadata = Metadata.from("foo", "bar");
        metadata.put("bla", "bla");
    }

    @Test
    public void addingEntriesResultsInNewInstance() {
        Metadata metadata1 = Metadata.from("foo", "bar");
        Metadata metadata2 = metadata1.withEntry("bla", "bla");
        assertNotSame(metadata1, metadata2);
    }

    @Test
    public void existingEntriesMayBeOverwritten() {
        Metadata metadata = Metadata.from("foo", "bar");
        assertEquals(Metadata.from("foo", "barbar"), metadata.withEntry("foo", "barbar"));
    }
}