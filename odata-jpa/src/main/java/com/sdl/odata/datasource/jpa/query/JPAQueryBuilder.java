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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;



/**
 * JPA query builder.
 *
 * See http://docs.oracle.com/cd/E17904_01/apirefs.1111/e13946/ejb3_langref.html
 *
 * @author Jesper de Jong
 */
public final class JPAQueryBuilder {
    private List<String> selectList = new ArrayList<>();

    private boolean distinct;

    private String fromCollection;
    private String fromAlias;

    private List<JoinString> joinStrings = new ArrayList<>();
    private List<String> expandFields = new ArrayList<>();

    private String whereClause;

    private List<String> orderByFields = new ArrayList<>();

    private int limitCount;
    private int skipCount;

    private Map<String, Object> params = new HashMap<>();

    public List<String> getSelectList() {
        return selectList;
    }

    public JPAQueryBuilder setSelectList(List<String> selectList) {
        this.selectList = selectList;
        return this;
    }

    public JPAQueryBuilder addToSelectList(String name) {
        this.selectList.add(name);
        return this;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public JPAQueryBuilder setDistinct(boolean distinct) {
        this.distinct = distinct;
        return this;
    }

    public String getFromCollection() {
        return fromCollection;
    }

    public JPAQueryBuilder setFromCollection(String fromCollection) {
        this.fromCollection = fromCollection;
        return this;
    }

    public String getFromAlias() {
        return fromAlias;
    }

    public JPAQueryBuilder setFromAlias(String fromAlias) {
        this.fromAlias = fromAlias;
        return this;
    }

    public List<JoinString> getJoinStrings() {
        return joinStrings;
    }

    public JPAQueryBuilder addJoinString(JoinString joinString) {
        this.joinStrings.add(joinString);
        return this;
    }

    public JPAQueryBuilder addJoinStrings(List<JoinString> joinStrings) {
        this.joinStrings.addAll(joinStrings);
        return this;
    }

    public List<String> getExpandFields() {
        return expandFields;
    }

    public JPAQueryBuilder addExpandField(String expandField) {
        this.expandFields.add(expandField);
        return this;
    }

    public JPAQueryBuilder addExpandFields(List<String> expandFields) {
        this.expandFields.addAll(expandFields);
        return this;
    }

    public String getWhereClause() {
        return whereClause;
    }

    public JPAQueryBuilder setWhereClause(String whereClause) {
        this.whereClause = whereClause;
        return this;
    }

    public List<String> getOrderByFields() {
        return orderByFields;
    }

    public JPAQueryBuilder addOrderByField(String orderByField) {
        this.orderByFields.add(orderByField);
        return this;
    }

    public JPAQueryBuilder addOrderByFields(List<String> orderByFields) {
        this.orderByFields.addAll(orderByFields);
        return this;
    }

    public int getLimitCount() {
        return limitCount;
    }

    public JPAQueryBuilder setLimitCount(int limitCount) {
        this.limitCount = limitCount;
        return this;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public JPAQueryBuilder setSkipCount(int skipCount) {
        this.skipCount = skipCount;
        return this;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public JPAQueryBuilder addParams(Map<String, Object> params) {
        this.params.putAll(params);
        return this;
    }

    public JPAQueryBuilder addParam(String name, Object value) {
        this.params.put(name, value);
        return this;
    }

    public JPAQuery build() {
        List<String> columns = Collections.emptyList();
        StringBuilder queryStringBuilder = new StringBuilder();

        // SELECT [DISTINCT]
        queryStringBuilder.append("SELECT ");
        if (isDistinct()) {
            queryStringBuilder.append("DISTINCT ");
        }

        queryStringBuilder.append(fromAlias);
//        if (!selectList.isEmpty()) {
//            Joiner.on(',').appendTo(queryStringBuilder, selectList);
//            columns = selectList.stream().map(c -> StringUtils.substringAfter(c, ".")).collect(Collectors.toList());
//        }

        // FROM <fromCollection> <fromAlias>
        queryStringBuilder.append(" FROM ").append(fromCollection).append(' ').append(fromAlias);

        // [LEFT] JOIN ...
        if (!joinStrings.isEmpty()) {
            for (JoinString joinString : joinStrings) {
                if (joinString.getJoinType() == JoinString.JoinType.OUTER) {
                    queryStringBuilder.append(" LEFT");
                }
                queryStringBuilder.append(" JOIN ").append(joinString.getString());
            }
        }

        // JOIN FETCH ...
        if (!expandFields.isEmpty()) {
            for (String expandField : expandFields) {
                queryStringBuilder.append(" LEFT JOIN FETCH ").append(expandField);
            }
        }

        // WHERE ...
        if (!Strings.isNullOrEmpty(whereClause)) {
            queryStringBuilder.append(" WHERE ").append(whereClause);
        }

        if (!orderByFields.isEmpty()) {
            queryStringBuilder.append(" ORDER BY ");
            Joiner.on(',').appendTo(queryStringBuilder, orderByFields);
        }

        return new JPAQuery(queryStringBuilder.toString(), params, columns, expandFields, limitCount, skipCount);
    }
}
