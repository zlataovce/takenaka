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

package me.kcra.takenaka.accessor.platform;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable implementation of {@link MapperPlatform} with set values.
 *
 * @author Matouš Kučera
 */
@ApiStatus.Internal
public class MapperPlatformImpl implements MapperPlatform {
    private final String version;
    private final String[] mappingNamespaces;
    private final ClassLoader classLoader;

    protected MapperPlatformImpl(
            @NotNull String version,
            @NotNull String[] mappingNamespaces,
            @Nullable ClassLoader classLoader
    ) {
        this.version = version;
        this.mappingNamespaces = mappingNamespaces;
        this.classLoader = classLoader;
    }

    /**
     * Determines whether the current environment is supported by this platform abstraction.
     *
     * @return is this environment supported?
     */
    @Override
    public boolean isSupported() {
        return true;
    }

    /**
     * Gets the Minecraft version of the current environment.
     *
     * @return the Minecraft version
     */
    @Override
    public @NotNull String getVersion() {
        return this.version;
    }

    /**
     * Gets the preferred mapping namespaces of this platform.
     *
     * @return the namespaces
     */
    @Override
    public @NotNull String[] getMappingNamespaces() {
        return this.mappingNamespaces;
    }

    /**
     * Gets the preferred class loader of this platform, used for looking up classes.
     *
     * @return the class loader
     */
    @Override
    public @NotNull ClassLoader getClassLoader() {
        return this.classLoader == null ? Thread.currentThread().getContextClassLoader() : this.classLoader;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final MapperPlatformImpl that = (MapperPlatformImpl) o;
        return Objects.equals(version, that.version)
                && Arrays.equals(mappingNamespaces, that.mappingNamespaces)
                && Objects.equals(classLoader, that.classLoader);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version, classLoader);
        result = 31 * result + Arrays.hashCode(mappingNamespaces);
        return result;
    }

    @Override
    public String toString() {
        return "MapperPlatformImpl{" +
                "version='" + version + '\'' +
                ", mappingNamespaces=" + Arrays.toString(mappingNamespaces) +
                ", classLoader=" + classLoader +
                '}';
    }
}
