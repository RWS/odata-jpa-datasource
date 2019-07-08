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
import com.sdl.odata.api.ODataException;
import com.sdl.odata.api.ODataSystemException;
import com.sdl.odata.api.edm.model.EntityDataModel;
import com.sdl.odata.api.edm.model.EntitySet;
import com.sdl.odata.api.edm.model.EntityType;
import com.sdl.odata.api.processor.query.CriteriaFilterOperation;
import com.sdl.odata.api.processor.query.ExpandOperation;
import com.sdl.odata.api.processor.query.JoinOperation;
import com.sdl.odata.api.processor.query.JoinSelect;
import com.sdl.odata.api.processor.query.JoinSelectLeft$;
import com.sdl.odata.api.processor.query.JoinSelectRight$;
import com.sdl.odata.api.processor.query.LimitOperation;
import com.sdl.odata.api.processor.query.OrderByOperation;
import com.sdl.odata.api.processor.query.OrderByProperty;
import com.sdl.odata.api.processor.query.QueryOperation;
import com.sdl.odata.api.processor.query.SelectByKeyOperation;
import com.sdl.odata.api.processor.query.SelectOperation;
import com.sdl.odata.api.processor.query.SelectPropertiesOperation;
import com.sdl.odata.api.processor.query.SkipOperation;
import org.springframework.util.StringUtils;

import static com.sdl.odata.datasource.jpa.util.JPAMetadataUtil.getJPACollectionName;
import static com.sdl.odata.datasource.jpa.util.JPAMetadataUtil.getJPAPropertyName;

/**
 * JPA Strategy for building a JPA query from the OData query model.
 * @author Renze de Vries
 */
public final class JPAQueryStrategyBuilder {
    private final EntityDataModel entityDataModel;

    private int aliasCount = 0;

    private int paramCount = 0;

    public JPAQueryStrategyBuilder(EntityDataModel entityDataModel) {
        this.entityDataModel = entityDataModel;
    }


    public JPAQuery build(QueryOperation operation) throws ODataException {
        return  buildFromOperation(operation).build();
    }

    private JPAQueryBuilder buildFromOperation(QueryOperation operation) throws ODataException {
        if (operation instanceof JoinOperation) {
            return buildFromJoin((JoinOperation) operation);
        } else if (operation instanceof SelectOperation) {
            return buildFromSelect((SelectOperation) operation);
        } else if (operation instanceof SelectByKeyOperation) {
            return buildFromSelectByKey((SelectByKeyOperation) operation);
        } else if (operation instanceof CriteriaFilterOperation) {
            return buildFromCriteriaFilter((CriteriaFilterOperation) operation);
        } else if (operation instanceof LimitOperation) {
            return buildFromLimit((LimitOperation) operation);
        } else if (operation instanceof SkipOperation) {
            return buildFromSkip((SkipOperation) operation);
        } else if (operation instanceof ExpandOperation) {
            return buildFromExpand((ExpandOperation) operation);
        } else if (operation instanceof OrderByOperation) {
            return buildFromOrderBy((OrderByOperation) operation);
        } else if (operation instanceof SelectPropertiesOperation) {
            return buildFromSelectProperties((SelectPropertiesOperation) operation);
        }

        throw new ODataSystemException("Unsupported query operation: " + operation);
    }

    private JPAQueryBuilder buildFromJoin(JoinOperation operation) throws ODataException {
        JPAQueryBuilder left = buildFromOperation(operation.getLeftSource());
        JPAQueryBuilder right = buildFromOperation(operation.getRightSource());

        String leftAlias = left.getFromAlias();
        String rightAlias = right.getFromAlias();

        EntityType leftEntityType = getUnderlyingEntityType(operation.getLeftSource());

        JoinString joinString = new JoinString(
                operation.isOuterJoin() ? JoinString.JoinType.OUTER : JoinString.JoinType.INNER,
                String.format("%s.%s %s", leftAlias,
                        getJPAPropertyName(leftEntityType, operation.getJoinPropertyName()), right.getFromAlias()));

        String leftWhere = left.getWhereClause();
        String rightWhere = right.getWhereClause();

        String whereClause = null;
        if (Strings.isNullOrEmpty(leftWhere)) {
            if (!Strings.isNullOrEmpty(rightWhere)) {
                whereClause = rightWhere;
            }
        } else {
            if (Strings.isNullOrEmpty(rightWhere)) {
                whereClause = leftWhere;
            } else {
                whereClause = String.format("(%s) AND (%s)", leftWhere, rightWhere);
            }
        }

        boolean distinct;
        List<String> selectList;
        final JoinSelect joinSelect = operation.getJoinSelect();
        if (joinSelect == JoinSelectLeft$.MODULE$) {
            selectList = left.getSelectList();
            if (selectList.isEmpty()) {
                selectList = Collections.singletonList(leftAlias);
            }
            distinct = left.isDistinct();
        } else if (joinSelect == JoinSelectRight$.MODULE$) {
            selectList = right.getSelectList();
            if (selectList.isEmpty()) {
                selectList = Collections.singletonList(rightAlias);
            }
            distinct = right.isDistinct();
        } else {
            throw new UnsupportedOperationException("Unsupported JoinSelect: " + joinSelect);
        }

        return left
                .setSelectList(selectList)
                .setDistinct(distinct)
                .setFromCollection(left.getFromCollection())
                .setFromAlias(leftAlias)
                .addJoinString(joinString)
                .addJoinStrings(right.getJoinStrings())
                .addExpandFields(right.getExpandFields())
                .setWhereClause(whereClause)
                .addOrderByFields(right.getOrderByFields())
                .setLimitCount(right.getLimitCount())
                .setSkipCount(right.getSkipCount())
                .addParams(left.getParams())
                .addParams(right.getParams());
    }

