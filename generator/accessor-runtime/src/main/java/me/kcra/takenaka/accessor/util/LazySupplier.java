/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023 Matous Kucera
 *
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.kcra.takenaka.accessor.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A supplier of results populated lazily.
 *
 * @param <T> the type of results supplied by this supplier
 * @author Matouš Kučera
 */
public final class LazySupplier<T> implements Supplier<T> {
    /**
     * A default object for {@link #value} meaning that the result was not yet initialized.
     */
    private static final Object UNINITIALIZED_VALUE = new Object();

    /**
     * The wrapped {@link Supplier}, <strong>not lazy</strong>.
     */
    private final Supplier<T> resultSupplier;

    /**
     * The cached supplier result, set to {@link #UNINITIALIZED_VALUE} if it wasn't yet initialized.
     */
    @SuppressWarnings("unchecked")
    private volatile T value = (T) UNINITIALIZED_VALUE;

    /**
     * Constructs a new {@link LazySupplier} with a given supplier.
     *
     * @param resultSupplier the supplier function that provides the result value
     */
    private LazySupplier(@NotNull Supplier<T> resultSupplier) {
        this.resultSupplier = resultSupplier;
    }

    /**
     * Creates a new {@link LazySupplier} with a given supplier.
     *
     * @param resultSupplier the supplier function that provides the result value
     * @param <T> the result type
     * @return the lazy supplier
     */
    public static <T> @NotNull LazySupplier<T> of(@NotNull Supplier<T> resultSupplier) {
        return new LazySupplier<>(resultSupplier);
    }

    /**
     * Gets a result.
     *
     * @return a result
     */
    @Override
    public T get() {
        if (!isInitialized()) {
            value = resultSupplier.get();
        }

        return value;
    }

    /**
     * Checks if this supplier has had its value initialized already.
     *
     * @return is it initialized?
     */
    public boolean isInitialized() {
        return value != UNINITIALIZED_VALUE;
    }

    /**
     * Returns the wrapped supplier.
     *
     * @return the wrapped supplier
     */
    @ApiStatus.Internal
    public @NotNull Supplier<T> getResultSupplier() {
        return this.resultSupplier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final LazySupplier<?> that = (LazySupplier<?>) o;
        return Objects.equals(resultSupplier, that.resultSupplier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resultSupplier);
    }
}
