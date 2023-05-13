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
import lombok.SneakyThrows;
import me.kcra.takenaka.accessor.platform.MapperPlatform;
import me.kcra.takenaka.accessor.platform.MapperPlatforms;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
public final class FieldMapping {
    /**
     * The parent class mapping.
     */
    private final ClassMapping parent;

    /**
     * The accessed field name declared in the accessor model.
     */
    private final String name;

    /**
     * The mappings, a map of namespace-mapping maps keyed by version.
     */
    private final Map<String, Map<String, String>> mappings;

    /**
     * Constructs a new {@link FieldMapping} without any initial mappings.
     *
     * @param parent the parent class mapping
     * @param name the field name declared in the accessor model
     */
    public FieldMapping(@NotNull ClassMapping parent, @NotNull String name) {
        this(parent, name, new HashMap<>());
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
        final Map<String, String> versionMappings = getMappings(version);
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
     * Creates a new {@link FieldMapping} that combines mappings of this mapping and {@code other}.
     * <p>
     * This mapping is given precedence over the other mapping when combining (if versions overlap).
     *
     * @param other the other field mapping
     * @throws IllegalArgumentException if the mapping's parents are not the same
     * @return the new {@link FieldMapping}
     */
    @Contract(pure = true)
    public @NotNull FieldMapping chain(@NotNull FieldMapping other) {
        if (this.parent != other.parent) {
            throw new IllegalArgumentException("Could not chain field mappings, disassociated mapping parent");
        }

        final Map<String, Map<String, String>> newMappings = new HashMap<>(other.mappings.size());

        // add mappings of the other instance
        for (final Map.Entry<String, Map<String, String>> entry : other.mappings.entrySet()) {
            final Map<String, String> newMappings1 = new HashMap<>(entry.getValue().size());
            newMappings1.putAll(entry.getValue());

            newMappings.put(entry.getKey(), newMappings1);
        }

        // add mappings of this instance, overwrite existing
        for (final Map.Entry<String, Map<String, String>> entry : this.mappings.entrySet()) {
            newMappings.computeIfAbsent(entry.getKey(), (k) -> new HashMap<>(entry.getValue().size())).putAll(entry.getValue());
        }

        return new FieldMapping(this.parent, this.name, newMappings);
    }

    /**
     * Gets a mapped field name by the version and namespaces,
     * and attempts to find it in the parent class reflectively.
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
     * and attempts to find it in the parent class reflectively.
     *
     * @param platform the platform
     * @return the field, null if it's not mapped
     */
    public @Nullable Field getField(@NotNull MapperPlatform platform) {
        return getField(platform.getVersion(), platform.getMappingNamespaces());
    }

    /**
     * Gets a mapped field name by the version and namespaces of the current {@link MapperPlatform},
     * and attempts to find it in the parent class reflectively.
     *
     * @return the field, null if it's not mapped
     */
    public @Nullable Field getField() {
        return getField(MapperPlatforms.getCurrentPlatform());
    }

    /**
     * Gets a mapped field name by the version and namespaces,
     * attempts to find it in the parent class and creates a getter {@link MethodHandle} if successful.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the field getter handle, null if it's not mapped
     */
    @SneakyThrows
    public @Nullable MethodHandle getFieldGetter(@NotNull String version, @NotNull String... namespaces) {
        final Field field = getField(version, namespaces);
        if (field == null) {
            return null;
        }

        return MethodHandles.lookup().unreflectGetter(field);
    }

    /**
     * Gets a mapped field name by the version and namespaces of the supplied {@link MapperPlatform},
     * attempts to find it in the parent class and creates a getter {@link MethodHandle} if successful.
     *
     * @param platform the platform
     * @return the field getter handle, null if it's not mapped
     */
    public @Nullable MethodHandle getFieldGetter(@NotNull MapperPlatform platform) {
        return getFieldGetter(platform.getVersion(), platform.getMappingNamespaces());
    }

    /**
     * Gets a mapped field name by the version and namespaces of the current {@link MapperPlatform},
     * attempts to find it in the parent class and creates a getter {@link MethodHandle} if successful.
     *
     * @return the field getter handle, null if it's not mapped
     */
    public @Nullable MethodHandle getFieldGetter() {
        return getFieldGetter(MapperPlatforms.getCurrentPlatform());
    }

    /**
     * Gets a mapped field name by the version and namespaces,
     * attempts to find it in the parent class and creates a setter {@link MethodHandle} if successful.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the field setter handle, null if it's not mapped
     */
    @SneakyThrows
    public @Nullable MethodHandle getFieldSetter(@NotNull String version, @NotNull String... namespaces) {
        final Field field = getField(version, namespaces);
        if (field == null) {
            return null;
        }

        return MethodHandles.lookup().unreflectSetter(field);
    }

    /**
     * Gets a mapped field name by the version and namespaces of the supplied {@link MapperPlatform},
     * attempts to find it in the parent class and creates a setter {@link MethodHandle} if successful.
     *
     * @param platform the platform
     * @return the field setter handle, null if it's not mapped
     */
    public @Nullable MethodHandle getFieldSetter(@NotNull MapperPlatform platform) {
        return getFieldSetter(platform.getVersion(), platform.getMappingNamespaces());
    }

    /**
     * Gets a mapped field name by the version and namespaces of the current {@link MapperPlatform},
     * attempts to find it in the parent class and creates a setter {@link MethodHandle} if successful.
     *
     * @return the field setter handle, null if it's not mapped
     */
    public @Nullable MethodHandle getFieldSetter() {
        return getFieldSetter(MapperPlatforms.getCurrentPlatform());
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