    private JPAQueryBuilder buildFromSelect(SelectOperation operation) {
        return new JPAQueryBuilder()
                .setDistinct(operation.isSelectDistinct())
                .setFromCollection(getJPACollectionName(entityDataModel, operation.getEntitySetName()))
                .setFromAlias("e" + (++aliasCount));
    }

    private JPAQueryBuilder buildFromSelectByKey(SelectByKeyOperation operation) throws ODataException {
        JPAQueryBuilder builder = buildFromOperation(operation.getSource());

        String alias = builder.getFromAlias();

        List<String> whereClauseElements = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, Object> entry : operation.getKeyAsJava().entrySet()) {
            String paramName = alias + entry.getKey();
            whereClauseElements.add(alias + "." + entry.getKey() + " = :" + paramName);
            params.put(paramName, entry.getValue());
        }
        if (!StringUtils.isEmpty(builder.getWhereClause())) {
            whereClauseElements.add(builder.getWhereClause());
        }

        return builder.setWhereClause(Joiner.on(" AND ").join(whereClauseElements)).addParams(params);
    }

    private JPAQueryBuilder buildFromCriteriaFilter(CriteriaFilterOperation operation) throws ODataException {
        JPAQueryBuilder builder = buildFromOperation(operation.getSource());
        JPAWhereStrategyBuilder whereStrategyBuilder =
            new JPAWhereStrategyBuilder(
                getUnderlyingEntityType(operation.getSource()),
                builder);
        whereStrategyBuilder.setParamCount(paramCount).build(operation.getCriteria());
        this.setParamCount(whereStrategyBuilder.getParamCount());
        return builder;
    }

    private JPAQueryBuilder buildFromLimit(LimitOperation operation) throws ODataException {
        return buildFromOperation(operation.getSource()).setLimitCount(operation.getCount());
    }

    private JPAQueryBuilder buildFromSkip(SkipOperation operation) throws ODataException {
        return buildFromOperation(operation.getSource()).setSkipCount(operation.getCount());
    }

    private JPAQueryBuilder buildFromExpand(ExpandOperation operation) throws ODataException {
        JPAQueryBuilder builder = buildFromOperation(operation.getSource());

        String alias = builder.getFromAlias();
        EntityType entityType = getUnderlyingEntityType(operation);

        for (String expandProperty : operation.getExpandPropertiesAsJava()) {
            builder.addToSelectList(alias + "." + getJPAPropertyName(entityType, expandProperty));
            addExpandProperty(builder, expandProperty, alias, entityType);
        }

        return builder;
    }

    private JPAQueryBuilder addExpandProperty(JPAQueryBuilder builder, String propertyName, String alias,
                                              EntityType entityType) {
        return builder.addExpandField(alias + "." + getJPAPropertyName(entityType, propertyName));
    }

    private JPAQueryBuilder buildFromOrderBy(OrderByOperation operation) throws ODataException {
        JPAQueryBuilder builder = buildFromOperation(operation.getSource());

        EntityType entityType = getUnderlyingEntityType(operation);

        String alias = builder.getFromAlias();

        for (OrderByProperty orderByProperty : operation.getOrderByPropertiesAsJava()) {
            builder.addOrderByField(alias + "." + getJPAPropertyName(entityType, orderByProperty.getPropertyName()) +
                    " " + orderByProperty.getDirection().toString());
        }

        return builder;
    }

    private JPAQueryBuilder buildFromSelectProperties(SelectPropertiesOperation operation) throws ODataException {
        JPAQueryBuilder builder = buildFromOperation(operation.getSource());

        EntityType entityType = getUnderlyingEntityType(operation);

        String alias = builder.getFromAlias();

        for (String propertyName : operation.getPropertyNamesAsJava()) {
            builder.addToSelectList(alias + "." + getJPAPropertyName(entityType, propertyName));
        }

        return builder;
    }

    private EntityType getUnderlyingEntityType(QueryOperation operation) {
        String entitySetName = operation.entitySetName();
        EntitySet entitySet = entityDataModel.getEntityContainer().getEntitySet(entitySetName);
        return (EntityType) entityDataModel.getType(entitySet.getTypeName());
    }


    public int getParamCount() {
        return paramCount;
    }

    public void setParamCount(int paramCount) {
        this.paramCount = paramCount;
    }
}
