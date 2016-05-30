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

import com.sdl.odata.api.edm.registry.ODataEdmRegistry;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Renze de Vries
 */
@Component
public class JPAEdmModelLoader {
    private static final Logger LOG = LoggerFactory.getLogger(JPAEdmModelLoader.class);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JPAODataEntityGenerator entityGenerator;

    @Autowired
    private ODataEdmRegistry edmRegistry;

    @PostConstruct
    public void init() throws ODataDataSourceException {
        LOG.info("Initiating JPA entity loader");

        List<Class<?>> jpaEntities = discoverEntities();

        List<Class<?>> entityClasses = entityGenerator.generateODataEntityClasses(jpaEntities);
        edmRegistry.registerClasses(entityClasses);

        LOG.info("Finished initiating JPA entities");
    }

    private List<Class<?>> discoverEntities() {
        Map<String, Class<?>> foundEntities = new HashMap<>();

        Metamodel metamodel = entityManagerFactory.getMetamodel();
        for (EntityType t : metamodel.getEntities()) {
            LOG.debug("We have a JPA Entity type: {}", t.getBindableJavaType().getCanonicalName());

            Class<?> entityType = t.getJavaType();

            foundEntities.put(entityType.getName(), entityType);
        }

        return new ArrayList<>(foundEntities.values());
    }
}
