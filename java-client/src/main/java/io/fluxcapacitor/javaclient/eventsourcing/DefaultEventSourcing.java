package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.caching.Cache;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handler.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@AllArgsConstructor
public class DefaultEventSourcing implements EventSourcing, HandlerInterceptor {

    private static final Map<Class, EventSourcingHandler> handlerCache = new ConcurrentHashMap<>();

    private final EventStore eventStore;
    private final Cache cache;
    private final ThreadLocal<List<DefaultEsModel<?>>> loadedModels = new ThreadLocal<>();

    @Override
    public <T> EsModel<T> load(String id, Class<T> modelType) {
        DefaultEsModel<T> model = new DefaultEsModel<>(getEventSourcingHandler(modelType), cache, eventStore, id,
                                                       loadedModels.get() == null);
        Optional.ofNullable(loadedModels.get()).ifPresent(models -> models.add(model));
        return model;
    }

    @SuppressWarnings("unchecked")
    protected <T> EventSourcingHandler<T> getEventSourcingHandler(Class<T> modelType) {
        return handlerCache.computeIfAbsent(modelType, AnnotatedEventSourcingHandler::new);
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function) {
        return command -> {
            List<DefaultEsModel<?>> models = new ArrayList<>();
            loadedModels.set(models);
            try {
                Object result = function.apply(command);
                try {
                    loadedModels.get().forEach(DefaultEsModel::commit);
                } catch (Exception e) {
                    throw new EventSourcingException(
                            String.format("Failed to commit applied events after handling %s", command), e);
                }
                return result;
            } catch (Exception e) {
                try {
                    loadedModels.get().forEach(DefaultEsModel::rollback);
                } catch (Exception rollbackException) {
                    log.error("Failed to roll back after handling {}", command, rollbackException);
                }
                throw e;
            } finally {
                loadedModels.remove();
            }
        };
    }
}
