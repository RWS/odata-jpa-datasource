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

import com.sdl.odata.api.processor.datasource.TransactionalDataSource;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

/**
 * This is the transactionable datasource that allows running a longer transaction across operations.
 * @author Renze de Vries
 */
@Component
@Scope("prototype")
public class JPATransactionDataSource extends JPADataSource implements TransactionalDataSource {

    private EntityManager entityManager;

    private EntityTransaction entityTransaction;

    @PostConstruct
    public void initializeTransaction() {
        entityManager = getEntityManagerFactory().createEntityManager();
        entityTransaction = entityManager.getTransaction();
        entityTransaction.begin();
    }

    @Override
    public boolean isActive() {
        return entityManager.isOpen();
    }

    @Override
    public boolean commit() {
        entityTransaction.commit();
        return true;
    }

    @Override
    public void rollback() {
        entityTransaction.rollback();
    }

    @Override
    protected EntityManager getEntityManager() {
        return entityManager;
    }
}
