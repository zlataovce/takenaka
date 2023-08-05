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

package me.kcra.takenaka.accessor.mapping;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A collection of {@link ClassMapping}s.
 *
 * @author Matouš Kučera
 */
@Data
@RequiredArgsConstructor
public final class MappingPool {
    /**
     * Class mappings of this pool keyed by the name declared in the accessor model.
     */
    private final Map<String, ClassMapping> mappings;

    /**
     * Constructs a new {@link MappingPool} without any initial mappings.
     */
    public MappingPool() {
        this(new HashMap<>());
    }

    /**
     * Gets a class mapping by its name ({@link ClassMapping#getName()}).
     *
     * @param name the class name
     * @return the class mapping, null if not found
     */
    public @Nullable ClassMapping getClass(@NotNull String name) {
        return mappings.get(name);
    }

    /**
     * Finds a class mapping by its mapped name.
     *
     * @param version the version where the {@code name} is contained
     * @param namespace the namespace of the {@code name}
     * @param name the mapped name of the class
     * @return the class mapping, null if not found
     */
    public @Nullable ClassMapping remapClass(
            @NotNull String version,
            @NotNull String namespace,
            @NotNull String name
    ) {
        for (final ClassMapping clazz : mappings.values()) {
            if (name.equals(clazz.getName(version, namespace))) {
                return clazz;
            }
        }

        return null;
    }

    /**
     * Puts a new mapping into this {@link MappingPool}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @param name the mapped name
     * @param mapping the {@link ClassMapping}
     * @return this {@link MappingPool}
     */
    @ApiStatus.Internal
    @Contract("_, _ -> this")
    public @NotNull MappingPool put(@NotNull String name, @NotNull ClassMapping mapping) {
        mappings.put(name, mapping);
        return this;
    }

    /**
     * Puts a new mapping into this {@link MappingPool}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @param mapping the {@link ClassMapping}
     * @return this {@link MappingPool}
     */
    @ApiStatus.Internal
    @Contract("_ -> this")
    public @NotNull MappingPool put(@NotNull ClassMapping mapping) {
        return put(mapping.getName(), mapping);
    }
}
