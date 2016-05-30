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
package com.tridion.odata.datasource.jpa;

import com.sdl.odata.api.edm.ODataEdmException;
import com.sdl.odata.api.edm.model.EntityDataModel;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.datasource.jpa.JPAODataEntityGeneratorImpl;
import com.sdl.odata.edm.factory.annotations.AnnotationEntityDataModelFactory;
import com.sdl.odata.jpa.model.PhotoItem;
import com.sdl.odata.jpa.model.User;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author renzedevries
 */
public class JPAODataEntityGeneratorImplTest {
    private static final Logger LOG = LoggerFactory.getLogger(JPAODataEntityGeneratorImplTest.class);

    private static List<Class<?>> ENTITY_CLASSES = new ArrayList<Class<?>>() {{
        add(PhotoItem.class);
        add(User.class);
    }};

    @Test
    public void testGenerateODataEntities() throws ODataDataSourceException, ODataEdmException {
        JPAODataEntityGeneratorImpl generator = new JPAODataEntityGeneratorImpl();
        generator.setOdataNamespace("Sdl.ContentDelivery");

        List<Class<?>> odataClasses = generator.generateODataEntityClasses(ENTITY_CLASSES);

        AnnotationEntityDataModelFactory factory = new AnnotationEntityDataModelFactory();
        odataClasses.forEach(factory::addClass);

        LOG.info("Building EntityDataModel");
        EntityDataModel entityDataModel = factory.buildEntityDataModel();
    }
}
