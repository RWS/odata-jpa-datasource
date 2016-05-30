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
package com.sdl.odata.datasource.jpa;

import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import org.hibernate.collection.spi.PersistentCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.persistence.Entity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The OData Proxy Interceptor
 *
 * The main goal of this util class is to handle entities that has hibernate proxies.
 * The interceptor scan possible proxies and transforms it into empty values according to type.
 *
 * @author Renze de Vries
 */
@Component
public class ODataProxyProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ODataProxyProcessor.class);

    /**
     * If the lazy initialization exists, we are able to receive the values with Hibernate proxies objects.
     * In order to come over, we scan all nested objects to find such element and un-proxy them.
     *
     * @param source entity provided to process and unproxy
     * @return The processed object.
     * @throws ODataDataSourceException If unable to process the object
     */
    public Object process(Object source)
            throws ODataDataSourceException {
        unproxyElements(source, new HashSet<>(), null);
        return source;
    }

    private void unproxyElements(Object entity, Set<Object> visitedEntities, List<String> expandProperties)
            throws ODataDataSourceException {
        if (visitedEntities.contains(entity)) {
            return;
        }
        //put entity to set of already visited
        visitedEntities.add(entity);
        Class reflectObj = entity.getClass();
        Field[] fields = reflectObj.getDeclaredFields();
        boolean sourceEntity = visitedEntities.size() == 1;

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object fieldType = field.get(entity);
                if (isJPAEntity(fieldType)) {
                    unproxyElements(fieldType, visitedEntities, expandProperties);
                } else if (fieldType instanceof PersistentCollection) {
                    PersistentCollection collection = (PersistentCollection) fieldType;
                    if (collection.wasInitialized()) {
                        for (Object element : (Iterable<?>) collection) {
                            unproxyElements(element, visitedEntities, expandProperties);
                        }
                    } else {
                        if (!sourceEntity && !isEntityExpanded(entity, expandProperties)) {
                            if (fieldType instanceof List) {
                                // Hibernate checks if the current field type is a hibernate proxy
                                LOG.debug("This collection is lazy initialized. Replaced by an empty list.");
                                field.set(entity, new ArrayList<>());
                            } else if (fieldType instanceof Set) {
                                LOG.debug("This set is lazy initialized. Replaced by an empty set.");
                                field.set(entity, new HashSet<>());
                            }
                        } else {
                            // These properties will be loaded during mapping
                            field.set(entity, null);
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                throw new ODataDataSourceException("Cannot un-proxy elements of: " + reflectObj.getName(), e);
            }
        }
    }

    private boolean isEntityExpanded(Object entity, List<String> expandProperties) {
        return false;
    }

    /**
     * Checks if the given entity is @Entity based object.
     *
     * @param entity fieldType
     * @return isEntityFlag
     */
    private static boolean isJPAEntity(Object entity) {
        return entity != null && entity.getClass().getAnnotation(Entity.class) != null;
    }
}
