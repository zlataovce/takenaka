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

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Standard {@link MapperPlatform} implementations.
 *
 * @author Matouš Kučera
 */
public enum MapperPlatforms implements MapperPlatform {
    /**
     * An abstraction for platforms that implement the Bukkit API (Spigot mappings).
     */
    BUKKIT {
        private String minecraftVersion = null;

        {
            try {
                final Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit", true, getClassLoader());
                try {
                    // Paper API
                    minecraftVersion = (String) bukkitClass.getMethod("getMinecraftVersion").invoke(null);
                } catch (NoSuchMethodException ignored) {
                    // normal Bukkit API
                    final String versionString = (String) bukkitClass.getMethod("getVersion").invoke(null);

                    final Pattern versionPattern = Pattern.compile("\\(MC: ([^)]+?)\\)");
                    final Matcher matcher = versionPattern.matcher(versionString);
                    if (!matcher.find()) {
                        throw new RuntimeException("Failed to find Minecraft version in version string " + versionString);
                    }

                    minecraftVersion = matcher.group(1);
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to get Minecraft version", e);
            } catch (ClassNotFoundException ignored) {
            }
        }

        @Override
        public boolean isSupported() {
            return minecraftVersion != null;
        }

        @Override
        public @NotNull String getVersion() {
            if (!isSupported()) {
                throw new UnsupportedOperationException("Bukkit is not supported by this environment");
            }
            return minecraftVersion;
        }

        @Override
        public @NotNull String[] getMappingNamespaces() {
            return new String[] { "spigot" };
        }
    },

    /**
     * An abstraction for NeoForge-based platforms (Mojang mappings).
     */
    NEOFORGE {
        private String minecraftVersion = null;

        {
            try {
                final Class<?> neoFormVersionClass = Class.forName(
                        "net.neoforged.neoforge.internal.versions.neoform.NeoFormVersion",
                        true, getClassLoader()
                );
                minecraftVersion = (String) neoFormVersionClass.getMethod("getMCVersion").invoke(null);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to get Minecraft version", e);
            } catch (ClassNotFoundException ignored) {
            }
        }

        @Override
        public boolean isSupported() {
            return minecraftVersion != null;
        }

        @Override
        public @NotNull String getVersion() {
            if (!isSupported()) {
                throw new UnsupportedOperationException("NeoForge is not supported by this environment");
            }
            return minecraftVersion;
        }

        @Override
        public @NotNull String[] getMappingNamespaces() {
            return new String[] { "mojang" };
        }
    },

    /**
     * An abstraction for Forge-based platforms (Searge mappings).
     */
    FORGE {
        private String minecraftVersion = null;

        {
            try {
                try {
                    // Flattening versions
                    final Class<?> mcpVersionClass = Class.forName("net.minecraftforge.versions.mcp.MCPVersion", true, getClassLoader());
                    minecraftVersion = (String) mcpVersionClass.getMethod("getMCVersion").invoke(null);
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                    // Legacy versions
                    final Class<?> forgeClass = Class.forName("net.minecraftforge.common.MinecraftForge", true, getClassLoader());
                    minecraftVersion = (String) forgeClass.getField("MC_VERSION").get(null);
                }
            } catch (NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to get Minecraft version", e);
            } catch (ClassNotFoundException ignored) {
            }
        }

        @Override
        public boolean isSupported() {
            return minecraftVersion != null;
        }

        @Override
        public @NotNull String getVersion() {
            if (!isSupported()) {
                throw new UnsupportedOperationException("Forge is not supported by this environment");
            }
            return minecraftVersion;
        }

        @Override
        public @NotNull String[] getMappingNamespaces() {
            return new String[] { "searge" };
        }
    };

    /**
     * The SPI loader, uses the class loader of this class.
     */
    private static final ServiceLoader<MapperPlatform> LOADER = ServiceLoader.load(MapperPlatform.class, MapperPlatforms.class.getClassLoader());

    /**
     * The current mapper platform implementation.
     */
    private static volatile MapperPlatform CURRENT = null;

    /**
     * Gets the current mapper platform, discovering a supported one, if not set.
     *
     * @return the current mapper platform
     */
    public static @NotNull MapperPlatform getCurrentPlatform() {
        if (CURRENT == null) {
            CURRENT = findSupportedPlatform();
        }

        return CURRENT;
    }

    /**
     * Sets the current mapper platform, useful for manual specification.
     * <p>
     * <strong>This should only be used in the initialization phase of the mod/plugin/...</strong><br>
     * (no locking is performed on {@link #CURRENT} writes).
     *
     * @param platform the platform
     */
    public static void setCurrentPlatform(@NotNull MapperPlatform platform) {
        CURRENT = platform;
    }

    /**
     * Tries to find a supported mapper platform in this enum and subsequently via SPI.
     *
     * @throws RuntimeException if none were discovered
     * @return the mapper platform
     */
    private static @NotNull MapperPlatform findSupportedPlatform() {
        return Stream.concat(Arrays.stream(values()), StreamSupport.stream(LOADER.spliterator(), false))
                .filter(MapperPlatform::isSupported)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Failed to find a supported platform, specify one with MapperPlatforms#setCurrentPlatform manually"));
    }

    /**
     * Gets the preferred class loader of the platform, used for looking up classes.
     * <p>
     * Returns the class loader of this class ({@link MapperPlatforms}) by default.
     *
     * @return the class loader
     */
    @Override
    public @NotNull ClassLoader getClassLoader() {
        // use the MapperPlatforms loader, Bukkit seemingly has issues with the threads' context class loaders (issue #30)
        return MapperPlatforms.class.getClassLoader();
    }
}
