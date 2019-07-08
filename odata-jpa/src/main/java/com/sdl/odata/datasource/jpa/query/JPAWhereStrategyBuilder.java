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

import com.google.common.base.Joiner;
import com.sdl.odata.api.ODataException;
import com.sdl.odata.api.ODataNotImplementedException;
import com.sdl.odata.api.edm.model.EntityType;
import com.sdl.odata.api.processor.query.ArithmeticCriteriaValue;
import com.sdl.odata.api.processor.query.ComparisonCriteria;
import com.sdl.odata.api.processor.query.CompositeCriteria;
import com.sdl.odata.api.processor.query.Criteria;
import com.sdl.odata.api.processor.query.CriteriaValue;
import com.sdl.odata.api.processor.query.LiteralCriteriaValue;
import com.sdl.odata.api.processor.query.ModOperator$;
import com.sdl.odata.api.processor.query.PropertyCriteriaValue;
import com.sdl.odata.datasource.jpa.util.JPAMetadataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;


/**
 * This class builds where clause for given criteria.
 *
 * @author Renze de Vries
 */
public class JPAWhereStrategyBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(JPAWhereStrategyBuilder.class);

    private static final String PREFIX_PARAM = "value";

    private final EntityType targetEntityType;

    private final JPAQueryBuilder jpaQueryBuilder;

    private int paramCount = 0;

    public JPAWhereStrategyBuilder(EntityType targetEntityType, JPAQueryBuilder jpaQueryBuilder) {
        this.targetEntityType = targetEntityType;
        this.jpaQueryBuilder = jpaQueryBuilder;
    }

    /**
     * Takes either {@link com.sdl.odata.api.processor.query.CompositeCriteria} or
     * {@link com.sdl.odata.api.processor.query.ComparisonCriteria} and builds where clause and set it
     * to JPAQueryBuilder which is passed in constructor.
     *
     * @param criteria for which where clause should build
     * @throws ODataException in case of any errors
     */
    public void build(Criteria criteria) throws ODataException {
        LOG.debug("where clause is going to build for {}", criteria);
        StringBuilder builder = new StringBuilder();
        buildFromCriteria(criteria, builder);
        if (StringUtils.isEmpty(jpaQueryBuilder.getWhereClause())) {
            jpaQueryBuilder.setWhereClause(builder.toString());
        } else {
            jpaQueryBuilder.setWhereClause(Joiner.on(" AND ")
              .join(jpaQueryBuilder.getWhereClause(), builder.toString()));
        }
        LOG.debug("where clause built for {}", criteria);
    }

    private void buildFromCriteria(Criteria criteria, StringBuilder builder) throws ODataException {
        if (criteria instanceof CompositeCriteria) {
            buildFromCompositeCriteria((CompositeCriteria) criteria, builder);
        } else if (criteria instanceof ComparisonCriteria) {
            buildFromComparisonCriteria((ComparisonCriteria) criteria, builder);
        } else {
            throw new ODataNotImplementedException("Unsupported criteria type: " + criteria);
        }
    }

    private void buildFromComparisonCriteria(ComparisonCriteria criteria, StringBuilder builder) throws ODataException {
        builder.append("(");
        buildFromCriteriaValue(criteria.left(), builder);
        builder.append(' ').append(criteria.operator().toString()).append(' ');
        buildFromCriteriaValue(criteria.right(), builder);
        builder.append(")");
    }

    private void buildFromCriteriaValue(CriteriaValue value, StringBuilder builder) throws ODataException {
        if (value instanceof LiteralCriteriaValue) {
            buildFromLiteralCriteriaValue((LiteralCriteriaValue) value, builder);
        } else if (value instanceof ArithmeticCriteriaValue) {
            buildFromArithmeticCriteriaValue((ArithmeticCriteriaValue) value, builder);
        } else if (value instanceof PropertyCriteriaValue) {
            buildFromPropertyCriteriaValue((PropertyCriteriaValue) value, builder);
        } else {
            throw new ODataNotImplementedException("Unsupported criteria value type: " + value);
        }
    }

    private void buildFromLiteralCriteriaValue(LiteralCriteriaValue value, StringBuilder builder) {
        String paramName = PREFIX_PARAM + (++paramCount);
        builder.append(':').append(paramName);
        jpaQueryBuilder.addParam(paramName, value.value());
    }

    private void buildFromArithmeticCriteriaValue(ArithmeticCriteriaValue value, StringBuilder builder)
            throws ODataException {
        // The MOD operator has to be treated as a special case, because it has a different syntax in JPQL
        if (value.operator() == ModOperator$.MODULE$) {
            builder.append("MOD(");
            buildFromCriteriaValue(value.left(), builder);
            builder.append(", ");
            buildFromCriteriaValue(value.right(), builder);
            builder.append(")");
        } else {
            buildFromCriteriaValue(value.left(), builder);
            builder.append(' ').append(value.operator().toString()).append(' ');
            buildFromCriteriaValue(value.right(), builder);
        }
    }

    private void buildFromPropertyCriteriaValue(PropertyCriteriaValue value, StringBuilder builder) {
        builder.append(jpaQueryBuilder.getFromAlias());
        builder.append(".");
        builder.append(JPAMetadataUtil.getJPAPropertyName(targetEntityType, value.propertyName()));
    }

    private void buildFromCompositeCriteria(CompositeCriteria criteria, StringBuilder builder) throws ODataException {
        builder.append("(");
        buildFromCriteria(criteria.left(), builder);
        builder.append(' ').append(criteria.operator().toString()).append(' ');
        buildFromCriteria(criteria.right(), builder);
        builder.append(")");
    }

    public int getParamCount() {
        return paramCount;
    }

    public JPAWhereStrategyBuilder setParamCount(int paramCount) {
        this.paramCount = paramCount;
        return this;
    }
}
