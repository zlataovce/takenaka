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

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

/**
 * A supplier of results populated lazily.
 *
 * @param <T> the type of results supplied by this supplier
 * @author Matouš Kučera
 */
@Data(staticConstructor = "of")
public final class LazySupplier<T> implements Supplier<T> {
    /**
     * A default object for {@link #value} meaning that the result was not yet initialized.
     */
    private static final Object UNINITIALIZED_VALUE = new Object();

    /**
     * The wrapped {@link Supplier}, <strong>not lazy</strong>.
     */
    @ApiStatus.Internal
    private final Supplier<T> resultSupplier;

    /**
     * The cached supplier result, set to {@link #UNINITIALIZED_VALUE} if it wasn't yet initialized.
     */
    @SuppressWarnings("unchecked")
    @ApiStatus.Internal
    @EqualsAndHashCode.Exclude
    private volatile T value = (T) UNINITIALIZED_VALUE;

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
}
