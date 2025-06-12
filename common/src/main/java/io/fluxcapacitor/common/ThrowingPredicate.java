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

package io.fluxcapacitor.common;

import java.util.Objects;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.ObjectUtils.call;

public interface ThrowingPredicate<T> {

    /**
     * Evaluates this predicate on the given argument.
     *
     * @param t the input argument
     * @return {@code true} if the input argument matches the predicate, otherwise {@code false}
     */
    boolean test(T t) throws Exception;

    /**
     * Returns a composed predicate that represents a short-circuiting logical AND of this predicate and another.  When
     * evaluating the composed predicate, if this predicate is {@code false}, then the {@code other} predicate is not
     * evaluated.
     *
     * <p>Any exceptions thrown during evaluation of either predicate are relayed
     * to the caller; if evaluation of this predicate throws an exception, the {@code other} predicate will not be
     * evaluated.
     *
     * @param other a predicate that will be logically-ANDed with this predicate
     * @return a composed predicate that represents the short-circuiting logical AND of this predicate and the
     * {@code other} predicate
     * @throws NullPointerException if other is null
     */
    default ThrowingPredicate<T> and(ThrowingPredicate<? super T> other) {
        Objects.requireNonNull(other);
        return (t) -> test(t) && other.test(t);
    }

    /**
     * Returns a predicate that represents the logical negation of this predicate.
     *
     * @return a predicate that represents the logical negation of this predicate
     */
    default ThrowingPredicate<T> negate() {
        return (t) -> !test(t);
    }

    /**
     * Returns a composed predicate that represents a short-circuiting logical OR of this predicate and another.  When
     * evaluating the composed predicate, if this predicate is {@code true}, then the {@code other} predicate is not
     * evaluated.
     *
     * <p>Any exceptions thrown during evaluation of either predicate are relayed
     * to the caller; if evaluation of this predicate throws an exception, the {@code other} predicate will not be
     * evaluated.
     *
     * @param other a predicate that will be logically-ORed with this predicate
     * @return a composed predicate that represents the short-circuiting logical OR of this predicate and the
     * {@code other} predicate
     * @throws NullPointerException if other is null
     */
    default ThrowingPredicate<T> or(ThrowingPredicate<? super T> other) {
        Objects.requireNonNull(other);
        return (t) -> test(t) || other.test(t);
    }

    /**
     * Returns a predicate that tests if two arguments are equal according to {@link Objects#equals(Object, Object)}.
     *
     * @param <T>       the type of arguments to the predicate
     * @param targetRef the object reference with which to compare for equality, which may be {@code null}
     * @return a predicate that tests if two arguments are equal according to {@link Objects#equals(Object, Object)}
     */
    static <T> ThrowingPredicate<T> isEqual(Object targetRef) {
        return (null == targetRef) ? Objects::isNull : targetRef::equals;
    }

    /**
     * Returns a predicate that is the negation of the supplied predicate. This is accomplished by returning result of
     * the calling {@code target.negate()}.
     *
     * @param <T>    the type of arguments to the specified predicate
     * @param target predicate to negate
     * @return a predicate that negates the results of the supplied predicate
     * @throws NullPointerException if target is null
     * @since 11
     */
    @SuppressWarnings("unchecked")
    static <T> ThrowingPredicate<T> not(ThrowingPredicate<? super T> target) {
        Objects.requireNonNull(target);
        return (ThrowingPredicate<T>) target.negate();
    }

    /**
     * Converts this {@code ThrowingPredicate} into a standard {@code Predicate}. Any exceptions thrown during the
     * evaluation of the predicate are wrapped and forcibly rethrown.
     *
     * @return a standard {@code Predicate} representation of this {@code ThrowingPredicate}
     */
    default Predicate<T> asPredicate() {
        return t -> call(() -> test(t));
    }
}
