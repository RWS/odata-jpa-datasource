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

import com.sdl.odata.api.ODataException;
import com.sdl.odata.api.edm.model.EntityDataModel;
import com.sdl.odata.api.mapper.EntityMapper;
import com.sdl.odata.api.parser.ODataUri;
import com.sdl.odata.api.processor.datasource.DataSource;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.api.processor.datasource.TransactionalDataSource;
import com.sdl.odata.api.processor.link.ODataLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import scala.Option;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;

import static com.sdl.odata.api.parser.ODataUriUtil.extractEntityWithKeys;

/**
 * The default JPA datasource, this datasource by default will create a transaction per operation.
 *
 * @author Renze de Vries
 */
@Component
@Primary
public class JPADataSource implements DataSource {
    private static final Logger LOG = LoggerFactory.getLogger(JPADataSource.class);

    @Autowired
    private EntityMapper<Object, Object> entityMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public Object create(ODataUri uri, Object entity, EntityDataModel entityDataModel) throws ODataException {
        Object jpaEntity = entityMapper.convertODataEntityToDS(entity, entityDataModel);
        EntityManager entityManager = getEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();

            LOG.info("Persisting entity: {}", jpaEntity);
            entityManager.persist(jpaEntity);

            return entityMapper.convertDSEntityToOData(jpaEntity, entity.getClass(), entityDataModel);
        } finally {
            if (transaction.isActive()) {
                transaction.commit();
            } else {
                transaction.rollback();
            }
        }
    }

    @Override
    public Object update(ODataUri uri, Object entity, EntityDataModel entityDataModel) throws ODataException {
        return create(uri, entity, entityDataModel);
    }

    @Override
    public void delete(ODataUri uri, EntityDataModel entityDataModel) throws ODataException {
        Option<Object> entity = extractEntityWithKeys(uri, entityDataModel);

        if (entity.isDefined()) {
            Object jpaEntity = entityMapper.convertODataEntityToDS(entity.get(), entityDataModel);
            if (jpaEntity != null) {
                EntityManager entityManager = getEntityManager();
                EntityTransaction transaction = entityManager.getTransaction();
                try {
                    transaction.begin();

                    Object attached = entityManager.merge(jpaEntity);
                    entityManager.remove(attached);
                } catch (PersistenceException e) {
                    LOG.error("Could not remove entity: {}", entity);
                    throw new ODataDataSourceException("Could not remove entity", e);
                } finally {
                    if (transaction.isActive()) {
                        transaction.commit();
                    } else {
                        transaction.rollback();
                    }
                }
            } else {
                throw new ODataDataSourceException("Could not remove entity, could not be loaded");
            }
        }
    }

    @Override
    public void createLink(ODataUri uri, ODataLink link, EntityDataModel entityDataModel) throws ODataException {

    }

    @Override
    public void deleteLink(ODataUri uri, ODataLink link, EntityDataModel entityDataModel) throws ODataException {

    }

    protected EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    @Override
    public TransactionalDataSource startTransaction() {
        return applicationContext.getBean(JPATransactionDataSource.class);
    }

    protected EntityManager getEntityManager() {
        return entityManagerFactory.createEntityManager();
    }
}
