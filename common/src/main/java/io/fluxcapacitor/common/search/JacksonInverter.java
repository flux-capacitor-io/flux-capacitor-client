/*
 * Copyright (c) 2016-2021 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.common.search;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.search.Document.Entry;
import io.fluxcapacitor.common.search.Document.EntryType;
import io.fluxcapacitor.common.serialization.NullCollectionsAsEmptyModule;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
public class JacksonInverter implements Inverter<JsonNode> {
    public static JsonMapper defaultObjectMapper = JsonMapper.builder()
            .findAndAddModules().addModule(new NullCollectionsAsEmptyModule())
            .disable(FAIL_ON_EMPTY_BEANS).disable(WRITE_DATES_AS_TIMESTAMPS).disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .nodeFactory(withExactBigDecimals(true)).build();

    @Getter
    private final ObjectMapper objectMapper;
    private final JsonFactory jsonFactory;
    private final JsonNodeFactory nodeFactory;

    public JacksonInverter() {
        this(defaultObjectMapper);
    }

    public JacksonInverter(ObjectMapper objectMapper) {
        this.jsonFactory = objectMapper.getFactory();
        this.nodeFactory = objectMapper.getNodeFactory();
        this.objectMapper = objectMapper;
    }

    /*
        Inversion
     */

    @Override
    public Document toDocument(Data<byte[]> data, String id, String collection, Instant timestamp) {
        if (!"application/json".equals(data.getFormat())) {
            throw new IllegalArgumentException("Only json inversion is supported");
        }
        return new Document(id, data.getType(), data.getRevision(), collection,
                            timestamp, invert(data.getValue(), "", new LinkedHashMap<>()));
    }

    @SneakyThrows
    protected Map<Entry, List<String>> invert(byte[] json, String path, Map<Entry, List<String>> valueMap) {
        try (JsonParser parser = jsonFactory.createParser(json)) {
            JsonToken token = parser.nextToken();
            if (token != null) {
                processToken(token, valueMap, path, parser);
            }
        }
        return valueMap;
    }

    @SneakyThrows
    protected JsonToken processToken(JsonToken token, Map<Entry, List<String>> valueMap, String path,
                                     JsonParser parser) {
        switch (token) {
            case START_ARRAY:
                parseArray(parser, valueMap, path);
                break;
            case START_OBJECT:
                parseObject(parser, valueMap, path);
                break;
            default:
                registerValue(getEntryType(token), parser.getText(), path, valueMap);
                break;
        }
        return parser.nextToken();
    }

    protected EntryType getEntryType(JsonToken token) {
        switch (token) {
            case VALUE_STRING:
                return EntryType.TEXT;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return EntryType.NUMERIC;
            case VALUE_TRUE:
            case VALUE_FALSE:
                return EntryType.BOOLEAN;
            case VALUE_NULL:
                return EntryType.NULL;
        }
        throw new IllegalArgumentException("Unsupported value token: " + token);
    }

    protected void registerValue(EntryType type, String value, String path, Map<Entry, List<String>> valueMap) {
        List<String> locations = valueMap.computeIfAbsent(new Entry(type, value), key -> new ArrayList<>());
        if (!isBlank(path)) {
            locations.add(path);
        }
    }

    @SneakyThrows
    private void parseArray(JsonParser parser, Map<Entry, List<String>> valueMap, String root) {
        JsonToken token = parser.nextToken();
        if (token.isStructEnd()) {
            registerValue(EntryType.EMPTY_ARRAY, "[]", root, valueMap);
        } else {
            root = root.isEmpty() ? root : root + "/";
            for (int i = 0; !token.isStructEnd(); i++) {
                token = processToken(token, valueMap, root + i, parser);
            }
        }
    }

    @SneakyThrows
    protected void parseObject(JsonParser parser, Map<Entry, List<String>> valueMap, String root) {
        JsonToken token = parser.nextToken();
        if (token.isStructEnd()) {
            registerValue(EntryType.EMPTY_OBJECT, "{}", root, valueMap);
        } else {
            root = root.isEmpty() ? root : root + "/";
            String path = root;
            while (!token.isStructEnd()) {
                if (token == JsonToken.FIELD_NAME) {
                    String fieldName = parser.getCurrentName();
                    if (SearchUtils.isInteger(fieldName)) {
                        fieldName = "\"" + fieldName + "\"";
                    }
                    path = root + fieldName;
                    token = parser.nextToken();
                    continue;
                }
                token = processToken(token, valueMap, path, parser);
            }
        }
    }

    protected String escapeFieldName(String fieldName) {
        if (StringUtils.isNumeric(fieldName)) {
            try {
                Integer.valueOf(fieldName);
                fieldName = "\"" + fieldName + "\"";
            } catch (Exception ignored) {
            }
        }
        fieldName = fieldName.replace("/", "\\/");
        return fieldName;
    }

    /*
        Reversion
     */

    @Override
    @SuppressWarnings("unchecked")
    public JsonNode fromDocument(Document document) {
        Map<Entry, List<String>> entries = document.getEntries();
        if (entries.isEmpty()) {
            return NullNode.getInstance();
        }
        Map<String, Object> tree = new TreeMap<>();
        for (Map.Entry<Entry, List<String>> entry : entries.entrySet()) {
            JsonNode valueNode = toJsonNode(entry.getKey());
            List<String> paths = entry.getValue();
            if (paths.isEmpty()) {
                return valueNode;
            }
            paths.forEach(path -> {
                Map<String, Object> parent = tree;
                Iterator<String> iterator = Arrays.stream(path.split("/")).iterator();
                while (iterator.hasNext()) {
                    String segment = iterator.next();
                    if (iterator.hasNext()) {
                        parent = (Map<String, Object>) parent.computeIfAbsent(segment, s -> new TreeMap<>());
                    } else {
                        Object existing = parent.put(segment, valueNode);
                        if (existing != null) {
                            log.warn("Multiple entries share the same pointer: {} and {}", existing, valueNode);
                        }
                    }
                }
            });
        }
        return toJsonNode(tree);
    }

    protected JsonNode toJsonNode(Object struct) {
        if (struct instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked") SortedMap<String, Object> map = (SortedMap<String, Object>) struct;
            return map.keySet().stream().findFirst().map(firstKey -> SearchUtils.asInteger(firstKey).<JsonNode>map(
                    arrayIndex -> new ArrayNode(
                            nodeFactory, map.values().stream().map(this::toJsonNode).collect(toList())))
                    .orElseGet(() -> new ObjectNode(nodeFactory, map.entrySet().stream().collect(
                            toMap(e -> {
                                String key = e.getKey();
                                if (key.startsWith("\"") && key.endsWith("\"")) {
                                    key = key.substring(1, key.length() - 1);
                                }
                                return key;
                            }, e -> toJsonNode(e.getValue())))))).orElse(NullNode.getInstance());
        }
        if (struct instanceof JsonNode) {
            return (JsonNode) struct;
        }
        throw new IllegalArgumentException("Unrecognized structure: " + struct);
    }

    protected JsonNode toJsonNode(Entry entry) {
        switch (entry.getType()) {
            case TEXT:
                return new TextNode(entry.getValue());
            case NUMERIC:
                return new DecimalNode(new BigDecimal(entry.getValue()));
            case BOOLEAN:
                return BooleanNode.valueOf(Boolean.parseBoolean(entry.getValue()));
            case NULL:
                return NullNode.getInstance();
            case EMPTY_ARRAY:
                return new ArrayNode(nodeFactory);
            case EMPTY_OBJECT:
                return new ObjectNode(nodeFactory);
        }
        throw new IllegalArgumentException("Unrecognized entry type: " + entry.getType());
    }
}
