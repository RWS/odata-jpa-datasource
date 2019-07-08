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

import com.google.common.base.Strings;
import com.sdl.odata.api.ODataSystemException;
import com.sdl.odata.api.edm.model.EntityDataModel;
import com.sdl.odata.api.edm.model.EntitySet;
import com.sdl.odata.api.edm.model.EntityType;
import com.sdl.odata.api.edm.model.StructuralProperty;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.datasource.jpa.ODataJPAEntity;
import com.sdl.odata.datasource.jpa.ODataJPAProperty;

import javax.persistence.Entity;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This contains a set of utility methods related to JPA metadata annotations in the Odata metadata model.
 * @author Renze de Vries
 */
public final class JPAMetadataUtil {

    private JPAMetadataUtil() {
    }

    public static boolean isJPAEntity(Object source) {
        checkNotNull(source);
        return source.getClass().isAnnotationPresent(Entity.class);
    }

    public static Class<?> getJPAEntityClass(Class<?> odataEntityType) {
        checkNotNull(odataEntityType);
        ODataJPAEntity odataJPAEntityAnno = odataEntityType.getAnnotation(ODataJPAEntity.class);
        if (odataJPAEntityAnno == null) {
            throw new ODataSystemException("OData entity type is not mapped to a JPA entity type: " + odataEntityType);
        }

        try {
            return ReflectionUtil.newClass(odataJPAEntityAnno.value());
        } catch (ODataDataSourceException e) {
            throw new ODataSystemException("OData entity type JPA entity could not be loaded: " + odataEntityType);
        }
    }

    /**
     * Get the jpa collection name for a given odata type.
     * @param odataEntityType The odata class that should contain the needed JPA annotations
     * @return The name of the collection, or null if invalid annotations where present
     */
    public static String getJPACollectionName(Class<?> odataEntityType) {
        checkNotNull(odataEntityType);
        Class<?> jpaEntityType = getJPAEntityClass(odataEntityType);

        Entity jpaEntityAnno = jpaEntityType != null ? jpaEntityType.getAnnotation(Entity.class) : null;
        if (jpaEntityAnno == null) {
            throw new ODataSystemException("Entity does not have an @Entity annotation: " + jpaEntityType.getName());
        }

        String jpaCollectionName = jpaEntityAnno.name();
        if (Strings.isNullOrEmpty(jpaCollectionName)) {
            jpaCollectionName = jpaEntityType.getSimpleName();
        }

        return jpaCollectionName;
    }

    /**
     * Get the JPA collection name based on the entity setname and odata metadata model.
     * @param entityDataModel The entity metadata model containing all odata metadata information
     * @param entitySetName The entity set for which to request the jpa collection name
     * @return The name of the JPA collection
     */
    public static String getJPACollectionName(EntityDataModel entityDataModel, String entitySetName) {
        // Translate entity set name to JPA collection name
        EntitySet entitySet = entityDataModel.getEntityContainer().getEntitySet(entitySetName);
        Class<?> odataEntityType = entityDataModel.getType(entitySet.getTypeName()).getJavaType();

        return getJPACollectionName(odataEntityType);
    }

    /**
     * Translates the name of an OData property into the name of the corresponding JPA property.
     *
     * @param entityType The entity type which contains the property.
     * @param propertyName The name of the OData property.
     * @return The name of the corresponding JPA property.
     */
    public static String getJPAPropertyName(EntityType entityType, String propertyName) {
        StructuralProperty property = entityType.getStructuralProperty(propertyName);
        if (property == null) {
            throw new ODataSystemException("Property '" + propertyName + "' does not exist in entity type: " +
                    entityType.getFullyQualifiedName());
        }

        Field field = property.getJavaField();
        PropertyDescriptor propertyDescriptor = property.getPropertyDescriptor();
        ODataJPAProperty jpaPropertyAnno = propertyDescriptor.getReadMethod()
                .getAnnotation(ODataJPAProperty.class) != null
                ? propertyDescriptor.getReadMethod().getAnnotation(ODataJPAProperty.class)
                : field.getAnnotation(ODataJPAProperty.class);
        String jpaPropertyName = jpaPropertyAnno.value();
        if (Strings.isNullOrEmpty(jpaPropertyName)) {
            jpaPropertyName = field.getName();
        }

        return jpaPropertyName;
    }
}
