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

import me.kcra.takenaka.accessor.platform.MapperPlatform;
import me.kcra.takenaka.accessor.platform.MapperPlatforms;
import me.kcra.takenaka.accessor.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * A multi-version multi-namespace constructor mapping.
 *
 * @author Matouš Kučera
 */
public class ConstructorMapping {
    /**
     * The parent class mapping.
     */
    private final ClassMapping parent;

    /**
     * The index of the declaration in the accessor model.
     */
    private final int index;

    /**
     * The mappings, a map of namespace-mapping maps keyed by version.
     */
    private final Map<String, Map<String, String[]>> mappings;

    /**
     * Constructs a new {@link FieldMapping} without any initial mappings.
     *
     * @param parent the parent class mapping
     * @param index the index of the declaration in the accessor model
     */
    public ConstructorMapping(@NotNull ClassMapping parent, int index) {
        this(parent, index, new HashMap<>());
    }

    /**
     * Constructs a new {@link FieldMapping} with pre-defined mappings.
     *
     * @param parent the parent class mapping
     * @param index the index of the declaration in the accessor model
     * @param mappings the mappings, a map of namespace-mapping maps keyed by version
     */
    public ConstructorMapping(
            @NotNull ClassMapping parent,
            int index,
            @NotNull Map<String, Map<String, String[]>> mappings
    ) {
        this.parent = parent;
        this.index = index;
        this.mappings = mappings;
    }

    /**
     * Gets mappings by version.
     *
     * @param version the version
     * @return the mappings, a map of namespace-mapping maps; null if the version is not mapped
     */
    public @Nullable Map<String, String[]> getMappings(@NotNull String version) {
        return mappings.get(version);
    }

    /**
     * Gets mapped constructor parameter types by the version and namespaces.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the types, null if it's not mapped
     */
    public @Nullable String[] getName(@NotNull String version, @NotNull String... namespaces) {
        final Map<String, String[]> versionMappings = getMappings(version);
        if (versionMappings == null) {
            return null;
        }

        for (final String namespace : namespaces) {
            final String[] types = versionMappings.get(namespace);
            if (types != null) {
                return types;
            }
        }
        return null;
    }

    /**
     * Gets mapped constructor parameter types by the version and namespaces,
     * and attempts to find a constructor in the parent class reflectively using them.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param loader the class loader used in the parent class lookup
     * @param version the version
     * @param namespaces the namespaces
     * @return the constructor, null if it's not mapped
     */
    public @Nullable Constructor<?> getConstructor(@NotNull ClassLoader loader, @NotNull String version, @NotNull String... namespaces) {
        final Class<?> clazz = parent.getClass(loader, version, namespaces);
        if (clazz == null) {
            return null;
        }

        final String[] types = getName(version, namespaces);
        if (types == null) {
            return null;
        }

        final Class<?>[] paramClasses = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) {
            final Class<?> paramClass = MethodMapping.parseClass(loader, types[i]);
            if (paramClass == null) {
                return null;
            }

            paramClasses[i] = paramClass;
        }

