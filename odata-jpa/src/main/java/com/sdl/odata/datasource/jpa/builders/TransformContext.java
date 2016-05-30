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

import java.util.List;

/**
 * The Transformation context containing all information needed in the JPA to OData entity transformation.
 * @author Renze de Vries
 */
public final class TransformContext {
    private final List<Class<?>> jpaClasses;
    private final String odataNamespace;

    public TransformContext(List<Class<?>> jpaClasses, String odataNamespace) {
        this.jpaClasses = jpaClasses;
        this.odataNamespace = odataNamespace;
    }

    public List<Class<?>> getJpaClasses() {
        return jpaClasses;
    }

    public boolean containsJpaType(Class<?> jpaType) {
        return jpaClasses.stream().anyMatch(jc -> jc.equals(jpaType));
    }

    public String getOdataNamespace() {
        return odataNamespace;
    }

    @Override
    public String toString() {
        return "TransformContext{" +
                "jpaClasses=" + jpaClasses +
                ", odataNamespace='" + odataNamespace + '\'' +
                '}';
    }
}
