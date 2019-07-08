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
import com.sdl.odata.api.parser.ODataUri;
import com.sdl.odata.api.processor.datasource.DataSource;
import com.sdl.odata.api.processor.datasource.TransactionalDataSource;
import com.sdl.odata.api.processor.link.ODataLink;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * OData DataSource marker interface that makes sure that every method is transactional.
 * This avoids manual transaction control within the methods from original implementation.
 */
public interface SpringTransactionalDatasource extends DataSource, TransactionalDataSource {

    @Transactional
    Object create(ODataUri var1, Object var2, EntityDataModel var3) throws ODataException;

    @Transactional
    Object update(ODataUri var1, Object var2, EntityDataModel var3, boolean isPartial) throws ODataException;

    @Transactional
    void delete(ODataUri var1, EntityDataModel var2) throws ODataException;

    @Transactional
    void createLink(ODataUri var1, ODataLink var2, EntityDataModel var3) throws ODataException;

    @Transactional
    void deleteLink(ODataUri var1, ODataLink var2, EntityDataModel var3) throws ODataException;

    TransactionStatus getTransactionStatus();

}
