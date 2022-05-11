package com.evolveum.midpoint.gui.impl.component.search;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SearchItemType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

import org.apache.commons.lang3.BooleanUtils;
import java.util.List;

public class DeadShadowSearchItemWrapper<T> extends ChoicesSearchItemWrapper<T> {

    public DeadShadowSearchItemWrapper(List<DisplayableValue<T>> availableValues) {
        super(ShadowType.F_DEAD, availableValues);
    }

    @Override
    public ObjectFilter createFilter(Class type, PageBase pageBase, VariablesMap variables) {
        DisplayableValue<Boolean> selectedValue = getValue();
        if (selectedValue == null) {
            return null;
        }
        Boolean value = selectedValue.getValue();
        if (BooleanUtils.isTrue(value)) {
            return null; // let the default behavior to take their chance
        }

        return PrismContext.get().queryFor(ShadowType.class)
                .not()
                .item(ShadowType.F_DEAD)
                .eq(true)
                .buildFilter();
    }

}
