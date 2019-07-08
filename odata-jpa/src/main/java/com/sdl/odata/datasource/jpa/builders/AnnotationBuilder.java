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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.sdl.odata.datasource.jpa.exceptions.JPADataMappingException;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.AnnotationMemberValue;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.DoubleMemberValue;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.LongMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.ShortMemberValue;
import javassist.bytecode.annotation.StringMemberValue;


/**
 * @author Renze de Vries
 */
public class AnnotationBuilder {

    private static final int SINGLE_ENTRY = 1;

    private final Class<? extends java.lang.annotation.Annotation> annotation;

    private final ConstPool constPool;

    private final Multimap<String, Object> multimap = HashMultimap.create();

    public AnnotationBuilder(ConstPool constPool, Class<? extends java.lang.annotation.Annotation> annotation) {
        this.constPool = constPool;
        this.annotation = annotation;
    }

    public AnnotationBuilder addValue(String name, String value) {
        multimap.put(name, value);

        return this;
    }

    public AnnotationBuilder addValue(String name, Boolean value) {
        multimap.put(name, value);

        return this;
    }

    public AnnotationBuilder addValue(String name, Number value) {
        multimap.put(name, value);

        return this;
    }

    public AnnotationBuilder addValue(String name, String[] values) {
        multimap.put(name, Arrays.asList(values));

        return this;
    }

    public AnnotationBuilder addValue(String name, Annotation[] values) {
        multimap.put(name, Arrays.asList(values));

        return this;
    }

    public Annotation build() throws JPADataMappingException {
        Annotation generatedAnnotation = new Annotation(annotation.getName(), constPool);

        for (Map.Entry<String, Collection<Object>> entry : multimap.asMap().entrySet()) {
            String annotationPropertyName = entry.getKey();
            Collection<Object> values = entry.getValue();

            if (values.size() == SINGLE_ENTRY) {
                generatedAnnotation.addMemberValue(annotationPropertyName,
                        generateMemberValue(values.iterator().next()));
            } else if (!values.isEmpty()) {
                generatedAnnotation.addMemberValue(annotationPropertyName, generateArrayValues(values));
            }
        }

        return generatedAnnotation;
    }

    private MemberValue generateArrayValues(Collection<Object> values) throws JPADataMappingException {
        ArrayMemberValue memberValue = new ArrayMemberValue(constPool);

        List<MemberValue> memberValueList = new ArrayList<>();
        for (Object value : values) {
            memberValueList.add(generateMemberValue(value));
        }

        memberValue.setValue(memberValueList.toArray(new MemberValue[memberValueList.size()]));

        return memberValue;
    }

    private MemberValue generateMemberValue(Object value) throws JPADataMappingException {
        if (value instanceof String) {
            return new StringMemberValue(value.toString(), constPool);
        } else if (value instanceof Boolean) {
            return new BooleanMemberValue((Boolean) value, constPool);
        } else if (value instanceof Integer) {
            return new IntegerMemberValue((Integer) value, constPool);
        } else if (value instanceof Long) {
            return new LongMemberValue((Long) value, constPool);
        } else if (value instanceof Short) {
            return new ShortMemberValue((Short) value, constPool);
        } else if (value instanceof Double) {
            return new DoubleMemberValue((Double) value, constPool);
        } else if (value instanceof List) {
            return generateArrayValues((List) value);
        } else if (value instanceof Annotation) {
            return new AnnotationMemberValue((Annotation) value, constPool);
        }

        throw new JPADataMappingException("Unable to map annotation value, unsupported type");
    }
}
