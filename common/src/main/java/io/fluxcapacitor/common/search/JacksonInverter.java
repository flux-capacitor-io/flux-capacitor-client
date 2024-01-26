/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.fluxcapacitor.common.ThrowingFunction;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.search.FacetEntry;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.common.search.Document.Entry;
import io.fluxcapacitor.common.search.Document.EntryType;
import io.fluxcapacitor.common.search.Document.Path;
import io.fluxcapacitor.common.serialization.JsonUtils;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.SearchUtils.asIntegerOrString;
import static io.fluxcapacitor.common.api.Data.JSON_FORMAT;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getMemberAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPropertyName;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;
import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@Getter(AccessLevel.PROTECTED)
@AllArgsConstructor
public class JacksonInverter implements Inverter<JsonNode> {

    private final JsonMapper objectMapper;
    private final ThrowingFunction<Object, String> summarizer;

    @SuppressWarnings("unused")
    public JacksonInverter() {
        this(JsonUtils.writer);
    }

    public JacksonInverter(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.summarizer = createSummarizer(this);
    }

    /*
        Summarize
     */

    protected static Function<Member, Boolean> searchIgnoreCache = memoize(
            m -> {
                Optional<? extends Annotation> result = getMemberAnnotation(m.getDeclaringClass(), m.getName(), SearchExclude.class)
                                .or(() -> ofNullable(getTypeAnnotation(m.getDeclaringClass(), SearchExclude.class)));
                return result
                        .map(a -> a instanceof SearchExclude s
                                ? s : a.annotationType().getAnnotation(SearchExclude.class))
                        .map(SearchExclude::value).orElse(false);
            });

    protected static ThrowingFunction<Object, String> createSummarizer(JacksonInverter inverter) {
        JacksonInverter summarizer = new JacksonInverter(inverter.objectMapper.rebuild().annotationIntrospector(
                new JacksonAnnotationIntrospector() {
                    @Override
                    public boolean hasIgnoreMarker(AnnotatedMember m) {
                        return super.hasIgnoreMarker(m) || searchIgnoreCache.apply(m.getMember());
                    }
                }).build(), o -> {
            throw new UnsupportedOperationException();
        });
        return value -> {
            var entries = summarizer.invert(summarizer.objectMapper.writeValueAsBytes(value));
            return entries.keySet().stream().map(Entry::asPhrase).distinct().collect(joining(" "));
        };
    }

    @SneakyThrows
    public String summarize(Object value) {
        return summarizer.apply(value);
    }

    /*
        Inversion
     */

    @Override
    @SneakyThrows
    public SerializedDocument toDocument(Object value, String type, int revision, String id, String collection,
                                         Instant timestamp, Instant end) {
        byte[] data = objectMapper.writeValueAsBytes(value);
        return new SerializedDocument(new Document(id, type, revision, collection,
                                                   timestamp, end, invert(data), () -> summarize(value),
                                                   getFacets(value)));
    }

