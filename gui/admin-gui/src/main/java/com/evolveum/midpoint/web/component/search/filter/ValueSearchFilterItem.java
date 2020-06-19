/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search.filter;

import java.io.Serializable;
import javax.xml.namespace.QName;

import org.apache.commons.collections.CollectionUtils;

import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.query.ValueFilter;

/**
 * @author honchar
 */
public class ValueSearchFilterItem<V extends PrismValue, D extends ItemDefinition> implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String F_VALUE = "value";
    public static final String F_FILTER_NAME = "filterName";
    public static final String F_APPLY_NEGATION = "applyNegation";
    public static final String F_FILTER = "filter";
    public static final String F_MATCHING_RULE = "matchingRule";

    public enum FilterName {
        EQUAL("EQUAL"),
        GREATER_OR_EQUAL("GREATER-OR-EQUAL"),
        GREATER("GREATER"),
        LESS_OR_EQUAL("LESS-OR-EQUAL"),
        LESS("LESS"),
        REF("REF"),
        SUBSTRING("SUBSTRING"),
        SUBSTRING_ANCHOR_START("SUBSTRING_ANCHOR_START"),
        SUBSTRING_ANCHOR_END("SUBSTRING_ANCHOR_END"),
        SUBSTRING_ANCHOR_START_AND_END("SUBSTRING_ANCHOR_START_AND_END");

        private String filterName;

        FilterName(String filterName) {
            this.filterName = filterName;
        }

        public String getFilterName() {
            return filterName;
        }
    }

    public enum MatchingRule {
        STRING_IGNORE_CASE(PrismConstants.STRING_IGNORE_CASE_MATCHING_RULE_NAME),
        POLY_STRING_STRICT(PrismConstants.POLY_STRING_STRICT_MATCHING_RULE_NAME),
        POLY_STRING_ORIG(PrismConstants.POLY_STRING_ORIG_MATCHING_RULE_NAME),
        POLY_STRING_NORM(PrismConstants.POLY_STRING_NORM_MATCHING_RULE_NAME),
        EXCHANGE_EMAIL_ADDRESSES(PrismConstants.EXCHANGE_EMAIL_ADDRESSES_MATCHING_RULE_NAME),
        DISTINGUISHED_NAME(PrismConstants.DISTINGUISHED_NAME_MATCHING_RULE_NAME),
        XML(PrismConstants.XML_MATCHING_RULE_NAME),
        UUID(PrismConstants.UUID_MATCHING_RULE_NAME),
        DEFAULT(PrismConstants.DEFAULT_MATCHING_RULE_NAME);

        private QName matchingRuleName;

        MatchingRule(QName matchingRuleName) {
            this.matchingRuleName = matchingRuleName;
        }

        public QName getMatchingRuleName() {
            return matchingRuleName;
        }
    }

    private boolean applyNegation;
    private ValueFilter<V, D> filter;
    private FilterName filterName;
    private MatchingRule matchingRule;

    public ValueSearchFilterItem(ValueFilter filter, boolean applyNegation) {
        this.filter = filter;
        this.applyNegation = applyNegation;
    }

    public boolean isApplyNegation() {
        return applyNegation;
    }

    public void setApplyNegation(boolean applyNegation) {
        this.applyNegation = applyNegation;
    }

    public ValueFilter getFilter() {
        return filter;
    }

    public void setFilter(ValueFilter filter) {
        this.filter = filter;
    }

    public V getValue() {
        if (filter == null || CollectionUtils.isEmpty(filter.getValues())) {
            return null;
        }
        return filter.getValues().get(0);
    }

    public FilterName getFilterName() {
        return filterName;
    }

    public void setFilterName(FilterName filterName) {
        this.filterName = filterName;
    }

    public MatchingRule getMatchingRule() {
        return matchingRule;
    }

    public void setMatchingRule(MatchingRule matchingRule) {
        this.matchingRule = matchingRule;
    }
}
