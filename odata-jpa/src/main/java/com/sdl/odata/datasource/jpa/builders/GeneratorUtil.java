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

/**
 * @author Renze de Vries
 */
public final class GeneratorUtil {
    private GeneratorUtil() {

    }

    public static String getODataTypeName(String jpaPackage, Class<?> jpaType, String odataNamespace) {
        if (jpaType.getName().startsWith(jpaPackage)) {
            return String.format("%s.%s", odataNamespace, jpaType.getSimpleName());
        } else {
            return jpaType.getName();
        }
    }
}
