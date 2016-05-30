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

import java.util.Map;

/**
 * The JPA Query containing the query string and parameters needed against the entitymanager.
 * @author Renze de Vries
 */
public final class JPAQuery {

    private final String queryString;
    private final Map<String, Object> queryParams;

    private final int limitCount;
    private final int skipCount;

    public JPAQuery(String queryString, Map<String, Object> queryParams, int limitCount, int skipCount) {
        this.queryString = queryString;
        this.queryParams = queryParams;
        this.limitCount = limitCount;
        this.skipCount = skipCount;
    }

    public JPAQuery(String queryString, Map<String, Object> queryParams) {
        this(queryString, queryParams, -1, -1);
    }

    public String getQueryString() {
        return queryString;
    }

    public Map<String, Object> getQueryParams() {
        return queryParams;
    }

    public int getLimitCount() {
        return limitCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    @Override
    public String toString() {
        return queryString + ", params=" + queryParams;
    }
}
