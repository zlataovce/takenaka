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
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * A multi-version multi-namespace method mapping.
 *
 * @author Matouš Kučera
 */
@Data
@RequiredArgsConstructor
public final class MethodMapping {
    /**
     * The parent class mapping.
     */
    private final ClassMapping parent;

    /**
     * The accessed method name declared in the accessor model.
     */
    private final String name;

    /**
     * The overload index of the declaration (i.e. 0 is the first defined overloaded method, 1 is the second overload, ...).
     */
    private final int index;

    /**
     * The mappings, a map of namespace-mapping maps keyed by version.
     */
    private final Map<String, Map<String, NameDescriptorPair>> mappings;

    /**
     * Constructs a new {@link MethodMapping} without any initial mappings.
     *
     * @param parent the parent class mapping
     * @param name the accessed method name declared in the accessor model
     * @param index the overload index of the declaration
     */
    public MethodMapping(@NotNull ClassMapping parent, @NotNull String name, int index) {
        this(parent, name, index, new HashMap<>());
    }

    /**
     * Gets mappings by version.
     *
     * @param version the version
     * @return the mappings, a map of namespace-mapping maps; null if the version is not mapped
     */
    public @Nullable Map<String, NameDescriptorPair> getMappings(@NotNull String version) {
        return mappings.get(version);
    }

    /**
     * Gets the mapped method name and parameter types by the version and namespaces.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the name and parameters, null if they're not mapped
     */
    public @Nullable NameDescriptorPair getName(@NotNull String version, @NotNull String... namespaces) {
        final Map<String, NameDescriptorPair> versionMappings = getMappings(version);
        if (versionMappings == null) {
            return null;
        }

        for (final String namespace : namespaces) {
            final NameDescriptorPair namePair = versionMappings.get(namespace);
            if (namePair != null) {
                return namePair;
            }
        }
        return null;
    }

    /**
     * Creates a new {@link MethodMapping} that combines mappings of this mapping and {@code other}.
     * <p>
     * This mapping is given precedence over the other mapping when combining (if versions overlap), overload index is set to -1.
     *
     * @param other the other method mapping
     * @throws IllegalArgumentException if the mapping's parents are not the same
     * @return the new {@link MethodMapping}
     */
    @Contract(pure = true)
    public @NotNull MethodMapping chain(@NotNull MethodMapping other) {
        if (this.parent != other.parent) {
            throw new IllegalArgumentException("Could not chain method mappings, disassociated mapping parent");
        }

        final Map<String, Map<String, NameDescriptorPair>> newMappings = new HashMap<>(other.mappings.size());

        // add mappings of the other instance
        for (final Map.Entry<String, Map<String, NameDescriptorPair>> entry : other.mappings.entrySet()) {
            final Map<String, NameDescriptorPair> newMappings1 = new HashMap<>(entry.getValue().size());
            newMappings1.putAll(entry.getValue());

            newMappings.put(entry.getKey(), newMappings1);
        }

        // add mappings of this instance, overwrite existing
        for (final Map.Entry<String, Map<String, NameDescriptorPair>> entry : this.mappings.entrySet()) {
            newMappings.computeIfAbsent(entry.getKey(), (k) -> new HashMap<>(entry.getValue().size())).putAll(entry.getValue());
        }

        return new MethodMapping(this.parent, this.name, -1, newMappings);
    }

    /**
     * Gets the mapped method name and parameter types by the version and namespaces,
     * and attempts to find a method in the parent class reflectively using them.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the method, null if it's not mapped
     */
    public @Nullable Method getMethod(@NotNull String version, @NotNull String... namespaces) {
        Class<?> clazz = parent.getClass(version, namespaces);
        if (clazz == null) {
            return null;
        }

        final NameDescriptorPair namePair = getName(version, namespaces);
        if (namePair == null) {
            return null;
        }

        final String[] types = namePair.getParameters();
        final Class<?>[] paramClasses = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) {
            final Class<?> paramClass = parseClass(types[i]);
            if (paramClass == null) {
                return null;
            }

            paramClasses[i] = paramClass;
        }

