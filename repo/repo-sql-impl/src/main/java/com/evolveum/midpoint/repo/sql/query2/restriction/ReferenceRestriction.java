/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.repo.sql.query2.restriction;

import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.NameItemPathSegment;
import com.evolveum.midpoint.prism.query.RefFilter;
import com.evolveum.midpoint.repo.sql.data.common.ObjectReference;
import com.evolveum.midpoint.repo.sql.query2.InterpretationContext;
import com.evolveum.midpoint.repo.sql.query.QueryException;
import com.evolveum.midpoint.repo.sql.query2.InterpretationContext.ProperDefinitionSearchResult;
import com.evolveum.midpoint.repo.sql.query2.definition.Definition;
import com.evolveum.midpoint.repo.sql.query2.definition.EntityDefinition;
import com.evolveum.midpoint.repo.sql.query2.definition.PropertyDefinition;
import com.evolveum.midpoint.repo.sql.query2.definition.ReferenceDefinition;
import com.evolveum.midpoint.repo.sql.query2.hqm.condition.AndCondition;
import com.evolveum.midpoint.repo.sql.query2.hqm.condition.Condition;
import com.evolveum.midpoint.repo.sql.util.ClassMapper;
import com.evolveum.midpoint.repo.sql.util.RUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConstructionType;
import org.apache.commons.lang.StringUtils;
import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

import javax.xml.namespace.QName;
import java.util.List;

/**
 * @author lazyman
 */
public class ReferenceRestriction extends ItemRestriction<RefFilter> {

    public ReferenceRestriction(EntityDefinition rootEntityDefinition, String startPropertyPath, EntityDefinition startEntityDefinition) {
        super(rootEntityDefinition, startPropertyPath, startEntityDefinition);
    }

    // modelled after PropertyRestriction.interpretInternal, with some differences
    @Override
    public Condition interpretInternal(String hqlPath) throws QueryException {

        List<? extends PrismValue> values = filter.getValues();
        if (values != null && values.size() > 1) {
            throw new QueryException("Ref filter '" + filter + "' contain more than one reference value (which is not supported for now).");
        }
        PrismReferenceValue refValue = null;
        if (values != null && !values.isEmpty()) {
            refValue = (PrismReferenceValue) values.get(0);
        }

        InterpretationContext context = getContext();
        ItemPath fullPath = getFullPath(filter.getPath());
        ProperDefinitionSearchResult<Definition> defResult = context.findProperDefinition(fullPath, Definition.class);
        if (defResult == null || defResult.getItemDefinition() == null) {
            throw new QueryException("Definition for " + fullPath + " couldn't be found.");
        }
        Definition definition = defResult.getItemDefinition();      // actually, we cannot expect ReferenceDefinition here, because e.g. linkRef has a CollectionDefinition

        String propertyFullNamePrefix = hqlPath + "." + definition.getJpaName() + ".";

        String refValueOid = null;
        QName refValueRelation = null;
        QName refValueTargetType = null;
        if (refValue != null) {
        	refValueOid = refValue.getOid();
        	refValueRelation = refValue.getRelation();
        	refValueTargetType = refValue.getTargetType();
        }
        AndCondition conjunction = Condition.and();
        conjunction.add(handleEqOrNull(propertyFullNamePrefix + ObjectReference.F_TARGET_OID, refValueOid));

        if (refValueOid != null) {
	        if (refValueRelation == null) {
	        	// Return only references without relation
	        	conjunction.add(Condition.eq(propertyFullNamePrefix + ObjectReference.F_RELATION, RUtil.QNAME_DELIMITER));
	        } else if (refValueRelation.equals(PrismConstants.Q_ANY)) {
	        	// Return all relations => no restriction
	        } else {
	        	// return references with specific relation
	            conjunction.add(handleEqOrNull(propertyFullNamePrefix + ObjectReference.F_RELATION, RUtil.qnameToString(refValueRelation)));
	        }
	
	        if (refValueTargetType != null) {
	            conjunction.add(handleEqOrNull(propertyFullNamePrefix + ObjectReference.F_TYPE,
	                    ClassMapper.getHQLTypeForQName(refValueTargetType)));
	        }
        }

        // TODO what about isNotNull if necessary ?

        return conjunction;
    }


    private Condition handleEqOrNull(String propertyName, Object value) {
        if (value == null) {
            return Condition.isNull(propertyName);
        } else {
            return Condition.eq(propertyName, value);
        }
    }
}
