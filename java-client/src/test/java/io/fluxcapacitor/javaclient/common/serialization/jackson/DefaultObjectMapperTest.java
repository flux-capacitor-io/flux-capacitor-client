package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer.defaultObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultObjectMapperTest {

    @Test
    @SneakyThrows
    public void testIncludeNullCollectionsAsEmptyButExcludeOtherNulls() {
        TestModel mappedEmptyObject = defaultObjectMapper.convertValue(new TestModel(), TestModel.class);
        assertEquals(defaultObjectMapper.writeValueAsString(mappedEmptyObject), "{\"list\":[],\"set\":[]}");
    }

    @Data
    private static class TestModel {
        String string;
        List<String> list;
        Set<String> set;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<String> ignoredList;
    }
}