        try {
            return clazz.getMethod(namePair.getName(), paramClasses);
        } catch (NoSuchMethodException ignored) {
            do {
                try {
                    final Method method = clazz.getDeclaredMethod(namePair.getName(), paramClasses);
                    method.setAccessible(true);

                    return method;
                } catch (NoSuchMethodException ignored2) {
                }
            } while ((clazz = clazz.getSuperclass()) != null && clazz != Object.class);
        }
        return null;
    }

    /**
     * Gets the mapped method name and parameter types by the version and namespaces of the supplied {@link MapperPlatform},
     * and attempts to find a method in the parent class reflectively using them.
     *
     * @param platform the platform
     * @return the method, null if it's not mapped
     */
    public @Nullable Method getMethod(@NotNull MapperPlatform platform) {
        return getMethod(platform.getVersion(), platform.getMappingNamespaces());
    }

    /**
     * Gets the mapped method name and parameter types by the version and namespaces of the current {@link MapperPlatform},
     * and attempts to find a method in the parent class reflectively using them.
     *
     * @return the method, null if it's not mapped
     */
    public @Nullable Method getMethod() {
        return getMethod(MapperPlatforms.getCurrentPlatform());
    }

    /**
     * Gets the mapped method name and parameter types by the version and namespaces,
     * attempts to find a method reflectively using them and creates a {@link MethodHandle} if successful.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the method handle, null if it's not mapped
     */
    @SneakyThrows
    public @Nullable MethodHandle getMethodHandle(@NotNull String version, @NotNull String... namespaces) {
        final Method method = getMethod(version, namespaces);
        if (method == null) {
            return null;
        }

        return MethodHandles.lookup().unreflect(method);
    }

    /**
     * Gets the mapped method name and parameter types by the version and namespaces of the supplied {@link MapperPlatform},
     * attempts to find a method reflectively using them and creates a {@link MethodHandle} if successful.
     *
     * @param platform the platform
     * @return the method handle, null if it's not mapped
     */
    public @Nullable MethodHandle getMethodHandle(@NotNull MapperPlatform platform) {
        return getMethodHandle(platform.getVersion(), platform.getMappingNamespaces());
    }

    /**
     * Gets the mapped method name and parameter types by the version and namespaces of the current {@link MapperPlatform},
     * attempts to find a method reflectively using them and creates a {@link MethodHandle} if successful.
     *
     * @return the method handle, null if it's not mapped
     */
    public @Nullable MethodHandle getMethodHandle() {
        return getMethodHandle(MapperPlatforms.getCurrentPlatform());
    }

    /**
     * Puts a new mapping into this {@link MethodMapping}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @param version the mapping's version
     * @param namespace the mapping's namespace
     * @param name the mapped method name
     * @param types the mapped parameter types
     * @return this {@link MethodMapping}
     */
    @ApiStatus.Internal
    @Contract("_, _, _, _ -> this")
    public @NotNull MethodMapping put(
            @NotNull String version,
            @NotNull String namespace,
            @NotNull String name,
            @NotNull String... types
    ) {
        mappings.computeIfAbsent(version, (k) -> new HashMap<>()).put(namespace, new NameDescriptorPair(name, types));
        return this;
    }

    /**
     * Parses a human-readable class name (java.lang.Integer, double, double[][], ...).
     *
     * @param className the class name
     * @return the class, null if the (element) type is not a primitive and couldn't be found using {@link Class#forName(String)}
     */
    @ApiStatus.Internal
    public static @Nullable Class<?> parseClass(@NotNull String className) {
        final String elementType = className.replace("[]", "");
        final int dimensions = (className.length() - elementType.length()) / 2;

        Class<?> element;
        switch (elementType) {
            case "boolean":
                element = boolean.class;
                break;
            case "byte":
                element = byte.class;
                break;
            case "short":
                element = short.class;
                break;
            case "int":
                element = int.class;
                break;
            case "long":
                element = long.class;
                break;
            case "float":
                element = float.class;
                break;
            case "double":
                element = double.class;
                break;
            case "char":
                element = char.class;
                break;
            case "void":
                element = void.class;
                break;
            default:
                try {
                    element = Class.forName(elementType);
                } catch (ClassNotFoundException ignored) {
                    return null;
                }
        }

        if (dimensions == 0) {
            return element;
        }
        return Array.newInstance(element, new int[dimensions]).getClass();
    }

    /**
     * A name and method parameters data holder.
     */
    @Data
    public static final class NameDescriptorPair {
        /**
         * The method name.
         */
        private final String name;

        /**
         * The method descriptor parameters.
         */
        private final String[] parameters;
    }
}