    protected Set<FacetEntry> getFacets(Object value) {
        if (value == null) {
            return emptySet();
        }
        var properties = ReflectionUtils.getAnnotatedProperties(value.getClass(), Facet.class);
        return properties.stream().flatMap(p -> ofNullable(ReflectionUtils.getValue(p, value)).stream()
                        .flatMap(o -> getFacets(p, o))).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    protected Stream<FacetEntry> getFacets(AccessibleObject property, Object propertyValue) {
        if (propertyValue == null) {
            return Stream.empty();
        }
        if (propertyValue instanceof Collection<?> collection) {
            return collection.stream().flatMap(v -> getFacets(property, v));
        }
        String stringValue = propertyValue.toString();
        var customName = ReflectionUtils.getAnnotation(
                property, Facet.class).map(Facet::value).filter(s -> !s.isBlank());
        return stringValue.isBlank()
                ? Stream.empty() : Stream.of(new FacetEntry(
                        customName.orElseGet(() -> getPropertyName(property)), stringValue));
    }

    @SneakyThrows
    protected Map<Entry, List<Path>> invert(byte[] json) {
        Map<Entry, List<Path>> valueMap = new LinkedHashMap<>();
        try (JsonParser parser = objectMapper.getFactory().createParser(json)) {
            JsonToken token = parser.nextToken();
            if (token != null) {
                processToken(token, valueMap, "", parser);
            }
        }
        return valueMap;
    }

    @SneakyThrows
    protected JsonToken processToken(JsonToken token, Map<Entry, List<Path>> valueMap, String path,
                                     JsonParser parser) {
        switch (token) {
            case START_ARRAY -> parseArray(parser, valueMap, path);
            case START_OBJECT -> parseObject(parser, valueMap, path);
            default -> registerValue(getEntryType(token), parser.getText(), path, valueMap);
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

    protected void registerValue(EntryType type, String value, String path, Map<Entry, List<Path>> valueMap) {
        List<Path> locations = valueMap.computeIfAbsent(new Entry(type, value), key -> new ArrayList<>());
        if (!isBlank(path)) {
            locations.add(new Path(path));
        }
    }

    @SneakyThrows
    private void parseArray(JsonParser parser, Map<Entry, List<Path>> valueMap, String root) {
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
    protected void parseObject(JsonParser parser, Map<Entry, List<Path>> valueMap, String root) {
        JsonToken token = parser.nextToken();
        if (token.isStructEnd()) {
            registerValue(EntryType.EMPTY_OBJECT, "{}", root, valueMap);
        } else {
            root = root.isEmpty() ? root : root + "/";
            String path = root;
            while (!token.isStructEnd()) {
                if (token == JsonToken.FIELD_NAME) {
                    String fieldName = parser.getCurrentName();
                    fieldName = Path.escapeFieldName(fieldName);
                    path = root + fieldName;
                    token = parser.nextToken();
                    continue;
                }
                token = processToken(token, valueMap, path, parser);
            }
        }
    }

    /*
        Reversion
     */

    @Override
    @SuppressWarnings("unchecked")
    public Data<JsonNode> fromDocument(SerializedDocument serializedDocument) {
        if (JSON_FORMAT.equals(serializedDocument.getDocument().getFormat())) {
            return serializedDocument.getDocument().map(d -> getObjectMapper().readTree(d));
        }
        var document = serializedDocument.deserializeDocument();
        Map<Entry, List<Path>> entries = document.getEntries();
        if (entries.isEmpty()) {
            return toData(NullNode.getInstance(), serializedDocument);
        }
        Map<Object, Object> tree = new TreeMap<>();
        for (Map.Entry<Entry, List<Path>> entry : entries.entrySet()) {
            JsonNode valueNode = toJsonNode(entry.getKey());
            List<Path> paths = entry.getValue();
            if (paths.isEmpty()) {
                return toData(valueNode, serializedDocument);
            }
            paths.forEach(path -> {
                Map<Object, Object> parent = tree;
                Iterator<String> iterator = Path.split(path.getValue()).iterator();
                while (iterator.hasNext()) {
                    var segment = asIntegerOrString(iterator.next());
                    if (iterator.hasNext()) {
                        parent = (Map<Object, Object>) parent.computeIfAbsent(segment, s -> new TreeMap<>());
                    } else {
                        Object existing = parent.put(segment, valueNode);
                        if (existing != null) {
                            log.warn("Multiple entries share the same pointer: {} and {}", existing, valueNode);
                        }
                    }
                }
            });
        }
        return toData(toJsonNode(tree), serializedDocument);
    }

    protected Data<JsonNode> toData(JsonNode node, SerializedDocument document) {
        return new Data<>(node, document.getDocument().getType(),
                          document.getDocument().getRevision(), JSON_FORMAT);
    }

    protected JsonNode toJsonNode(Object struct) {
        if (struct instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked") SortedMap<Object, Object> map = (SortedMap<Object, Object>) struct;
            return map.keySet().stream().findFirst().<JsonNode>map(firstKey -> firstKey instanceof Integer
                    ? new ArrayNode(objectMapper.getNodeFactory(),
                                    map.values().stream().map(this::toJsonNode).collect(toList()))
                    : new ObjectNode(objectMapper.getNodeFactory(), map.entrySet().stream().collect(
                    toMap(e -> {
                        String key = e.getKey().toString();
                        key = Path.unescapeFieldName(key);
                        return key;
                    }, e -> toJsonNode(e.getValue()))))).orElse(NullNode.getInstance());
        }
        if (struct instanceof JsonNode) {
            return (JsonNode) struct;
        }
        throw new IllegalArgumentException("Unrecognized structure: " + struct);
    }

    protected JsonNode toJsonNode(Entry entry) {
        return switch (entry.getType()) {
            case TEXT -> new TextNode(entry.getValue());
            case NUMERIC -> new DecimalNode(new BigDecimal(entry.getValue()));
            case BOOLEAN -> BooleanNode.valueOf(Boolean.parseBoolean(entry.getValue()));
            case NULL -> NullNode.getInstance();
            case EMPTY_ARRAY -> new ArrayNode(objectMapper.getNodeFactory());
            case EMPTY_OBJECT -> new ObjectNode(objectMapper.getNodeFactory());
        };
    }
}
