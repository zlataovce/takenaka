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

import lombok.*;
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
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public final class FieldMapping {
    /**
     * A value for {@link #constantValue} representing an uninitialized state.
     */
    private static final Object UNINITIALIZED_VALUE = new Object();

    /**
     * The parent class mapping.
     */
    @ToString.Exclude
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
     * Cached value from {@link #getConstantValue()}.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private volatile Object constantValue = UNINITIALIZED_VALUE;

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
     * @param loader the class loader used in the parent class lookup
     * @param version the version
     * @param namespaces the namespaces
     * @return the field, null if it's not mapped
     */
    public @Nullable Field getField(@NotNull ClassLoader loader, @NotNull String version, @NotNull String... namespaces) {
        Class<?> clazz = parent.getClass(loader, version, namespaces);
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
     * Gets a mapped field name by the version and namespaces,
     * and attempts to find it in the parent class reflectively.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.<br>
     * The parent class is resolved using the current thread's context class loader.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the field, null if it's not mapped
     */
    public @Nullable Field getField(@NotNull String version, @NotNull String... namespaces) {
        return getField(Thread.currentThread().getContextClassLoader(), version, namespaces);
    }

    /**
     * Gets a mapped field name by the version and namespaces of the supplied {@link MapperPlatform},
     * and attempts to find it in the parent class reflectively.
     * <p>
     * The parent class is resolved using the current thread's context class loader.
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
     * <p>
     * The parent class is resolved using the current thread's context class loader.
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
     * @param loader the class loader used in the parent class lookup
     * @param version the version
     * @param namespaces the namespaces
     * @return the field getter handle, null if it's not mapped
     */
    @SneakyThrows
    public @Nullable MethodHandle getFieldGetter(@NotNull ClassLoader loader, @NotNull String version, @NotNull String... namespaces) {
        final Field field = getField(loader, version, namespaces);
        if (field == null) {
            return null;
        }

        return MethodHandles.lookup().unreflectGetter(field);
    }

    /**
     * Gets a mapped field name by the version and namespaces,
     * attempts to find it in the parent class and creates a getter {@link MethodHandle} if successful.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.<br>
     * The parent class is resolved using the current thread's context class loader.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the field getter handle, null if it's not mapped
     */
    public @Nullable MethodHandle getFieldGetter(@NotNull String version, @NotNull String... namespaces) {
        return getFieldGetter(Thread.currentThread().getContextClassLoader(), version, namespaces);
    }

    /**
     * Gets a mapped field name by the version and namespaces of the supplied {@link MapperPlatform},
     * attempts to find it in the parent class and creates a getter {@link MethodHandle} if successful.
     * <p>
     * The parent class is resolved using the current thread's context class loader.
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
     * <p>
     * The parent class is resolved using the current thread's context class loader.
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
     * @param loader the class loader used in the parent class lookup
     * @param version the version
     * @param namespaces the namespaces
     * @return the field setter handle, null if it's not mapped
     */
    @SneakyThrows
    public @Nullable MethodHandle getFieldSetter(@NotNull ClassLoader loader, @NotNull String version, @NotNull String... namespaces) {
        final Field field = getField(loader, version, namespaces);
        if (field == null) {
            return null;
        }

        return MethodHandles.lookup().unreflectSetter(field);
    }

    /**
     * Gets a mapped field name by the version and namespaces,
     * attempts to find it in the parent class and creates a setter {@link MethodHandle} if successful.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.<br>
     * The parent class is resolved using the current thread's context class loader.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the field setter handle, null if it's not mapped
     */
    @SneakyThrows
    public @Nullable MethodHandle getFieldSetter(@NotNull String version, @NotNull String... namespaces) {
        return getFieldSetter(Thread.currentThread().getContextClassLoader(), version, namespaces);
    }

    /**
     * Gets a mapped field name by the version and namespaces of the supplied {@link MapperPlatform},
     * attempts to find it in the parent class and creates a setter {@link MethodHandle} if successful.
     * <p>
     * The parent class is resolved using the current thread's context class loader.
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
     * <p>
     * The parent class is resolved using the current thread's context class loader.
     *
     * @return the field setter handle, null if it's not mapped
     */
    public @Nullable MethodHandle getFieldSetter() {
        return getFieldSetter(MapperPlatforms.getCurrentPlatform());
    }

    /**
     * Gets a mapped field name by the version and namespaces of the current {@link MapperPlatform},
     * attempts to find it in the parent class, gets the value and caches it.
     * <p>
     * Despite the name of this method, it can also be used to cache non-final fields.<br>
     * The parent class is resolved using the current thread's context class loader.
     *
     * @return the value, null if it's not mapped or the field's value is null
     */
    @SneakyThrows
    public @Nullable Object getConstantValue() {
        if (constantValue != UNINITIALIZED_VALUE) {
            return constantValue;
        }

        final Field field = getField();
        if (field == null) {
            return null;
        }

        return (constantValue = field.get(null));
    }

    /**
     * Puts a new mapping into this {@link FieldMapping}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @param namespace the mapping's namespace
     * @param mapping the mapped name
     * @param versions the versions which include the mapping
     * @return this {@link FieldMapping}
     */
    @ApiStatus.Internal
    @Contract("_, _, _ -> this")
    public @NotNull FieldMapping put(@NotNull String namespace, @NotNull String mapping, @NotNull String... versions) {
        for (final String version : versions) {
            mappings.computeIfAbsent(version, (k) -> new HashMap<>()).put(namespace, mapping);
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FieldMapping that = (FieldMapping) o;

        if (parent != that.parent) { // use reference equality here to prevent stack overflow
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        return mappings.equals(that.mappings);
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(parent); // use identity hash code here to prevent stack overflow
        result = 31 * result + name.hashCode();
        result = 31 * result + mappings.hashCode();
        return result;
    }
}