        try {
            return clazz.getConstructor(paramClasses);
        } catch (NoSuchMethodException ignored) {
            try {
                final Constructor<?> ctor = clazz.getDeclaredConstructor(paramClasses);
                ctor.setAccessible(true);

                return ctor;
            } catch (NoSuchMethodException ignored2) {
            }
        }
        return null;
    }

    /**
     * Gets mapped constructor parameter types by the version and namespaces,
     * and attempts to find a constructor in the parent class reflectively using them.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.<br>
     * The parent class is resolved using the current thread's context class loader.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the constructor, null if it's not mapped
     */
    public @Nullable Constructor<?> getConstructor(@NotNull String version, @NotNull String... namespaces) {
        return getConstructor(Thread.currentThread().getContextClassLoader(), version, namespaces);
    }

    /**
     * Gets mapped constructor parameter types by the version and namespaces of the supplied {@link MapperPlatform},
     * and attempts to find a constructor in the parent class reflectively using them.
     * <p>
     * The parent class is resolved using the platform's preferred class loader.
     *
     * @param platform the platform
     * @return the constructor, null if it's not mapped
     */
    public @Nullable Constructor<?> getConstructor(@NotNull MapperPlatform platform) {
        return getConstructor(platform.getClassLoader(), platform.getVersion(), platform.getMappingNamespaces());
    }

    /**
     * Gets mapped constructor parameter types by the version and namespaces of the current {@link MapperPlatform},
     * and attempts to find a constructor in the parent class reflectively using them.
     * <p>
     * The parent class is resolved using the platform's preferred class loader.
     *
     * @return the constructor, null if it's not mapped
     */
    public @Nullable Constructor<?> getConstructor() {
        return getConstructor(MapperPlatforms.getCurrentPlatform());
    }

    /**
     * Gets mapped constructor parameter types by the version and namespaces,
     * attempts to find a constructor reflectively using them and creates a {@link MethodHandle} if successful.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param loader the class loader used in the parent class lookup
     * @param version the version
     * @param namespaces the namespaces
     * @return the constructor handle, null if it's not mapped
     */
    public @Nullable MethodHandle getConstructorHandle(@NotNull ClassLoader loader, @NotNull String version, @NotNull String... namespaces) {
        final Constructor<?> ctor = getConstructor(loader, version, namespaces);
        if (ctor == null) {
            return null;
        }

        try {
            return MethodHandles.lookup().unreflectConstructor(ctor);
        } catch (IllegalAccessException e) {
            ExceptionUtil.sneakyThrow(e);
        }

        return null;
    }

    /**
     * Gets mapped constructor parameter types by the version and namespaces,
     * attempts to find a constructor reflectively using them and creates a {@link MethodHandle} if successful.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.<br>
     * The parent class is resolved using the current thread's context class loader.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the constructor handle, null if it's not mapped
     */
    public @Nullable MethodHandle getConstructorHandle(@NotNull String version, @NotNull String... namespaces) {
        return getConstructorHandle(Thread.currentThread().getContextClassLoader(), version, namespaces);
    }

    /**
     * Gets mapped constructor parameter types by the version and namespaces of the supplied {@link MapperPlatform},
     * attempts to find a constructor reflectively using them and creates a {@link MethodHandle} if successful.
     * <p>
     * The parent class is resolved using the platform's preferred class loader.
     *
     * @param platform the platform
     * @return the constructor handle, null if it's not mapped
     */
    public @Nullable MethodHandle getConstructorHandle(@NotNull MapperPlatform platform) {
        return getConstructorHandle(platform.getClassLoader(), platform.getVersion(), platform.getMappingNamespaces());
    }

    /**
     * Gets mapped constructor parameter types by the version and namespaces of the current {@link MapperPlatform},
     * attempts to find a constructor reflectively using them and creates a {@link MethodHandle} if successful.
     * <p>
     * The parent class is resolved using the platform's preferred class loader.
     *
     * @return the constructor handle, null if it's not mapped
     */
    public @Nullable MethodHandle getConstructorHandle() {
        return getConstructorHandle(MapperPlatforms.getCurrentPlatform());
    }

    /**
     * Puts a new mapping into this {@link ConstructorMapping}.
     * <p>
     * <strong>This is only for use in generated code.</strong>
     *
     * @param namespace the mapping's namespace
     * @param versions the versions which include the mapping
     * @param types the mapped parameter types
     * @return this {@link ConstructorMapping}
     */
    @ApiStatus.Internal
    @Contract("_, _, _ -> this")
    public @NotNull ConstructorMapping put(@NotNull String namespace, @NotNull String[] versions, @NotNull String... types) {
        for (final String version : versions) {
            mappings.computeIfAbsent(version, (k) -> new HashMap<>()).put(namespace, types);
        }
        return this;
    }

    /**
     * Gets the parent class mapping.
     *
     * @return the parent class mapping
     */
    public @NotNull ClassMapping getParent() {
        return this.parent;
    }

    /**
     * Gets the index of the declaration in the accessor model.
     *
     * @return the index
     */
    public int getIndex() {
        return this.index;
    }

    /**
     * Gets the mappings, a map of namespace-mapping maps keyed by version.
     *
     * @return the mappings
     */
    public @NotNull Map<String, Map<String, String[]>> getMappings() {
        return this.mappings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ConstructorMapping that = (ConstructorMapping) o;

        if (index != that.index) {
            return false;
        }
        if (parent != that.parent) { // use reference equality here to prevent stack overflow
            return false;
        }
        return mappings.equals(that.mappings);
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(parent); // use identity hash code here to prevent stack overflow
        result = 31 * result + index;
        result = 31 * result + mappings.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ConstructorMapping{" +
                "index=" + index +
                ", mappings=" + mappings +
                '}';
    }
}
