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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.persistence.Entity;

/**
 * This post processor will scan for Entity annotated classes.
 * @author Renze de Vries
 */
@Component("PersistenceUnitPostProcessor")
public class JpaPostProcessor implements PersistenceUnitPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(JpaPostProcessor.class);

    private ClassPathScanningCandidateComponentProvider provider;

    @Value("${datasource.entitymodel}")
    private String entityModel;


    @PostConstruct
    public void setProvider() {
        if (provider == null) {
            provider = new ClassPathScanningCandidateComponentProvider(false);
            provider.addIncludeFilter(new AnnotationTypeFilter(Entity.class, false));
        }
    }

    @Override
    public void postProcessPersistenceUnitInfo(MutablePersistenceUnitInfo unit) {
        LOG.info("Finding entities in model: {}", entityModel);
        for (BeanDefinition bean : provider.findCandidateComponents(entityModel)) {
            unit.addManagedClassName(bean.getBeanClassName());
        }

        // Note: The managed classes are logged for debugging purposes
        for (String name : unit.getManagedClassNames()) {
            LOG.info("Registered managed class name : " + name);
        }
    }
}
