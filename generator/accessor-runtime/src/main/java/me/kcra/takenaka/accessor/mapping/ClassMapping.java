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
import me.kcra.takenaka.accessor.util.NameDescriptorPair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A multi-version multi-namespace class mapping.
 *
 * @author Matouš Kučera
 */
@Data
@RequiredArgsConstructor
public final class ClassMapping {
    /**
     * The mappings, a map of namespace-mapping maps keyed by version.
     * <p>
     * Fully qualified class name parts are delimited by <strong>dots, not slashes</strong> (non-internal names).
     */
    private final Map<String, Map<String, String>> mappings;

    /**
     * Field mappings of this class keyed by the name declared in the accessor model.
     */
    private final Map<String, FieldMapping> fields;

    /**
     * Constructor mappings of this class indexed as declared in the accessor model.
     */
    private final List<ConstructorMapping> constructors;

    /**
     * Method mappings of this class keyed by the name declared in the accessor model.
     */
    private final Map<String, List<MethodMapping>> methods;

    /**
     * Constructs a new {@link ClassMapping} without any initial mappings or members.
     */
    public ClassMapping() {
        this(new HashMap<>(), new HashMap<>(), new ArrayList<>(), new HashMap<>());
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
     * Gets a mapped class name by the version and namespaces.
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
     * Gets a mapped class name by the version and namespaces, and attempts to resolve it in the supplied class loader.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param loader the class loader used in the {@link Class#forName(String, boolean, ClassLoader)} lookup
     * @param version the version
     * @param namespaces the namespaces
     * @return the class, null if it's not mapped
     */
    public @Nullable Class<?> getClass(@NotNull ClassLoader loader, @NotNull String version, @NotNull String... namespaces) {
        final String name = getName(version, namespaces);
        if (name == null) {
            return null;
        }

        try {
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    /**
     * Gets a mapped class name by the version and namespaces, and attempts to resolve it in the current thread's context class loader.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the class, null if it's not mapped
     */
    public @Nullable Class<?> getClass(@NotNull String version, @NotNull String... namespaces) {
        return getClass(Thread.currentThread().getContextClassLoader(), version, namespaces);
    }

    /**
     * Gets a mapped class name by the version and namespaces of the supplied {@link MapperPlatform},
     * and attempts to resolve it in the current thread's context class loader.
     *
     * @param platform the platform
     * @return the class, null if it's not mapped
     */
    public @Nullable Class<?> getClass(@NotNull MapperPlatform platform) {
        return getClass(platform.getVersion(), platform.getMappingNamespaces());
    }

    /**
     * Gets a mapped class name by the version and namespaces of the current {@link MapperPlatform},
     * and attempts to resolve it in the current thread's context class loader.
     *
     * @return the class, null if it's not mapped
     */
    public @Nullable Class<?> getClazz() {
        return getClass(MapperPlatforms.getCurrentPlatform());
    }

    /**
     * Gets a field mapping by its name ({@link FieldMapping#getName()}).
     *
     * @param name the field name
     * @return the field mapping, null if not found
     */
    public @Nullable FieldMapping getField(@NotNull String name) {
        return fields.get(name);
    }

    /**
     * Finds a field mapping by its mapped name ({@link FieldMapping#getName(String, String...)}).
     *
     * @param version the version where the {@code name} is contained
     * @param namespace the namespace of the {@code name}
     * @param name the mapped name
     * @return the field mapping, null if not found
     */
    public @Nullable FieldMapping remapField(@NotNull String version, @NotNull String namespace, @NotNull String name) {
        for (final FieldMapping field : fields.values()) {
            if (name.equals(field.getName(version, namespace))) {
                return field;
            }
        }

        return null;
    }

    /**
     * Gets a constructor mapping by its index ({@link ConstructorMapping#getIndex()}).
     *
     * @param index the constructor index
     * @return the constructor mapping, null if index is out of bounds
     */
    public @Nullable ConstructorMapping getConstructor(int index) {
        if (index < 0 || index >= constructors.size()) {
            return null;
        }

        return constructors.get(index);
    }

    /**
     * Gets a method mapping by its name ({@link MethodMapping#getName()}) and overload index ({@link MethodMapping#getIndex()}).
     *
     * @param name the method name
     * @param index the method overload index
     * @return the method mapping, null if index is out of bounds
     */
    public @Nullable MethodMapping getMethod(@NotNull String name, int index) {
        final List<MethodMapping> overloads = methods.get(name);
        if (overloads == null) {
            return null;
        }

        if (index < 0 || index >= overloads.size()) {
            return null;
        }

        return overloads.get(index);
    }

    /**
     * Finds a method mapping by its mapped name and parameters ({@link MethodMapping#getName(String, String...)}).
     *
     * @param version the version where the {@code name} and {@code parameters} are contained
     * @param namespace the namespace of the {@code name} and {@code parameters}
     * @param namePair the mapped name and parameters
     * @return the method mapping, null if not found
     */
    public @Nullable MethodMapping remapMethod(
            @NotNull String version,
            @NotNull String namespace,
            @NotNull NameDescriptorPair namePair
    ) {
        for (final List<MethodMapping> overloads : methods.values()) {
            for (final MethodMapping method : overloads) {
                if (namePair.equals(method.getName(version, namespace))) {
                    return method;
                }
            }
        }

        return null;
    }

    /**
     * Finds a method mapping by its mapped name and parameters ({@link MethodMapping#getName(String, String...)}).
     *
     * @param version the version where the {@code name} and {@code parameters} are contained
     * @param namespace the namespace of the {@code name} and {@code parameters}
     * @param name the mapped name
     * @param parameters the mapped parameter types
     * @return the method mapping, null if not found
     */
    public @Nullable MethodMapping remapMethod(
            @NotNull String version,
            @NotNull String namespace,
            @NotNull String name,
            @NotNull String... parameters
    ) {
        return remapMethod(version, namespace, NameDescriptorPair.of(name, parameters));
    }

    /**
     * Puts a new mapping into this {@link ClassMapping}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @param namespace the mapping's namespace
     * @param mapping the mapped name
     * @param versions the versions which include the mapping
     * @return this {@link ClassMapping}
     */
    @ApiStatus.Internal
    @Contract("_, _, _ -> this")
    public @NotNull ClassMapping put(@NotNull String namespace, @NotNull String mapping, @NotNull String... versions) {
        for (final String version : versions) {
            mappings.computeIfAbsent(version, (k) -> new HashMap<>()).put(namespace, mapping);
        }
        return this;
    }

    /**
     * Puts a new field mapping into this {@link ClassMapping}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @param name the field name declared in the accessor model
     * @return the new {@link FieldMapping}
     */
    @ApiStatus.Internal
    public @NotNull FieldMapping putField(@NotNull String name) {
        final FieldMapping mapping = new FieldMapping(this, name);

        fields.put(name, mapping);
        return mapping;
    }

    /**
     * Puts a new constructor mapping into this {@link ClassMapping}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @return the new {@link ConstructorMapping}
     */
    @ApiStatus.Internal
    public @NotNull ConstructorMapping putConstructor() {
        final ConstructorMapping mapping = new ConstructorMapping(this, constructors.size());

        constructors.add(mapping);
        return mapping;
    }

    /**
     * Puts a new method mapping into this {@link ClassMapping}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @param name the method name declared in the accessor model
     * @return the new {@link MethodMapping}
     */
    @ApiStatus.Internal
    public @NotNull MethodMapping putMethod(@NotNull String name) {
        final List<MethodMapping> overloads = methods.computeIfAbsent(name, (k) -> new ArrayList<>());
        final MethodMapping mapping = new MethodMapping(this, name, overloads.size());

        overloads.add(mapping);
        return mapping;
    }
}
