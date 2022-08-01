/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType;

import com.evolveum.midpoint.prism.path.ItemPath;

import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.gui.impl.page.admin.resource.ResourceDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.basic.AbstractFormResourceWizardStepPanel;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceAttributeDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

/**
 * @author lskublik
 */
public abstract class AbstractOutboundStepPanel<C extends Containerable> extends AbstractFormResourceWizardStepPanel {

    private static final String PANEL_TYPE = "OutboundAttributeMappingWizard";

    private final IModel<PrismContainerValueWrapper<C>> newValueModel;

    public AbstractOutboundStepPanel(ResourceDetailsModel model,
                                     IModel<PrismContainerValueWrapper<C>> newValueModel) {
        super(model);
        this.newValueModel = newValueModel;
    }

    @Override
    protected IModel<? extends PrismContainerWrapper> getContainerFormModel() {
        PrismContainerWrapperModel<C, Containerable> model
                = PrismContainerWrapperModel.fromContainerValueWrapper(newValueModel, getContainerPath());
        model.getObject().setExpanded(true);
        return model;
    }

    protected ItemPath getContainerPath() {
        return ResourceAttributeDefinitionType.F_OUTBOUND;
    }

    @Override
    protected boolean isExitButtonVisible() {
        return true;
    }

    @Override
    protected String getIcon() {
        return "fa fa-circle";
    }
}
