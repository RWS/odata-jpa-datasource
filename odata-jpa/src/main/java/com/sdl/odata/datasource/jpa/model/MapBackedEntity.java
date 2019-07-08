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
package com.sdl.odata.datasource.jpa.model;

import java.time.temporal.Temporal;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.sdl.odata.model.ReferencableEntity;
import org.springframework.data.util.CastUtils;

/**
 * All properties of this OData entity are present in the {@link #_backingMap()}.
 */
public abstract class MapBackedEntity extends ReferencableEntity {

    private Map<String, Object> backingMap = new LinkedHashMap<>();


    @SuppressWarnings("checkstyle:methodname")
    public <T> T _getProperty(String propName, Class<T> type) {
        Object value = backingMap.get(propName);
        if (type.isPrimitive() && value == null) {
            return null;
        }
        return CastUtils.cast(value);
    }

    @SuppressWarnings("checkstyle:methodname")
    public <T> T _setProperty(String propName, T value) {
        Object old = backingMap.get(propName);
        backingMap.put(propName, value);
        return CastUtils.cast(old);
    }

    @SuppressWarnings("checkstyle:methodname")
    public Map<String, Object> _backingMap() {
        return Collections.unmodifiableMap(backingMap);
    }

    @Override
    public String toString() {
        return String.format("%s[%s]@%s", this.getClass().getName(),
                             simplePropertiesOnly(), System.identityHashCode(this));
    }

    public Map<String, Object> simplePropertiesOnly() {
        Map<String, Object> result = new HashMap<>();
        for (final Map.Entry<String, Object> entry : backingMap.entrySet()) {
            if (entry.getValue() == null) {
                result.put(entry.getKey(), entry.getValue());
                continue;
            }

            if (entry.getValue() instanceof CharSequence
                || entry.getValue() instanceof Number
                || entry.getValue() instanceof Date
                || entry.getValue() instanceof Temporal
                || entry.getValue() instanceof UUID) {
                result.put(entry.getKey(), entry.getValue());
            }

        }
        return result;
    }
}
