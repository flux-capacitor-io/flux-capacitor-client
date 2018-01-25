package io.fluxcapacitor.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.junit.Assert;
import org.junit.Test;

public class DataSerializationTest {

    @Test
    public void testSerializationIfValueIsSupplied() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TestData testData = new TestData(new Data<>(() -> "foo", "test", 1));
        String serializedObject = objectMapper.writeValueAsString(testData);
        Assert.assertEquals(objectMapper.readValue(serializedObject, TestData.class), testData);
    }

    @Value
    private static class TestData {
        Data<String> data;
    }

}