/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
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

package me.kcra.takenaka.accessor.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An abstraction for platform-dependent calls.
 * <p>
 * Implementations can be set manually via {@link MapperPlatforms#setCurrentPlatform(MapperPlatform)}
 * or discovered via the Java Service Provider API.
 *
 * @author Matouš Kučera
 */
public interface MapperPlatform {
    /**
     * Creates a new {@link MapperPlatform} using the provided version and mapping namespaces.
     * <p>
     * The returned implementation uses the default semantics for {@link #getClassLoader()}.
     *
     * @param version the Minecraft version of the platform
     * @param mappingNamespaces the namespaces to be used for mapping
     * @return the {@link MapperPlatform}
     */
    static @NotNull MapperPlatform create(@NotNull String version, @NotNull String... mappingNamespaces) {
        return create(version, null, mappingNamespaces);
    }

    /**
     * Creates a new {@link MapperPlatform} using the provided version, mapping namespaces and class loader.
     *
     * @param version the Minecraft version of the platform
     * @param loader the class loader of the platform, defaults to the context class loader of the thread calling {@link #getClassLoader()} if null
     * @param mappingNamespaces the namespaces to be used for mapping
     * @return the {@link MapperPlatform}
     */
    static @NotNull MapperPlatform create(@NotNull String version, @Nullable ClassLoader loader, @NotNull String... mappingNamespaces) {
        return new MapperPlatformImpl(version, mappingNamespaces, loader);
    }

    /**
     * Determines whether the current environment is supported by this platform abstraction.
     * <p>
     * Methods that require interfacing with the underlying platform
     * should throw {@link UnsupportedOperationException}, if {@literal false} is returned.
     *
     * @return is this environment supported?
     */
    boolean isSupported();

    /**
     * Gets the Minecraft version of the current environment.
     *
     * @return the Minecraft version
     */
    @NotNull
    String getVersion();

    /**
     * Gets the preferred mapping namespaces of this platform.
     * <p>
     * The first value is expected to be the most preferred (resolved first), i.e. sorted by descending preference.
     *
     * @return the namespaces
     */
    @NotNull
    String[] getMappingNamespaces();

    /**
     * Gets the preferred class loader of this platform, used for looking up classes.
     * <p>
     * Returns the caller thread's context class loader by default.
     *
     * @return the class loader
     */
    default @NotNull ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
