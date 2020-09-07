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
package com.sdl.odata.datasource.jpa.mapper;

import com.sdl.odata.api.edm.model.EntityDataModel;
import com.sdl.odata.api.edm.model.StructuralProperty;
import com.sdl.odata.api.edm.model.StructuredType;
import com.sdl.odata.api.mapper.EntityMapper;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.datasource.jpa.ODataJPAEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sdl.odata.datasource.jpa.util.ReflectionUtil.getField;
import static com.sdl.odata.datasource.jpa.util.ReflectionUtil.newInstance;
import static com.sdl.odata.datasource.jpa.util.ReflectionUtil.readField;
import static com.sdl.odata.datasource.jpa.util.ReflectionUtil.writeField;
import static com.sdl.odata.util.AnnotationsUtil.checkAnnotationPresent;
import static com.sdl.odata.util.AnnotationsUtil.getAnnotation;
import static com.sdl.odata.util.edm.EntityDataModelUtil.createPropertyCollection;
import static com.sdl.odata.util.edm.EntityDataModelUtil.getPropertyType;
import static com.sdl.odata.util.edm.EntityDataModelUtil.getPropertyValue;
import static com.sdl.odata.util.edm.EntityDataModelUtil.isStructuredType;
import static com.sdl.odata.util.edm.EntityDataModelUtil.setPropertyValue;
import static com.sdl.odata.util.edm.EntityDataModelUtil.visitProperties;

/**
 * Implementation of {@link EntityMapper} that converts between OData entities
 * and entities having the JPA annotations.
 *
 * @author Renze de Vries
 */
@Component
@Qualifier("JPA")
public class AnnotationJPAEntityMapper implements EntityMapper<Object, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(AnnotationJPAEntityMapper.class);

    @Override
    public Object convertODataEntityToDS(Object odataEntity, EntityDataModel entityDataModel)
            throws ODataDataSourceException {
        return odataEntityToJPA(odataEntity, entityDataModel, new HashMap<>());
    }

    @Override
    public List<Object> convertODataEntitiesListToDS(java.util.List<Object> list, EntityDataModel entityDataModel)
            throws ODataDataSourceException {
        List<Object> objects = new java.util.ArrayList<>();
        for (Object item : list) {
            objects.add(odataEntityToJPA(item, entityDataModel, new HashMap<>()));
        }
        return objects;
    }

    @Override
    public <T> T convertDSEntityToOData(Object jpaEntity, Class<T> odataEntityClass, EntityDataModel entityDataModel)
            throws ODataDataSourceException {
        return jpaEntityToOData(jpaEntity, odataEntityClass, entityDataModel, new HashMap<>());
    }

    @Override
    public <R> List<R> convertDSEntitiesListToOData(java.util.List<Object> list, Class<R> aClass,
                                                    EntityDataModel entityDataModel)
            throws ODataDataSourceException {
        List<R> objects = new java.util.ArrayList<>();
        for (Object item : list) {
            objects.add(jpaEntityToOData(item, aClass, entityDataModel, new HashMap<>()));
        }
        return objects;
    }

    private Object odataEntityToJPA(final Object odataEntity, final EntityDataModel entityDataModel,
                                    final Map<Object, Object> visitedEntities)
            throws ODataDataSourceException {
        // If we already have entity in map, then it is a cyclic link, just return stored entity
        if (visitedEntities.containsKey(odataEntity)) {
            return visitedEntities.get(odataEntity);
        }


        Class<?> sourceClass = odataEntity.getClass();
        ODataJPAEntity jpaEntityAnno = getAnnotation(sourceClass, ODataJPAEntity.class);

        final String targetClass = jpaEntityAnno.value();
        LOG.debug("Mapping OData entity to JPA: {} => {}", sourceClass.getName(), targetClass);

        // Create new instance of JPA entity
        final Object jpaEntity = newInstance(targetClass);

        // Put entity to map of already visited
        visitedEntities.put(odataEntity, jpaEntity);

        StructuredType structType = (StructuredType) entityDataModel.getType(sourceClass);

        // Copy field values from OData entity to JPA entity
        visitProperties(entityDataModel, structType, new JPAPropertyVisitor() {
            @Override
            public void visit(StructuralProperty property, String jpaFieldName) throws ODataDataSourceException {
                Object odataValue = getPropertyValue(property, odataEntity);
                Object jpaValue = odataValue;

                // If the value is not null and the property is of a structured type, then map value(s) recursively
                if (odataValue != null && isStructuredType(getPropertyType(entityDataModel, property))) {
                    if (property.isCollection()) {
                        java.util.Collection<Object> result = createPropertyCollection(property);
                        for (Object element : (Iterable<?>) odataValue) {
                            result.add(odataEntityToJPA(element, entityDataModel, visitedEntities));
                        }
                        jpaValue = result;
                    } else {
                        jpaValue = odataEntityToJPA(odataValue, entityDataModel, visitedEntities);
                    }
                }

                writeField(getField(jpaEntity.getClass(), jpaFieldName), jpaEntity, jpaValue);
            }
        });

        return jpaEntity;
    }

    private <T> T jpaEntityToOData(final Object jpaEntity, Class<T> odataEntityClass,
                                   final EntityDataModel entityDataModel, final Map<Object, Object> visitedEntities)
            throws ODataDataSourceException {
        // If we already have entity in map, then it is a cyclic link, just return stored entity
        if (visitedEntities.containsKey(jpaEntity)) {
            return odataEntityClass.cast(visitedEntities.get(jpaEntity));
        }

        final Class<?> sourceClass = jpaEntity.getClass();
        checkAnnotationPresent(odataEntityClass, ODataJPAEntity.class);

        final StructuredType structType = (StructuredType) entityDataModel.getType(odataEntityClass);

        LOG.debug("Mapping JPA entity to OData: {} => {}", sourceClass.getName(), odataEntityClass.getName());

        // Create new instance of OData entity
        final T odataEntity = newInstance(odataEntityClass);

        //add visited entity to map
        visitedEntities.put(jpaEntity, odataEntity);

        // Copy field values from JPA entity to OData entity
        visitProperties(entityDataModel, structType, new JPAPropertyVisitor() {
            @Override
            public void visit(StructuralProperty property, String jpaFieldName) throws ODataDataSourceException {
                Object jpaValue = readField(getField(sourceClass, jpaFieldName), jpaEntity);
                Object odataValue = jpaValue;

                // If the value is not null and the property is of a structured type, then map value(s) recursively
                if (jpaValue != null && isStructuredType(getPropertyType(entityDataModel, property))) {
                    Class<?> targetType = getPropertyType(entityDataModel, property).getJavaType();
                    if (property.isCollection()) {
                        java.util.Collection<Object> result = createPropertyCollection(property);
                        for (Object element : (Iterable<?>) jpaValue) {
                            result.add(jpaEntityToOData(element, targetType, entityDataModel, visitedEntities));
                        }
                        odataValue = result;
                    } else {
                        odataValue = jpaEntityToOData(jpaValue, targetType, entityDataModel, visitedEntities);
                    }
                }

                setPropertyValue(property, odataEntity, odataValue);
            }
        });

        return odataEntity;
    }
}
