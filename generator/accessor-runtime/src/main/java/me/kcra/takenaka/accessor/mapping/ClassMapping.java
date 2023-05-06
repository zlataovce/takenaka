package me.kcra.takenaka.accessor.mapping;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import me.kcra.takenaka.accessor.MapperPlatform;
import me.kcra.takenaka.accessor.MapperPlatforms;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A multi-version multi-namespace class mapping.
 *
 * @author Matouš Kučera
 */
@Data
@RequiredArgsConstructor
public class ClassMapping {
    /**
     * The mappings, a map of namespace-mapping maps keyed by version.
     */
    private final Map<String, Map<String, String>> mappings;

    /**
     * Constructs a new {@link ClassMapping} without any initial mappings.
     */
    public ClassMapping() {
        this(new HashMap<>());
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
     * Gets a mapped class name by the version and namespaces, and attempts to resolve it in the current thread class loader.
     * <p>
     * Namespaces are iterated in order, the first mapped namespace's name is returned.
     *
     * @param version the version
     * @param namespaces the namespaces
     * @return the class, null if it's not mapped
     */
    public @Nullable Class<?> getClass(@NotNull String version, @NotNull String... namespaces) {
        final String name = getName(version, namespaces);
        if (name == null) {
            return null;
        }

        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    /**
     * Gets a mapped class name by the version and namespaces of the current {@link MapperPlatform},
     * and attempts to resolve it in the current thread class loader.
     *
     * @return the class, null if it's not mapped
     */
    public @Nullable Class<?> getClassByCurrentPlatform() {
        final MapperPlatform currentPlatform = MapperPlatforms.getCurrentPlatform();

        return getClass(currentPlatform.getVersion(), currentPlatform.getMappingNamespaces());
    }

    /**
     * Puts a new mapping into this {@link ClassMapping}.
     * <p>
     * <strong>This is only for use in generated code, it is not API and may be subject to change.</strong>
     *
     * @param version the mapping's version
     * @param namespace the mapping's namespace
     * @param mapping the mapped name
     * @return this {@link ClassMapping}
     */
    @ApiStatus.Internal
    @Contract("_, _, _ -> this")
    public @NotNull ClassMapping put(@NotNull String version, @NotNull String namespace, @NotNull String mapping) {
        mappings.computeIfAbsent(version, (k) -> new HashMap<>()).put(namespace, mapping);
        return this;
    }
}
