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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.Type;

import com.sdl.odata.api.ODataException;
import com.sdl.odata.api.edm.model.EntityDataModel;
import com.sdl.odata.api.mapper.EntityMapper;
import com.sdl.odata.api.parser.ODataUri;
import com.sdl.odata.api.processor.datasource.DataSource;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.api.processor.datasource.TransactionalDataSource;
import com.sdl.odata.api.processor.link.ODataLink;
import com.sdl.odata.datasource.jpa.mapper.AnnotationJPAEntityMapper;
import com.sdl.odata.datasource.jpa.model.MapBackedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import scala.Option;

import static com.sdl.odata.api.parser.ODataUriUtil.extractEntityWithKeys;

/**
 * The default JPA datasource, this datasource by default will create a transaction per operation.
 *
 * @author Renze de Vries
 */
@Component
@Primary
public class JPADataSource implements DataSource, SpringTransactionalDatasource {
    private static final Logger LOG = LoggerFactory.getLogger(JPADataSource.class);

    @Autowired
    private EntityMapper<Object, Object> entityMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;


    @Override
    public Object create(ODataUri uri, Object odataEntity, EntityDataModel entityDataModel) throws ODataException {
        Object jpaEntity = entityMapper.convertODataEntityToDS(odataEntity, entityDataModel);
        EntityType entityType = entityManager.getMetamodel().entity(jpaEntity.getClass());
        try {
            LOG.info("Persisting entity: {}", jpaEntity);
            AnnotationJPAEntityMapper.updateBidirectionalProperties(entityType, jpaEntity);
            entityManager.persist(jpaEntity);

            return entityMapper.convertDSEntityToOData(jpaEntity, odataEntity.getClass(), entityDataModel);
        } finally {
            LOG.info("Finished persist: {}", jpaEntity);
        }
    }

    @Override
    public Object update(ODataUri uri, Object odataEntity, EntityDataModel entityDataModel,
                         boolean isPartial) throws ODataException {
        Object jpaEntity = entityMapper.convertODataEntityToDS(odataEntity, entityDataModel);
        EntityType entityType = entityManager.getMetamodel().entity(jpaEntity.getClass());
        Object idValue = AnnotationJPAEntityMapper.getId(entityType, jpaEntity);
        try {

            Object existingJpaEntity = entityManager.find(jpaEntity.getClass(), idValue);
            if (existingJpaEntity != null) {
                if (!isPartial) {
                    for (Attribute attribute : (Set<Attribute>) entityType.getAttributes()) {
                        if (attribute instanceof PluralAttribute) {
                            switch (((PluralAttribute) attribute).getCollectionType()) {
                                case COLLECTION:
                                case LIST: {
                                    AnnotationJPAEntityMapper.setPropertyValue(attribute, existingJpaEntity,
                                                                               new ArrayList<>());
                                    break;
                                }
                                case SET: {
                                    AnnotationJPAEntityMapper.setPropertyValue(attribute, existingJpaEntity,
                                                                               new HashSet<>());
                                    break;
                                }
                                case MAP: {
                                    AnnotationJPAEntityMapper.setPropertyValue(attribute, existingJpaEntity,
                                                                               new HashMap<>());
                                    break;
                                }
                                default: {
                                    throw new IllegalArgumentException("Unknown type of collection " +
                                             ((PluralAttribute) attribute).getCollectionType());
                                }

                            }
                        } else {
                            AnnotationJPAEntityMapper.setPropertyValue(attribute, existingJpaEntity, null);
                        }
                    }
                }
                LOG.info("Upserting existing entity: {}", jpaEntity);
                if (odataEntity instanceof MapBackedEntity) {
                    for (Map.Entry<String, Object> entry : ((MapBackedEntity) odataEntity)._backingMap().entrySet()) {
                        String propertyName = entry.getKey();
                        Attribute attribute = entityType.getAttribute(propertyName);

                        if (attribute == null) {
                            throw new ODataDataSourceException("Unable to properly map odata to jpa " +
                                                               odataEntity.getClass() + " key " + propertyName);
                        }

                        Object newVal = AnnotationJPAEntityMapper.getPropertyValue(attribute, jpaEntity);
                        if (attribute instanceof PluralAttribute) {
                            Collection collection = (Collection) AnnotationJPAEntityMapper.getPropertyValue(
                                    attribute, existingJpaEntity);
                            if (newVal != null && isPartial) { //This kind of merge is only used for partial updates
                                List mergeList = new ArrayList(collection);
                                mergeList.addAll((Collection) newVal);
                                newVal = mergeList;
                            } else {
                                newVal = collection;
                            }
                        }
                        AnnotationJPAEntityMapper.setPropertyValue(attribute, existingJpaEntity, newVal);

                    }
                } else {
                    throw new IllegalArgumentException(
                            "OData entity doesn't implement " + MapBackedEntity.class +
                            " and cannot be safely upserted");
                }
                AnnotationJPAEntityMapper.updateBidirectionalProperties(entityType, existingJpaEntity);
                jpaEntity = entityManager.merge(existingJpaEntity);
            } else {
                LOG.info("Upserting new entity: {}", jpaEntity);
                AnnotationJPAEntityMapper.updateBidirectionalProperties(entityType, jpaEntity);
                entityManager.persist(jpaEntity);
            }

            return entityMapper.convertDSEntityToOData(jpaEntity, odataEntity.getClass(), entityDataModel);
        } finally {
            LOG.info("Finished update: {}", jpaEntity);
        }
    }

