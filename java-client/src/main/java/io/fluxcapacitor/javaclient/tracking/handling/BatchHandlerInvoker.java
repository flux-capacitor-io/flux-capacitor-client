package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;

import java.lang.reflect.Executable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public class BatchHandlerInvoker extends HandlerInspector.MethodHandlerInvoker<DeserializingMessage> {

    public static boolean handlesBatch(Executable method) {
        return method.getParameterCount() > 0
                && List.class.isAssignableFrom(method.getParameters()[0].getType());
    }

    private final Class<?> elementType;
    private final Map<Object, LinkedHashMap<DeserializingMessage, CompletableFuture<Object>>> batches =
            new ConcurrentHashMap<>();

    public BatchHandlerInvoker(Executable executable, Class<?> enclosingType,
                               List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        super(executable, enclosingType, parameterResolvers);
        if (!handlesBatch(executable)) {
            throw new IllegalArgumentException(format("Delegate does not handle Collection types: %s", executable));
        }
        this.elementType = getListElementType(executable);
    }

    @Override
    public Object invoke(Object target, DeserializingMessage message) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        Map<DeserializingMessage, CompletableFuture<Object>> batch =
                batches.computeIfAbsent(target, t -> new LinkedHashMap<>());
        batch.put(message, result);
        return result;
    }

    @Override
    public void onEndOfBatch() {
        try {
            batches.forEach((target, batch) -> {
                List<CompletableFuture<Object>> futures = new ArrayList<>(batch.values());
                try {
                    DeserializingMessage firstMessage = batch.keySet().stream().findFirst()
                            .orElseThrow(() -> new IllegalStateException("expected at least one value"));
                    List<Object> payloads =
                            new ArrayList<>(batch.keySet()).stream().map(DeserializingMessage::getPayload)
                                    .collect(toList());
                    DeserializingMessage merged = new DeserializingMessage(
                            new DeserializingObject<>(firstMessage.getSerializedObject(), () -> payloads),
                            firstMessage.getMessageType());

                    Object listResult;
                    DeserializingMessage previous = DeserializingMessage.getCurrent();
                    try {
                        DeserializingMessage.setCurrent(firstMessage);
                        listResult = super.invoke(target, merged);
                    } finally {
                        DeserializingMessage.setCurrent(previous);
                    }

                    if (listResult instanceof Collection<?>) {
                        List<?> results = new ArrayList<>((Collection<?>) listResult);
                        if (results.size() != futures.size()) {
                            throw new IllegalStateException(
                                    format("Number of results from method (%s) does not match number of handled messages (%s)",
                                           results.size(), futures.size()));
                        }
                        for (int i = 0; i < results.size(); i++) {
                            Object r = results.get(i);
                            CompletableFuture<Object> future = futures.get(i);
                            if (r instanceof CompletionStage<?>) {
                                ((CompletionStage<?>) r).whenComplete((o, e) -> {
                                    if (e == null) {
                                        future.complete(o);
                                    } else {
                                        future.completeExceptionally(e);
                                    }
                                });
                            } else {
                                future.complete(r);
                            }
                        }
                    } else {
                        futures.forEach(f -> f.complete(listResult));
                    }
                } catch (Exception e) {
                    futures.forEach(f -> f.completeExceptionally(e));
                }
            });
        } finally {
            batches.clear();
        }
    }

    @Override
    protected Class<?> getPayloadType() {
        return elementType;
    }

    @Override
    protected Predicate<DeserializingMessage> getMatcher(Executable executable,
                                                         List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        Class<?> elementType = getListElementType(executable);
        return d -> elementType.isAssignableFrom(d.getPayloadClass());
    }

    private static Class<?> getListElementType(Executable method) {
        Type type = method.getGenericParameterTypes()[0];
        if (type instanceof ParameterizedType) {
            Type elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
            if (elementType instanceof WildcardType) {
                Type[] upperBounds = ((WildcardType) elementType).getUpperBounds();
                elementType = upperBounds.length > 0 ? upperBounds[0] : null;
            }
            return elementType instanceof Class<?> ? (Class<?>) elementType : Object.class;
        }
        return Object.class;
    }
}
