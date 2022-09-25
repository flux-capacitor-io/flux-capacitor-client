package io.fluxcapacitor.common.handling;

import lombok.AllArgsConstructor;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

public interface HandlerInvoker {

    Object getTarget();

    Executable getMethod();

    boolean expectResult();

    boolean isPassive();

    default Object invoke() {
        return invoke((first, second) -> {
            @SuppressWarnings("unchecked")
            ArrayList<Object> combination = first instanceof ArrayList<?>
                    ? (ArrayList<Object>) first : first instanceof Collection<?>
                    ? new ArrayList<>((Collection<?>) first) : new ArrayList<>(Collections.singletonList(first));
            if (second instanceof Collection<?>) {
                combination.addAll((Collection<?>) second);
            } else {
                combination.add(second);
            }
            return combination;
        });
    }

    Object invoke(BiFunction<Object, Object, Object> combiner);

    default HandlerInvoker combine(HandlerInvoker second) {
        return new DelegatingHandlerInvoker(this) {
            @Override
            public Object invoke(BiFunction<Object, Object, Object> combiner) {
                return combiner.apply(delegate.invoke(), second.invoke());
            }
        };
    }

    @AllArgsConstructor
    abstract class DelegatingHandlerInvoker implements HandlerInvoker {
        protected final HandlerInvoker delegate;

        @Override
        public Object getTarget() {
            return delegate.getTarget();
        }

        @Override
        public Executable getMethod() {
            return delegate.getMethod();
        }

        @Override
        public boolean expectResult() {
            return delegate.expectResult();
        }

        @Override
        public boolean isPassive() {
            return delegate.isPassive();
        }
    }
}
