package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.api.Metadata;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MetadataSerializationTest {

    @Test
    public void testDeserializeMetadata() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Metadata in = Metadata.from("foo", "bar");
        Metadata out = objectMapper.readValue(objectMapper.writeValueAsBytes(in), Metadata.class);
        assertEquals(in, out);
    }
}
