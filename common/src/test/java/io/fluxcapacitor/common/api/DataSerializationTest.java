package io.fluxcapacitor.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataSerializationTest {

    @Test
    public void testSerializationIfValueIsSupplied() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TestData testData = new TestData(new Data<>(() -> "foo", "test", 1));
        String serializedObject = objectMapper.writeValueAsString(testData);
        assertEquals(objectMapper.readValue(serializedObject, TestData.class), testData);
    }

    @Value
    private static class TestData {
        Data<String> data;
    }

}