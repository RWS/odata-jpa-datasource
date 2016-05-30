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

import com.sdl.odata.api.edm.annotations.EdmNavigationProperty;
import com.sdl.odata.api.edm.annotations.EdmProperty;
import com.sdl.odata.datasource.jpa.ODataJPAProperty;
import com.sdl.odata.datasource.jpa.exceptions.JPADataMappingException;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * @author renzedevries
 */
public class PropertyBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(PropertyBuilder.class);

    private final ClassPool pool = ClassPool.getDefault();

    private final Class<?> jpaType;
    private final CtClass generatedClass;
    private final ConstPool constPool;
    private final TransformContext context;

    public PropertyBuilder(TransformContext context, Class<?> jpaType, CtClass generatedClass) {
        this.context = context;
        this.jpaType = jpaType;
        this.generatedClass = generatedClass;
        this.constPool = generatedClass.getClassFile().getConstPool();
    }

    public void build() throws JPADataMappingException {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(jpaType);
            for (PropertyDescriptor propertyDescriptor : beanInfo.getPropertyDescriptors()) {
                LOG.info("Processing property: {}", propertyDescriptor.getName());

                MethodInfo methodInfo = new MethodInfo(propertyDescriptor.getReadMethod());
                if (methodInfo.isValid()) {
                    if(methodInfo.isPrimitiveType()) {
                        generateField(methodInfo.getReturnType(), propertyDescriptor.getName());
                    } else {
                        generateComplexRelation(propertyDescriptor, methodInfo);
                    }
                }

            }
        } catch(IntrospectionException e) {
            throw new JPADataMappingException("Unable to map properties for entity: " + jpaType.getName(), e);
        }
    }

    private void generateComplexRelation(PropertyDescriptor propertyDescriptor, MethodInfo readMethodInfo) throws JPADataMappingException {
        Class<?> propertyType = propertyDescriptor.getPropertyType();
        if(pool.getOrNull(propertyType.getName()) == null) {
            pool.makeClass(readMethodInfo.getReturnType().getName());
        }

        try {
            CtClass fieldType = pool.get(propertyType.getName());
            String propertyName = propertyDescriptor.getName();
            if (readMethodInfo.isCollection()) {
                Method readMethod = propertyDescriptor.getReadMethod();
                Type genericReturnType = readMethod.getGenericReturnType();
                Class<?> genericType = getCollectionElementType(genericReturnType);
                String odataTypeName = GeneratorUtil.getODataTypeName(genericType.getPackage().getName(), genericType, context.getOdataNamespace());

                LOG.debug("Generating collection of complex entities: {}", propertyDescriptor.getPropertyType().getName());
                CtField field = generateField(fieldType, propertyName, () -> generateNavigationAnnotation(propertyName));
                String listSig = new SignatureAttribute.ClassType(propertyType.getName(), new SignatureAttribute.TypeArgument[] {
                        new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(odataTypeName))
                }).encode();
                field.setGenericSignature(listSig);
            } else if(context.containsJpaType(propertyType)) {
                String odataTypeName = GeneratorUtil.getODataTypeName(propertyType.getPackage().getName(),
                        propertyType, context.getOdataNamespace());
                if(pool.getOrNull(odataTypeName) == null) {
                    pool.makeClass(odataTypeName);
                }
                CtClass generatedClass = pool.get(odataTypeName);

                LOG.debug("Generating field of type: {}", odataTypeName);
                generateField(generatedClass, propertyDescriptor.getName(), () -> generateNavigationAnnotation(propertyDescriptor.getName()));
            } else {
                throw new JPADataMappingException("Found a complex relation type of an unmapped JPA type");
            }
        } catch(NotFoundException e) {
            throw new JPADataMappingException("Unable to find property return type for property: " + propertyDescriptor.getName(), e);
        }
    }

    private CtField generateField(CtClass fieldType, String propertyName, Supplier<AnnotationsAttribute> s) throws JPADataMappingException {
        try {
            CtField field = new CtField(fieldType, propertyName, generatedClass);
            AnnotationsAttribute annotationsAttribute = s.get();
            annotationsAttribute.addAnnotation(generateJpaPropertyAnnotation());
            field.getFieldInfo().addAttribute(annotationsAttribute);

            generatedClass.addField(field);

            return field;
        } catch(CannotCompileException e) {
            throw new JPADataMappingException("Unable to generate field: " + propertyName, e);
        }
    }

    private void generateField(Class<?> propertyType, String propertyName) {
        try {
            CtClass fieldType = pool.get(propertyType.getName());

            generateField(fieldType, propertyName, () -> generateAnnotation(propertyName));
        } catch(NotFoundException e) {
            throw new JPADataMappingException("Unable to generate field: " + propertyName, e);
        }
    }

    private AnnotationsAttribute generateNavigationAnnotation(String propertyName) throws JPADataMappingException {
        AnnotationsAttribute fieldAttribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        Annotation propertyAnnotation = new AnnotationBuilder(constPool, EdmNavigationProperty.class)
                .addValue("name", propertyName).build();
        fieldAttribute.addAnnotation(propertyAnnotation);

        return fieldAttribute;
    }

    private Annotation generateJpaPropertyAnnotation() {
        Annotation annotation = new AnnotationBuilder(constPool, ODataJPAProperty.class).build();

        return annotation;
    }

    private AnnotationsAttribute generateAnnotation(String propertyName) throws JPADataMappingException {
        AnnotationsAttribute fieldAttribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        Annotation propertyAnnotation = new AnnotationBuilder(constPool, EdmProperty.class)
                .addValue("name", propertyName).build();
        fieldAttribute.addAnnotation(propertyAnnotation);

        return fieldAttribute;
    }

    private Class<?> getCollectionElementType(Type genericType) {
        if (genericType instanceof ParameterizedType) {
            java.lang.reflect.Type[] actualTypeArguments =
                    ((ParameterizedType) genericType).getActualTypeArguments();
            if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
                return (Class<?>) actualTypeArguments[0];
            }
        }

        throw new IllegalArgumentException("The element type of this collection type cannot be determined: "
                + genericType);
    }


    private class MethodInfo {
        private Method method;

        private boolean column = false;
        private boolean oneToMany = false;
        private boolean manyToOne = false;
        private boolean collection = false;
        private boolean key = false;

        private MethodInfo(Method method) {
            this.method = method;

            if(method != null) {
                column = method.getAnnotation(Column.class) != null;
                oneToMany = method.getAnnotation(OneToMany.class) != null;
                manyToOne = method.getAnnotation(ManyToOne.class) != null;
                collection = Collection.class.isAssignableFrom(method.getReturnType());
                key = method.getAnnotation(Id.class) != null;
            }
        }

        public boolean isPrimitiveType() {
            Class<?> returnType = method.getReturnType();

            return returnType.isPrimitive() || returnType.isAssignableFrom(String.class);
        }

        public Class<?> getReturnType() {
            return method.getReturnType();
        }

        public boolean isValid() {
            return method != null && (column || oneToMany || manyToOne || key);
        }

        public boolean isColumn() {
            return column;
        }

        public boolean isOneToMany() {
            return oneToMany;
        }

        public boolean isManyToOne() {
            return manyToOne;
        }

        public boolean isCollection() {
            return collection;
        }

        public boolean isKey() {
            return key;
        }
    }
}
