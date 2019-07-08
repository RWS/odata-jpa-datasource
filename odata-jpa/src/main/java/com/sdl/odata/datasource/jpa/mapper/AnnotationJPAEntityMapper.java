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

import java.beans.PropertyDescriptor;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.persistence.EntityManager;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Query;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import com.sdl.odata.api.edm.model.EntityDataModel;
import com.sdl.odata.api.edm.model.EntityType;
import com.sdl.odata.api.edm.model.StructuralProperty;
import com.sdl.odata.api.edm.model.StructuredType;
import com.sdl.odata.api.mapper.EntityMapper;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.datasource.jpa.ODataJPAEntity;
import com.sdl.odata.datasource.jpa.model.MapBackedEntity;
import com.sdl.odata.datasource.jpa.util.ReflectionUtil;
import com.sdl.odata.util.edm.EntityDataModelUtil;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static com.sdl.odata.datasource.jpa.util.ReflectionUtil.newClass;
import static com.sdl.odata.datasource.jpa.util.ReflectionUtil.newInstance;
import static com.sdl.odata.util.AnnotationsUtil.checkAnnotationPresent;
import static com.sdl.odata.util.AnnotationsUtil.getAnnotation;
import static com.sdl.odata.util.edm.EntityDataModelUtil.createPropertyCollection;
import static com.sdl.odata.util.edm.EntityDataModelUtil.getPropertyType;
import static com.sdl.odata.util.edm.EntityDataModelUtil.isStructuredType;
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

    @Autowired
    private EntityManager entityManager;

    public javax.persistence.metamodel.EntityType getJPAEntityTypeFor(EntityType entityType,
           final EntityDataModel entityDataModel) throws ODataDataSourceException {
        Class<?> sourceClass = entityType.getJavaType();
        ODataJPAEntity jpaEntityAnno = getAnnotation(sourceClass, ODataJPAEntity.class);
        StructuredType structType = (StructuredType) entityDataModel.getType(sourceClass);

        return entityManager.getMetamodel().entity(newClass(jpaEntityAnno.value()));
    }

    public EntityType getODataEntityTypeFor(javax.persistence.metamodel.EntityType entityType,
                                            final EntityDataModel entityDataModel) throws ODataDataSourceException {
        return (EntityType) entityDataModel.getType(entityType.getName());
    }

    @Override
    public Object convertODataEntityToDS(Object odataEntity, EntityDataModel entityDataModel)
            throws ODataDataSourceException {
        return odataEntityToJPA(odataEntity, entityDataModel, new IdentityHashMap<>());
    }

    @Override
    public List<Object> convertODataEntitiesListToDS(List<Object> list, EntityDataModel entityDataModel)
            throws ODataDataSourceException {
        List<Object> result = new ArrayList<>();
        Map<Object, Object> visitedEntities = new IdentityHashMap();
        for (Object odataEntity : list) {
            result.add(odataEntityToJPA(odataEntity, entityDataModel, visitedEntities));
        }
        return result;
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
        StructuredType structType = (StructuredType) entityDataModel.getType(sourceClass);

        final String targetClass = jpaEntityAnno.value();
        LOG.debug("Mapping OData entity to JPA: {} => {}", sourceClass.getName(), targetClass);

        Object jpaEntity;
        EntityType entityType = null;
        if (structType instanceof EntityType) {
            entityType = (EntityType) structType;
            MapBackedEntity mapBackedEntity = ((MapBackedEntity) odataEntity);
            Object id = mapBackedEntity._getProperty(entityType.getKey().getPropertyRefs().get(0).getPath(),
                                                     Object.class);
            if (mapBackedEntity.isReference()) {
                LOG.debug("Replacing @id reference with proxy reference {} {}", targetClass,
                          mapBackedEntity.getReferenceString());
                if (id != null) {
                    jpaEntity = entityManager.getReference(newClass(targetClass), id);
                } else {
                    String queryStr = String.format("SELECT %s FROM %s ",
                                                    entityType.getKey().getPropertyRefs().get(0).getPath(),
                                                    entityType.getName());
                    String predicateString = "";
                    for (String key: mapBackedEntity._backingMap().keySet()) {
                        if (predicateString.isEmpty()) {
                            predicateString += " WHERE ";
                        }
                        predicateString += " " + key + " = :" + key + " AND ";
                    }
                    if (predicateString.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Reference string does not contain any keys: " + mapBackedEntity.getReferenceString());
                    }
                    predicateString = StringUtils.removeEnd(predicateString, " AND ");
                    queryStr += predicateString;
                    Query query = entityManager.createQuery(queryStr);
                    query.setMaxResults(2);
                    for (String key: mapBackedEntity._backingMap().keySet()) {
                        query.setParameter(key, mapBackedEntity._getProperty(key, Object.class));
                    }
                    List results = query.getResultList();
                    if (results.size() == 0) {
                        throw new IllegalArgumentException("Reference cannot be found: " +
                                                           mapBackedEntity.getReferenceString());
                    } else if (results.size() > 1) {
                        throw new IllegalArgumentException("Reference resolves to more than one result: " +
                                                           mapBackedEntity.getReferenceString());
                    }
                    id = results.get(0);
                    jpaEntity = entityManager.getReference(newClass(targetClass), id);
                }
                visitedEntities.put(odataEntity, jpaEntity);
                return jpaEntity;
            }
        }

        // Create new instance of JPA entity
        jpaEntity = newInstance(targetClass);
        javax.persistence.metamodel.EntityType jpaEntityType =
                entityManager.getMetamodel().entity(jpaEntity.getClass());

        // Put entity to map of already visited
        visitedEntities.put(odataEntity, jpaEntity);

        // Copy field values from OData entity to JPA entity
        visitProperties(entityDataModel, structType, new JPAPropertyVisitor() {
            @Override
            public void visit(StructuralProperty property, String jpaFieldName) throws ODataDataSourceException {
                Object odataValue = EntityDataModelUtil.getPropertyValue(property, odataEntity);
                Object jpaValue = odataValue;

                // If the value is not null and the property is of a structured type, then map value(s) recursively
                if (odataValue != null && isStructuredType(getPropertyType(entityDataModel, property))) {
                    if (property.isCollection()) {
                        Collection<Object> result = createPropertyCollection(property);
                        for (Object element : (Iterable<?>) odataValue) {
                            result.add(odataEntityToJPA(element, entityDataModel, visitedEntities));
                        }
                        jpaValue = result;
                    } else {
                        jpaValue = odataEntityToJPA(odataValue, entityDataModel, visitedEntities);
                    }
                }

                setPropertyValue(jpaEntityType.getAttribute(property.getName()), jpaEntity, jpaValue);

            }
        });

        return jpaEntity;
    }

    @Override
    public <T> T convertDSEntityToOData(Object jpaEntity, Class<T> odataEntityClass, EntityDataModel entityDataModel)
            throws ODataDataSourceException {
        return jpaEntityToOData(jpaEntity, odataEntityClass, entityDataModel, new IdentityHashMap<>());
    }

    @Override
    public <T> List<T> convertDSEntitiesListToOData(List<Object> list, Class<T> odataEntityClass,
                                                    EntityDataModel entityDataModel) throws ODataDataSourceException {
        List<T> result = new ArrayList<>();
        Map<Object, Object> visitedEntities = new IdentityHashMap();
        for (Object jpaEntity : list) {
            result.add(jpaEntityToOData(jpaEntity, odataEntityClass, entityDataModel, visitedEntities));
        }
        return result;
    }

    private <T> T jpaEntityToOData(final Object jpaEntity, Class<T> odataEntityClass,
                                   final EntityDataModel entityDataModel, final Map<Object, Object> visitedEntities)
            throws ODataDataSourceException {
        // If we already have entity in map, then it is a cyclic link, just return stored entity
        if (visitedEntities.containsKey(jpaEntity)) {
            return odataEntityClass.cast(visitedEntities.get(jpaEntity));
        }

        Class<?> sourceClass = Hibernate.getClass(jpaEntity);
        checkAnnotationPresent(odataEntityClass, ODataJPAEntity.class);
        javax.persistence.metamodel.EntityType jpaEntityType = sourceClass == null
                ? null : entityManager.getMetamodel().entity(sourceClass);

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
                Object jpaValue = getPropertyValue(jpaEntityType.getAttribute(jpaFieldName), jpaEntity);
                Object odataValue = jpaValue;

                // If the value is not null and the property is of a structured type, then map value(s) recursively
                if (jpaValue != null && isStructuredType(getPropertyType(entityDataModel, property))) {
                    Class<?> targetType = getPropertyType(entityDataModel, property).getJavaType();
                    if (property.isCollection()) {
                        Collection<Object> result = createPropertyCollection(property);
                        for (Object element : (Iterable<?>) jpaValue) {
                            result.add(jpaEntityToOData(element, targetType, entityDataModel, visitedEntities));
                        }
                        odataValue = result;
                    } else {
                        odataValue = jpaEntityToOData(jpaValue, targetType, entityDataModel, visitedEntities);
                    }
                }

                EntityDataModelUtil.setPropertyValue(property, odataEntity, odataValue);
            }
        });

        return odataEntity;
    }

    public static Object getId(javax.persistence.metamodel.EntityType entityType, Object jpaEntity)
            throws ODataDataSourceException {
        return getPropertyValue(entityType.getId(Object.class), jpaEntity);
    }

    public static Object setId(javax.persistence.metamodel.EntityType entityType, Object jpaEntity, Object id)
            throws ODataDataSourceException {
        return setPropertyValue(entityType.getId(Object.class), jpaEntity, id);
    }

    public static Object getPropertyValue(Attribute attribute, Object jpaEntity) throws ODataDataSourceException {
        PropertyDescriptor propertyDescriptor =
                BeanUtils.getPropertyDescriptor(jpaEntity.getClass(), attribute.getName());
        Member readMember = propertyDescriptor.getReadMethod() != null
                ? propertyDescriptor.getReadMethod()
                : attribute.getJavaMember();
        return ReflectionUtil.readMember(readMember, jpaEntity);
    }

    public static Object setPropertyValue(Attribute attribute, Object jpaEntity, Object value)
            throws ODataDataSourceException {
        Object existing = getPropertyValue(attribute, jpaEntity);
        PropertyDescriptor propertyDescriptor =
                BeanUtils.getPropertyDescriptor(jpaEntity.getClass(), attribute.getName());
        Member writeMember = propertyDescriptor.getWriteMethod() != null
                ? propertyDescriptor.getWriteMethod()
                : attribute.getJavaMember();
        ReflectionUtil.writeMember(writeMember, jpaEntity, value);
        return existing;
    }

    public static void updateBidirectionalProperties(javax.persistence.metamodel.EntityType entityType,
                                                     Object jpaEntity)
            throws ODataDataSourceException {
        updateBidirectionalProperties(entityType, jpaEntity, new ArrayList<>());
    }

    public static void updateBidirectionalProperties(javax.persistence.metamodel.EntityType entityType,
                                                     Object jpaEntity, List<Object> visitedEntities)
            throws ODataDataSourceException {
        if (isNullOrUnitialisedProxy(jpaEntity)) {
            return;
        }
        if (visitedEntities.contains(jpaEntity)) { //infinite recursion fix
            return;
        }
        visitedEntities.add(jpaEntity);
        for (Attribute attribute : (Set<Attribute>) entityType.getAttributes()) {
            if (attribute.isAssociation()) {
                Object sourceValue = getPropertyValue(attribute, jpaEntity);
                boolean isCollection = attribute.isCollection();
                String mappedBy = getMappedBy(attribute);
                boolean isRelationshipOwner = StringUtils.isBlank(mappedBy);
                javax.persistence.metamodel.Type itemType = getAttributeItemType(attribute);

                if (!isNullOrUnitialisedProxy(sourceValue)) {
                    javax.persistence.metamodel.EntityType itemEntityType =
                            (javax.persistence.metamodel.EntityType) itemType;
                    Attribute targetAttribute = null;
                    if (!isRelationshipOwner) {
                        targetAttribute = itemEntityType.getAttribute(mappedBy);
                    } else {
                        for (Attribute targetAttribute1 : (Set<Attribute>) itemEntityType.getAttributes()) {
                            if (getAttributeItemType(targetAttribute1).getJavaType().isAssignableFrom(
                                    attribute.getJavaType())) {
                                if (getMappedBy(targetAttribute1).equals(attribute.getName())) {
                                    targetAttribute = targetAttribute1;
                                    break;
                                }
                            }
                        }
                    }

                    if (targetAttribute == null) {
                        continue; //This is not a bidirectional mapping
                    }

                    if (isCollection) {
                        Collection sourceValueColl = (Collection) sourceValue;
                        for (final Object sourceValueItem : sourceValueColl) {
                            if (!isNullOrUnitialisedProxy(sourceValueItem)) {
                                if (targetAttribute.isCollection()) {
                                    Collection targetValueColl = (Collection) getPropertyValue(
                                            targetAttribute, sourceValueItem);
                                    if (!isNullOrUnitialisedProxy(targetValueColl) && !targetValueColl.contains(
                                            jpaEntity)) {
                                        targetValueColl.add(jpaEntity);
                                    }
                                } else {
                                    setPropertyValue(targetAttribute, sourceValueItem, jpaEntity);
                                }

                                updateBidirectionalProperties(itemEntityType, sourceValueItem, visitedEntities);
                            }
                        }
                    } else {
                        setPropertyValue(targetAttribute, sourceValue, jpaEntity);
                        updateBidirectionalProperties(itemEntityType, sourceValue, visitedEntities);
                    }

                }

            }
        }

    }

    public static javax.persistence.metamodel.Type getAttributeItemType(Attribute attribute) {
        if (attribute instanceof PluralAttribute) {
            return ((PluralAttribute) attribute).getElementType();
        } else {
            return ((SingularAttribute) attribute).getType();
        }
    }

    public static String getMappedBy(Attribute attribute) {
        AnnotatedElement annotatedElement = (AnnotatedElement) attribute.getJavaMember();
        if (annotatedElement.isAnnotationPresent(OneToMany.class)) {
            return annotatedElement.getAnnotation(OneToMany.class).mappedBy();
        } else if (annotatedElement.isAnnotationPresent(OneToOne.class)) {
            return annotatedElement.getAnnotation(OneToOne.class).mappedBy();
        } else if (annotatedElement.isAnnotationPresent(ManyToMany.class)) {
            return annotatedElement.getAnnotation(ManyToMany.class).mappedBy();
        }
        return null;
    }

    public static boolean isNullOrUnitialisedProxy(Collection collection) {
        return collection == null || !Hibernate.isInitialized(collection);
    }

    public static boolean isNullOrUnitialisedProxy(Object entity) {
        return entity == null || !Hibernate.isInitialized(entity);
    }
}
