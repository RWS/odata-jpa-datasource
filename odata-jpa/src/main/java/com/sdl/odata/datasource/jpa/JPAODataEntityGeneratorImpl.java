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
import com.sdl.odata.datasource.jpa.builders.EntityBuilder;
import com.sdl.odata.datasource.jpa.builders.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Renze de Vries
 */
@Component
public class JPAODataEntityGeneratorImpl implements JPAODataEntityGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(JPAODataEntityGeneratorImpl.class);

    @Value("${datasource.odatanamespace}")
    private String odataNamespace;

    @Override
    public List<Class<?>> generateODataEntityClasses(List<Class<?>> jpaEntities) throws ODataDataSourceException {
        LOG.info("Generating entities for {} JPA entities", jpaEntities.size());
        List<Class<?>> odataEntities = new ArrayList<>();
        TransformContext context = new TransformContext(jpaEntities, odataNamespace);
        for (Class<?> jpaEntity : jpaEntities) {
            LOG.info("Generating OData entity for JPA Entity: {}", jpaEntity.getName());
            Class<?> odataEntity = new EntityBuilder(jpaEntity, context).build();

            LOG.info("Generated an odata entity: {}", odataEntity.getName());
            odataEntities.add(odataEntity);
        }

        return odataEntities;
    }

    public void setOdataNamespace(String odataNamespace) {
        this.odataNamespace = odataNamespace;
    }
}
