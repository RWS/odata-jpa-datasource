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
package com.sdl.odata.datasource.jpa.query;

/**
 * Join operation data holder.
 *
 * @author Renze de Vries
 */
public class JoinString {

    /**
     * Joint type.
     */
    public enum JoinType {
        /**
         * Inner join.
         */
        INNER,
        /**
         * Outer join.
         */
        OUTER
    }

    private final JoinType joinType;
    private final String string;

    public JoinString(JoinType joinType, String string) {
        this.joinType = joinType;
        this.string = string;
    }

    public JoinType getJoinType() {
        return joinType;
    }

    public String getString() {
        return string;
    }
}
