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

import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;

import com.sdl.odata.api.edm.annotations.EdmEntity;
import com.sdl.odata.api.edm.annotations.EdmEntitySet;
import com.sdl.odata.api.edm.annotations.EdmPropertyRef;
import com.sdl.odata.api.processor.datasource.ODataDataSourceException;
import com.sdl.odata.datasource.jpa.ODataJPAEntity;
import com.sdl.odata.datasource.jpa.exceptions.JPADataMappingException;
import com.sdl.odata.datasource.jpa.model.MapBackedEntity;
import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private volatile EntityManager entityManager;

    public EntityBuilder(Class<?> jpaType, TransformContext context, EntityManager entityManager) {
        this.jpaPackage = jpaType.getPackage().getName();
        this.jpaType = jpaType;
        this.context = context;
        this.entityManager = entityManager;
        pool.appendClassPath(new ClassClassPath(this.getClass()));
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
            classFile.setSuperclass(MapBackedEntity.class.getName());
            new PropertyBuilder(context, jpaType, generatedClass, entityManager).build();

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
        readKeys(jpaType, entityAnnotationBuilder, constPool);

        return entityAnnotationBuilder.build();
    }

    private void readKeys(Class<?> jpaType, AnnotationBuilder entityAnnotationBuilder,
                          ConstPool constPool) throws JPADataMappingException {
            EntityType entityType = entityManager.getMetamodel().entity(jpaType);

            List<String> keys = new ArrayList<>();
            List<Annotation> keyRefs = new ArrayList<>();
            SingularAttribute idAttribute = entityType.getId(entityType.getIdType().getJavaType());
            if (idAttribute != null) {
                keys.add(idAttribute.getName());
                keyRefs.add(new AnnotationBuilder(constPool, EdmPropertyRef.class)
                           .addValue("path", idAttribute.getName())
                           .build());
            }

            entityAnnotationBuilder.addValue("key", keys.toArray(new String[0]));
            entityAnnotationBuilder.addValue("keyRef", keyRefs.toArray(new Annotation[0]));

    }
}
