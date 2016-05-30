/**
 * Copyright (c) 2016 All Rights Reserved by the SDL Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sdl.odata.datasource.jpa.util;

import com.sdl.odata.api.processor.datasource.ODataDataSourceException;

import java.lang.reflect.Field;

/**
 * Small utility class that contains reflection shortcuts.
 * @author Renze de Vries
 */
public final class ReflectionUtil {
    private ReflectionUtil() {

    }

    /**
     * Creates a new instance of the class.
     * @param cls The class to create new instance for
     * @param <T> The type of the class
     * @return The instance of the class
     * @throws ODataDataSourceException If unable to create the new class
     */
    public static <T> T newInstance(Class<T> cls) throws ODataDataSourceException {
        try {
            return cls.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ODataDataSourceException("Cannot create new instance of: " + cls.getName(), e);
        }
    }

    public static <T> T newInstance(String className) throws ODataDataSourceException {
        return newInstance(newClass(className));
    }

    public static <T> Class<T> newClass(String className) throws ODataDataSourceException {
        try {
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new ODataDataSourceException("Cannot create class of: " + className, e);
        }
    }

    /**
     * Gets the field in a certain class for the given field name.
     * @param cls The class to get the field from
     * @param fieldName The name of the field
     * @return The Field instance
     * @throws ODataDataSourceException If unable to load the Field
     */
    public static Field getField(Class<?> cls, String fieldName) throws ODataDataSourceException {
        try {
            return cls.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new ODataDataSourceException("Field " + fieldName + " does not exist in class: " + cls.getName(), e);
        }
    }

    /**
     * Reads the field data by getting the object.
     * @param field The field to get the field value for
     * @param object The object instance that contains the field
     * @return The raw object for the field
     * @throws ODataDataSourceException If unable to read the field
     */
    public static Object readField(Field field, Object object) throws ODataDataSourceException {
        field.setAccessible(true);
        try {
            return field.get(object);
        } catch (IllegalAccessException e) {
            throw new ODataDataSourceException("Cannot read field: " + field.getName(), e);
        }
    }

    /**
     * Writes the object to the field.
     * @param field The field to write the object to
     * @param object The object instance on which the field is present
     * @param value The value to write to the field
     * @throws ODataDataSourceException If unable to write to the field
     */
    public static void writeField(Field field, Object object, Object value) throws ODataDataSourceException {
        field.setAccessible(true);
        try {
            field.set(object, value);
        } catch (IllegalAccessException e) {
            throw new ODataDataSourceException("Cannot write field: " + field.getName(), e);
        }
    }
}
