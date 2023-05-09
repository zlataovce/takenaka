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
import me.kcra.takenaka.accessor.platform.MapperPlatform;
import me.kcra.takenaka.accessor.platform.MapperPlatforms;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * A multi-version multi-namespace field mapping.
 *
 * @author Matouš Kučera
 */
@Data
@RequiredArgsConstructor
public class FieldMapping {
    /**
     * The parent class mapping.
     */
    private final ClassMapping parent;

    /**
     * The mappings, a map of namespace-mapping maps keyed by version.
     */
    private final Map<String, Map<String, String>> mappings;

    /**
     * Constructs a new {@link FieldMapping} without any initial mappings.
     *
     * @param parent the parent class mapping
     */
    public FieldMapping(@NotNull ClassMapping parent) {
        this(parent, new HashMap<>());
    }

    /**
     * Gets mappings by version.
     *
     * @param version the version
     * @return the mappings, a map of namespace-mapping maps; null if the version is not mapped
     */
    public @Nullable Map<String, String> getMappings(@NotNull String version) {
        return mappings.get(version);
    }

    /**
     * Gets a mapped field name by the version and namespaces.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the name, null if it's not mapped
     */
    public @Nullable String getName(@NotNull String version, @NotNull String... namespaces) {
        final Map<String, String> versionMappings = mappings.get(version);
        if (versionMappings == null) {
            return null;
        }

        for (final String namespace : namespaces) {
            final String name = versionMappings.get(namespace);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    /**
     * Gets a mapped field name by the version and namespaces, and attempts to find it in the parent class.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the field, null if it's not mapped
     */
    public @Nullable Field getField(@NotNull String version, @NotNull String... namespaces) {
        Class<?> clazz = parent.getClass(version, namespaces);
        if (clazz == null) {
            return null;
        }

        final String name = getName(version, namespaces);
        if (name == null) {
            return null;
        }

        try {
            return clazz.getField(name);
        } catch (NoSuchFieldException ignored) {
            do {
                try {
                    final Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);

                    return field;
                } catch (NoSuchFieldException ignored2) {
                }
            } while ((clazz = clazz.getSuperclass()) != null && clazz != Object.class);
        }
        return null;
    }

    /**
     * Gets a mapped field name by the version and namespaces of the supplied {@link MapperPlatform},
     * and attempts to find it in the parent class.
     *
     * @param platform the platform
     * @return the field, null if it's not mapped
     */
    public @Nullable Field getFieldByPlatform(@NotNull MapperPlatform platform) {
        return getField(platform.getVersion(), platform.getMappingNamespaces());
    }

    /**
     * Gets a mapped field name by the version and namespaces of the current {@link MapperPlatform},
     * and attempts to find it in the parent class.
     *
     * @return the field, null if it's not mapped
     */
    public @Nullable Field getFieldByCurrentPlatform() {
        return getFieldByPlatform(MapperPlatforms.getCurrentPlatform());
    }

    /**
     * Puts a new mapping into this {@link FieldMapping}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @param version the mapping's version
     * @param namespace the mapping's namespace
     * @param mapping the mapped name
     * @return this {@link FieldMapping}
     */
    @ApiStatus.Internal
    @Contract("_, _, _ -> this")
    public @NotNull FieldMapping put(@NotNull String version, @NotNull String namespace, @NotNull String mapping) {
        mappings.computeIfAbsent(version, (k) -> new HashMap<>()).put(namespace, mapping);
        return this;
    }
}
