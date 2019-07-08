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
package com.sdl.odata.jpa;

import com.sdl.odata.api.edm.ODataEdmException;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.datasource.jpa.JPAODataEntityGeneratorImpl;
import com.sdl.odata.edm.factory.annotations.AnnotationEntityDataModelFactory;
import com.sdl.odata.jpa.model.PhotoItem;
import com.sdl.odata.jpa.model.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;

/**
 * @author Renze de Vries
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ServiceContainer.class)
@ActiveProfiles("test")
public class JPAODataEntityGeneratorImplTest {
    @Autowired
    private EntityManager entityManager;

    private static final Logger LOG = LoggerFactory.getLogger(JPAODataEntityGeneratorImplTest.class);

    private static final List<Class<?>> ENTITIES = new ArrayList<Class<?>>() { {
        add(PhotoItem.class);
        add(User.class);
    } };


    @Test
    public void testGenerateODataEntities() throws ODataDataSourceException, ODataEdmException {
        JPAODataEntityGeneratorImpl generator = new JPAODataEntityGeneratorImpl();
        generator.setEntityManager(entityManager);
        generator.setOdataNamespace("Sdl.ContentDelivery");

        List<Class<?>> odataClasses = generator.generateODataEntityClasses(ENTITIES);

        AnnotationEntityDataModelFactory factory = new AnnotationEntityDataModelFactory();
        odataClasses.forEach(factory::addClass);

        LOG.info("Building EntityDataModel");
        factory.buildEntityDataModel();
    }
}
