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

package me.kcra.takenaka.generator.accessor.runtime;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reflection and {@link java.lang.invoke.MethodHandle} utilities.
 *
 * @author Matouš Kučera
 */
@UtilityClass
public class Reflect {
    /**
     * Tries to get a class using the class loader of this class,
     * returning null if {@link ClassNotFoundException} is thrown.
     *
     * @param clazz the class name
     * @return the class, null if not found
     */
    public @Nullable Class<?> getClassSafe(String clazz) {
        return getClassSafe(Reflect.class.getClassLoader(), clazz);
    }

    /**
     * Tries to get a class using the supplied class loader,
     * returning null if {@link ClassNotFoundException} is thrown.
     *
     * @param loader the class loader
     * @param clazz the class name
     * @return the class, null if not found
     */
    public @Nullable Class<?> getClassSafe(ClassLoader loader, String clazz) {
        try {
            return Class.forName(clazz, true, loader);
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    public @Nullable MethodHandle findVirtualSafe(String clazz, String name, Class<?> returnType, Class<?>... parameterTypes) {
        return findVirtualSafe(getClassSafe(clazz), name, MethodType.methodType(returnType, parameterTypes));
    }

    public @Nullable MethodHandle findVirtualSafe(String clazz, String name, MethodType type) {
        return findVirtualSafe(getClassSafe(clazz), name, type);
    }

    public @Nullable MethodHandle findVirtualSafe(Class<?> clazz, String name, Class<?> returnType, Class<?>... parameterTypes) {
        return findVirtualSafe(clazz, name, MethodType.methodType(returnType, parameterTypes));
    }

    public @Nullable MethodHandle findVirtualSafe(Class<?> clazz, String name, MethodType type) {
        if (clazz == null || name == null || type == null) return null;

        try {
            return MethodHandles.lookup().findVirtual(clazz, name, type);
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
        }
        return null;
    }

    public @Nullable MethodHandle findStaticSafe(String clazz, String name, Class<?> returnType, Class<?>... parameterTypes) {
        return findStaticSafe(getClassSafe(clazz), name, MethodType.methodType(returnType, parameterTypes));
    }

    public @Nullable MethodHandle findStaticSafe(String clazz, String name, MethodType type) {
        return findStaticSafe(getClassSafe(clazz), name, type);
    }

    public @Nullable MethodHandle findStaticSafe(Class<?> clazz, String name, Class<?> returnType, Class<?>... parameterTypes) {
        return findStaticSafe(clazz, name, MethodType.methodType(returnType, parameterTypes));
    }

    public @Nullable MethodHandle findStaticSafe(Class<?> clazz, String name, MethodType type) {
        if (clazz == null || name == null || type == null) return null;

        try {
            return MethodHandles.lookup().findStatic(clazz, name, type);
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
        }
        return null;
    }

    public @Nullable MethodHandle findGetterSafe(String clazz, String name, String type) {
        return findGetterSafe(getClassSafe(clazz), name, getClassSafe(type));
    }

    public @Nullable MethodHandle findGetterSafe(Class<?> clazz, String name, String type) {
        return findGetterSafe(clazz, name, getClassSafe(type));
    }

    public @Nullable MethodHandle findGetterSafe(String clazz, String name, Class<?> type) {
        return findGetterSafe(getClassSafe(clazz), name, type);
    }

    public @Nullable MethodHandle findGetterSafe(Class<?> clazz, String name, Class<?> type) {
        if (clazz == null || name == null || type == null) return null;

        try {
            return MethodHandles.lookup().findGetter(clazz, name, type);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        return null;
    }

    public @Nullable MethodHandle findStaticGetterSafe(String clazz, String name, String type) {
        return findStaticGetterSafe(getClassSafe(clazz), name, getClassSafe(type));
    }

    public @Nullable MethodHandle findStaticGetterSafe(Class<?> clazz, String name, String type) {
        return findStaticGetterSafe(clazz, name, getClassSafe(type));
    }

    public @Nullable MethodHandle findStaticGetterSafe(String clazz, String name, Class<?> type) {
        return findStaticGetterSafe(getClassSafe(clazz), name, type);
    }

    public @Nullable MethodHandle findStaticGetterSafe(Class<?> clazz, String name, Class<?> type) {
        if (clazz == null || name == null || type == null) return null;

        try {
            return MethodHandles.lookup().findStaticGetter(clazz, name, type);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        return null;
    }
}
