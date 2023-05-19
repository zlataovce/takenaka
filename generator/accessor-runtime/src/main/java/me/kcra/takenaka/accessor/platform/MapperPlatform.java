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

import org.jetbrains.annotations.NotNull;

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
     *
     * @return the namespaces
     */
    @NotNull
    String[] getMappingNamespaces();
}