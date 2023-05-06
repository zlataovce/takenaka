package me.kcra.takenaka.accessor.mapping;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ClassMapping {
    private final Map<String, Map<String, String>> mappings = new HashMap<>();

    @Contract("_, _, _ -> this")
    public ClassMapping put(@NotNull String version, @NotNull String namespace, @NotNull String mapping) {
        mappings.computeIfAbsent(version, (k) -> new HashMap<>()).put(namespace, mapping);
        return this;
    }
}
