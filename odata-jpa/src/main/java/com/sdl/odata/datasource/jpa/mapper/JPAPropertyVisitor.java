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

import com.google.common.base.Strings;
import com.sdl.odata.api.edm.model.StructuralProperty;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.util.edm.PropertyVisitor;
import com.sdl.odata.datasource.jpa.ODataJPAProperty;

import java.lang.reflect.Field;

import static com.sdl.odata.util.AnnotationsUtil.getAnnotation;


/**
 * Simple abstract property visitor that reads the JPA metadata.
 */
public abstract class JPAPropertyVisitor implements PropertyVisitor<ODataDataSourceException> {
    @Override
    public void visit(StructuralProperty property) throws ODataDataSourceException {
        Field field = property.getJavaField();

        ODataJPAProperty jpaPropertyAnno = getAnnotation(field, ODataJPAProperty.class);
        String jpaFieldName = jpaPropertyAnno.value();

        if (Strings.isNullOrEmpty(jpaFieldName)) {
            jpaFieldName = field.getName();
        }

        visit(property, jpaFieldName);
    }

    public abstract void visit(StructuralProperty property, String jpaFieldName) throws ODataDataSourceException;
}
