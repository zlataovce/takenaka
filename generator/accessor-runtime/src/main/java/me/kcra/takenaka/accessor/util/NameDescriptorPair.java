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

package me.kcra.takenaka.accessor.util;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * A name and method parameters immutable data holder.
 *
 * @author Matouš Kučera
 */
public final class NameDescriptorPair {
    /**
     * The method name.
     */
    private final String name;

    /**
     * The method descriptor parameters.
     */
    private final String[] parameters;

    /**
     * Constructs a new {@link NameDescriptorPair} with the given method name and parameters.
     *
     * @param name the method name
     * @param parameters the method descriptor parameters
     */
    public NameDescriptorPair(@NotNull String name, @NotNull String[] parameters) {
        this.name = name;
        this.parameters = parameters;
    }

    /**
     * Gets the method name.
     *
     * @return the method name
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * Gets the method parameters.
     *
     * @return the method parameters
     */
    public @NotNull String[] getParameters() {
        return this.parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final NameDescriptorPair that = (NameDescriptorPair) o;
        return Objects.equals(name, that.name)
                && Arrays.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name);
        result = 31 * result + Arrays.hashCode(parameters);
        return result;
    }

    @Override
    public String toString() {
        return "NameDescriptorPair{" +
                "name='" + name + '\'' +
                ", parameters=" + Arrays.toString(parameters) +
                '}';
    }
}