    @Override
    public void delete(ODataUri uri, EntityDataModel entityDataModel) throws ODataException {
        Option<Object> entity = extractEntityWithKeys(uri, entityDataModel);

        if (entity.isDefined()) {
            Object jpaEntity = entityMapper.convertODataEntityToDS(entity.get(), entityDataModel);
            if (jpaEntity != null) {
                try {
                    EntityType entityType = entityManager.getMetamodel().entity(jpaEntity.getClass());
                    Object idValue = AnnotationJPAEntityMapper.getId(entityType, jpaEntity);
                    Object existingJpaEntity = entityManager.find(jpaEntity.getClass(), idValue);

                    entityManager.remove(existingJpaEntity);
                } catch (PersistenceException e) {
                    LOG.error("Could not remove entity: {}", entity);
                    throw new ODataDataSourceException("Could not remove entity", e);
                } finally {
                    LOG.info("Finished delete: {}", jpaEntity);
                }
            } else {
                throw new ODataDataSourceException("Could not remove entity, could not be loaded");
            }
        }
    }

    @Override
    public void createLink(ODataUri uri, ODataLink link, EntityDataModel entityDataModel) throws ODataException {
        saveLink(uri, link, entityDataModel, false);
    }

    @Override
    public void deleteLink(ODataUri uri, ODataLink link, EntityDataModel entityDataModel) throws ODataException {
        saveLink(uri, link, entityDataModel, true);
    }

    public void saveLink(ODataUri uri, ODataLink link, EntityDataModel entityDataModel,
                         boolean isDelete) throws ODataException {
        EntityType jpaEntityType = ((AnnotationJPAEntityMapper) entityMapper).getJPAEntityTypeFor(
                link.fromEntityType(), entityDataModel);
        Attribute navigationPropertyAttr = jpaEntityType.getAttribute(link.getFromNavigationProperty().getName());
        Type navigationPropertyJpaEntityType = navigationPropertyAttr instanceof PluralAttribute ?
                ((PluralAttribute) navigationPropertyAttr).getElementType()
                : ((SingularAttribute) navigationPropertyAttr).getType();
        if (!(navigationPropertyJpaEntityType instanceof EntityType)) {
            throw new IllegalArgumentException(
                    String.format("Link invalid, target entity (%s) is not a navigation property",
                                  link.getFromNavigationProperty().getName()));
        }
        Object idValue = link.getFromEntityKey().values().head();
        Object targetIdValue;
        if (isDelete) {
            targetIdValue = null;
        } else {
            targetIdValue = link.getToEntityKey().values().head();
        }
        try {
            Object jpaEntity = entityManager.find(jpaEntityType.getJavaType(), idValue);
            Object targetJpaEntityRef;
            if (isDelete) {
                targetJpaEntityRef = null;
            } else {
                targetJpaEntityRef = entityManager.getReference(navigationPropertyJpaEntityType.getJavaType(),
                                                                targetIdValue);
            }

            AnnotationJPAEntityMapper.setPropertyValue(navigationPropertyAttr, jpaEntity, targetJpaEntityRef);

            if (isDelete) {
                LOG.info("Unlinking {} from entity : {}", navigationPropertyAttr.getName(), jpaEntity);
            } else {
                LOG.info("Linking {}({}) to entity : {}", navigationPropertyAttr.getName(), targetIdValue, jpaEntity);
            }
            AnnotationJPAEntityMapper.updateBidirectionalProperties(jpaEntityType, jpaEntity);
            entityManager.persist(jpaEntity);

            return;
        } finally {
            LOG.info("Finished linkSave: {}", uri);
        }
    }


    @Override
    public TransactionalDataSource startTransaction() {
        return this;
    }

    @Override
    public boolean commit() {
        transactionManager.commit(getTransactionStatus());
        return true;
    }

    @Override
    public void rollback() {
        transactionManager.rollback(getTransactionStatus());
    }

    @Override public boolean isActive() {
        TransactionStatus status = getTransactionStatus();
        return status != null && !status.isCompleted();
    }

    public TransactionStatus getTransactionStatus() {
        return transactionManager.getTransaction(null);
    }


}
