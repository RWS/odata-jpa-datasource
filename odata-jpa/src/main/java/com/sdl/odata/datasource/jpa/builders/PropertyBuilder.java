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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.persistence.Column;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;

import com.google.common.collect.ImmutableMap;
import com.sdl.odata.api.edm.annotations.EdmNavigationProperty;
import com.sdl.odata.api.edm.annotations.EdmProperty;
import com.sdl.odata.datasource.jpa.ODataJPAProperty;
import com.sdl.odata.datasource.jpa.exceptions.JPADataMappingException;
import com.sdl.odata.edm.model.PrimitiveTypeNameResolver;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMember;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.CtPrimitiveType;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Renze de Vries
 */
public class PropertyBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(PropertyBuilder.class);
    private static final PrimitiveTypeNameResolver PRIMITIVE_TYPE_NAME_RESOLVER = new PrimitiveTypeNameResolver();

    private final ClassPool pool;

    private final Class<?> jpaType;
    private final CtClass generatedClass;
    private final ConstPool constPool;
    private final TransformContext context;
    private volatile EntityManager entityManager;

    public PropertyBuilder(TransformContext context, Class<?> jpaType, CtClass generatedClass
            , EntityManager entityManager) {
        this.context = context;
        this.jpaType = jpaType;
        this.generatedClass = generatedClass;
        this.constPool = generatedClass.getClassFile().getConstPool();
        this.pool = generatedClass.getClassPool();
        this.entityManager = entityManager;
    }

    public void build() throws JPADataMappingException {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(jpaType);
            EntityType<?> entityType = entityManager.getMetamodel().entity(jpaType);
            Set<Attribute<?, ?>> attributes = new LinkedHashSet<>(entityType.getDeclaredAttributes());
            attributes.addAll(entityType.getAttributes());
            for (Attribute<?, ?> attribute : attributes) {
                Class<?> attributeJavaType = attribute.getJavaType();
                if (attributeJavaType.isEnum()) {
                    throw new IllegalArgumentException(
                            "Enumerated types not implemented at this moment as OData "
                            + "generally maps them as INT " + attributeJavaType
                                    .getName());
                } else if (attributeJavaType.isPrimitive() ||
                           PRIMITIVE_TYPE_NAME_RESOLVER.resolveTypeName(attributeJavaType) != null
                           || attributeJavaType.isAssignableFrom(String.class)
                           || UUID.class.isAssignableFrom(attributeJavaType)
                           || Date.class.isAssignableFrom(attributeJavaType)
                           || Temporal.class.isAssignableFrom(attributeJavaType)) {
                    generateMember(attribute);
                } else {
                    generateComplexRelation(attribute);
                }
            }
        } catch (IntrospectionException e) {
            throw new JPADataMappingException("Unable to map properties for entity: " + jpaType.getName(), e);
        }
    }

    private void generateComplexRelation(Attribute<?, ?> attribute)
            throws JPADataMappingException {
        Class<?> propertyType = attribute.getJavaType();
        if (pool.getOrNull(propertyType.getName()) == null) {
            pool.makeClass(attribute.getJavaType().getName());
        }

        try {
            CtClass fieldType = pool.get(propertyType.getName());
            String propertyName = attribute.getName();
           if (propertyType.isEnum()) {
               String odataTypeName = GeneratorUtil.getODataTypeName(propertyType.getPackage().getName(),
                                                                     propertyType, context.getOdataNamespace());
               LOG.debug("Generating enumeration: {}", propertyType.getName());
               if (pool.getOrNull(odataTypeName) == null) {
                   pool.makeClass(odataTypeName);
               }
               CtClass generatedClass = pool.get(odataTypeName);

               LOG.debug("Generating field of type: {}", odataTypeName);
               generateMember(generatedClass, attribute.getName(),
                              () -> generateAnnotation(attribute, attribute.getName()));
           } else if (attribute.isCollection()) {
                Type collectionType = null;
                Class<?> itemType = null;
                Member javaMember = attribute.getJavaMember();
                if (javaMember instanceof Field) {
                    collectionType = ((Field) javaMember).getGenericType();

                } else if (javaMember instanceof Method) {
                    collectionType = ((Method) javaMember).getGenericReturnType();
                }
               if (collectionType instanceof ParameterizedType) {
                   itemType = Class.forName(((ParameterizedType) collectionType)
                                                    .getActualTypeArguments()[0].getTypeName());
               }
                String odataTypeName = GeneratorUtil.getODataTypeName(itemType.getPackage().getName(),
                                                                      itemType, context.getOdataNamespace());

                LOG.debug("Generating collection of complex types: {}", itemType.getName());
                Map<String, CtMember> members = generateMember(fieldType, propertyName,
                                               () -> generateNavigationAnnotation(propertyName));
                SignatureAttribute.ClassType classType = new SignatureAttribute.ClassType(propertyType.getName(),
                                                 new SignatureAttribute.TypeArgument[] {
                                                         new SignatureAttribute.TypeArgument(
                                                                 new SignatureAttribute.ClassType(odataTypeName))
                                                 });
                String fieldSig = classType.encode();
                String getterSig = new SignatureAttribute.MethodSignature(null,
                                                                          null,
                                                                          classType,
                                                                          null).encode();
                String setterSig = new SignatureAttribute.MethodSignature(
                       null,
                       new SignatureAttribute.Type[]{classType},
                       new SignatureAttribute.ClassType(void.class.getName()),
                       null).encode();
               if (members.containsKey("field")) {
                   members.get("field").setGenericSignature(fieldSig);
               }
               if (members.containsKey("getter")) {
                   members.get("getter").setGenericSignature(getterSig);
               }
               if (members.containsKey("setter")) {
                   members.get("setter").setGenericSignature(setterSig);
               }
            } else if (context.containsJpaType(propertyType)) {
                String odataTypeName = GeneratorUtil.getODataTypeName(propertyType.getPackage().getName(),
                        propertyType, context.getOdataNamespace());
                if (pool.getOrNull(odataTypeName) == null) {
                    pool.makeClass(odataTypeName);
                }
                CtClass generatedClass = pool.get(odataTypeName);
                pool.appendClassPath(new LoaderClassPath(PropertyBuilder.class.getClassLoader()));

                LOG.debug("Generating field of type: {}", odataTypeName);
                generateMember(generatedClass, attribute.getName(),
                               () -> generateNavigationAnnotation(attribute.getName()));
            } else {
                throw new JPADataMappingException("Found a complex relation type of an unmapped JPA type " +
                                                  propertyType);
            }
        } catch (ClassNotFoundException  e) {
            throw new JPADataMappingException("Unable to find property return type for property: " +
                                              attribute.getName(), e);
        } catch (NotFoundException e) {
            throw new JPADataMappingException("Unable to find property return type for property: " +
                                              attribute.getName(), e);
        }
    }

    private Map<String, CtMember> generateMember(CtClass fieldType, String propertyName,
                                                 Supplier<AnnotationsAttribute> s) throws JPADataMappingException {
        try {

            String methodPropName = StringUtils.capitalize(propertyName);
            CtClass wrapperType = fieldType.isPrimitive() ?
                    pool.getOrNull(((CtPrimitiveType) fieldType).getWrapperName()) : fieldType;


            CtField field = new CtField(wrapperType, propertyName, generatedClass);
            AnnotationsAttribute annotationsAttribute = s.get();
            annotationsAttribute.addAnnotation(generateJpaPropertyAnnotation());
//            field.getFieldInfo().addAttribute(annotationsAttribute);
            field.setModifiers(Modifier.PRIVATE);
            generatedClass.addField(field);

            CtMethod getter = CtNewMethod.getter("get" + methodPropName, field);
            getter.getMethodInfo().addAttribute(annotationsAttribute);
            CtMethod setter =
                    CtNewMethod.setter("set" + methodPropName, field);
            setter.insertAfter(String.format("_setProperty(\"%s\", $1); ", field.getName(), propertyName));

            generatedClass.addMethod(getter);
            generatedClass.addMethod(setter);

            return ImmutableMap.of("field", field, "getter", getter, "setter", setter);
        } catch (CannotCompileException | SecurityException e) {
            throw new JPADataMappingException("Unable to generate field: " + propertyName, e);
        }
    }

    private void generateMember(Attribute<?, ?> attribute) {
            CtClass fieldType = pool.getOrNull(attribute.getJavaType().getName());
            if (fieldType == null) {
                fieldType = pool.makeClass(attribute.getJavaType().getName());
            }

            generateMember(fieldType, attribute.getName(), () -> generateAnnotation(attribute, attribute.getName()));
    }

    private AnnotationsAttribute generateNavigationAnnotation(String propertyName) throws JPADataMappingException {
        AnnotationsAttribute fieldAttribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        Annotation propertyAnnotation = new AnnotationBuilder(constPool, EdmNavigationProperty.class)
                .addValue("name", propertyName).build();
        fieldAttribute.addAnnotation(propertyAnnotation);

        return fieldAttribute;
    }

    private Annotation generateJpaPropertyAnnotation() {
        return new AnnotationBuilder(constPool, ODataJPAProperty.class).build();
    }

    private AnnotationsAttribute generateAnnotation(Attribute<?, ?> attribute,
                                                    String propertyName) throws JPADataMappingException {
        boolean isNullable = true;
//      //Due to partial updates and selecting of specific properties, every OData entity property must be optional
//        if (attribute instanceof SingularAttribute)
//            isNullable = ((SingularAttribute)attribute).isOptional() || ((SingularAttribute<?, ?>) attribute).isId();
        AnnotationsAttribute fieldAttribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        Annotation propertyAnnotation = new AnnotationBuilder(constPool, EdmProperty.class)
                .addValue("name", propertyName).addValue("nullable", isNullable).build();
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

    /**
     * Method information about the jpa property.
     */
    private final class MethodInfo {
        private Method method;

        private boolean column = false;
        private boolean oneToMany = false;
        private boolean manyToOne = false;
        private boolean collection = false;
        private boolean key = false;

        private MethodInfo(Method method) {
            this.method = method;

            if (method != null) {
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
