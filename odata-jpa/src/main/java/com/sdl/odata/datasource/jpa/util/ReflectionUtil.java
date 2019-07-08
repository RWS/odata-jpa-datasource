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

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import org.springframework.util.ReflectionUtils;

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
            Field field = ReflectionUtils.findField(cls, fieldName);
            if (field == null) {
                throw new ODataDataSourceException("Field " + fieldName + " does not exist in class: " + cls.getName());
            }
            return field;
    }

    public static <T> T readMember(Member member, Object object) throws ODataDataSourceException {
        try {
            if (member instanceof Field) {
                return (T) ReflectionUtils.getField((Field) member, object);
            } else if (member instanceof Method) {
                return (T) ReflectionUtils.invokeMethod((Method) member, object);
            }
        } catch (Exception e) {
            throw new ODataDataSourceException(
                    "Unable to get " + member + " from entity " +
                    (object == null ? null : object.getClass()), e);
        }
        throw new ODataDataSourceException(
                "Unable to get " + member + " from entity " +
                (object == null ? null : object.getClass()));
    }



    public static <T> void writeMember(Member member, Object object, T value) throws ODataDataSourceException {
        try {
            if (member instanceof Field) {
                ReflectionUtils.setField((Field) member, object, value);
                return;

            } else if (member instanceof Method) {
                ReflectionUtils.invokeMethod((Method) member, object, new Object[]{value});
                return;
            }
        } catch (Exception e) {
            throw new ODataDataSourceException("Unable to set " + member + " from entity " +
                                               (object == null ? null : object.getClass()), e);
        }
        throw new ODataDataSourceException("Unable to set " + member + " from entity " +
                                           (object == null ? null : object.getClass()));
    }

}
