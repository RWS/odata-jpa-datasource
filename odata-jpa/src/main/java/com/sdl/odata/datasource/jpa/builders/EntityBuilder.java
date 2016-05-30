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
package com.sdl.odata.datasource.jpa.builders;

import com.sdl.odata.api.edm.annotations.EdmEntity;
import com.sdl.odata.api.edm.annotations.EdmEntitySet;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.datasource.jpa.ODataJPAEntity;
import com.sdl.odata.datasource.jpa.exceptions.JPADataMappingException;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Id;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.sdl.odata.datasource.jpa.builders.GeneratorUtil.getODataTypeName;

/**
 * The entity builder that converts a jpa type into an OData entity.
 * @author Renze de Vries
 */
public class EntityBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(EntityBuilder.class);

    private final ClassPool pool = ClassPool.getDefault();

    private final Class<?> jpaType;

    private final String jpaPackage;
    private final TransformContext context;

    public EntityBuilder(Class<?> jpaType, TransformContext context) {
        this.jpaPackage = jpaType.getPackage().getName();
        this.jpaType = jpaType;
        this.context = context;
    }

    public Class<?> build() throws ODataDataSourceException {
        String odataTypeName = getODataTypeName(jpaPackage, jpaType, context.getOdataNamespace());
        if (pool.getOrNull(odataTypeName) == null) {
            pool.makeClass(odataTypeName);
        }

        try {
            CtClass generatedClass = pool.get(odataTypeName);
            ClassFile classFile = generatedClass.getClassFile();
            ConstPool constPool = classFile.getConstPool();

            classFile.addAttribute(buildAnnotations(constPool));
            new PropertyBuilder(context, jpaType, generatedClass).build();

            Class<?> odataEntityClass = generatedClass.toClass();
            LOG.debug("Generated odata entity class: {}", odataEntityClass);

            return odataEntityClass;
        } catch (NotFoundException | CannotCompileException e) {
            throw new JPADataMappingException("Unable to transform JPA entity class: " + jpaType.getName(), e);
        }
    }

    private AnnotationsAttribute buildAnnotations(ConstPool constPool) throws JPADataMappingException {
        AnnotationsAttribute classAnnotationAttribute = new AnnotationsAttribute(constPool,
                AnnotationsAttribute.visibleTag);

        Annotation entitySetAnnotation = new AnnotationBuilder(constPool, EdmEntitySet.class).build();
        Annotation entityAnnotation = buildEntityAnnotation(constPool);
        Annotation jpaEntityAnnotation = new AnnotationBuilder(constPool, ODataJPAEntity.class)
                .addValue("value", jpaType.getName()).build();

        classAnnotationAttribute.addAnnotation(entitySetAnnotation);
        classAnnotationAttribute.addAnnotation(entityAnnotation);
        classAnnotationAttribute.addAnnotation(jpaEntityAnnotation);

        return classAnnotationAttribute;
    }

    private Annotation buildEntityAnnotation(ConstPool constPool) throws JPADataMappingException {
        AnnotationBuilder entityAnnotationBuilder = new AnnotationBuilder(constPool, EdmEntity.class)
                .addValue("name", jpaType.getSimpleName())
                .addValue("namespace", context.getOdataNamespace());
        readKeys(jpaType, entityAnnotationBuilder);

        return entityAnnotationBuilder.build();
    }

    private void readKeys(Class<?> jpaType, AnnotationBuilder entityAnnotationBuilder) throws JPADataMappingException {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(jpaType);

            List<String> keys = new ArrayList<>();
            for (PropertyDescriptor propertyDescriptor : beanInfo.getPropertyDescriptors()) {
                LOG.debug("Processing property: {}", propertyDescriptor.getName());

                Method readMethod = propertyDescriptor.getReadMethod();
                if (readMethod != null && readMethod.getAnnotation(Id.class) != null) {

                    keys.add(propertyDescriptor.getName());
                }
            }

            entityAnnotationBuilder.addValue("key", keys);
        } catch (IntrospectionException e) {
            throw new JPADataMappingException("Unable to read bean information");
        }

    }
}
