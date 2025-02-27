/*
 * Copyright (C) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.task.component;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.prism.wrapper.ItemMandatoryHandler;
import com.evolveum.midpoint.gui.api.prism.wrapper.ItemWrapper;
import com.evolveum.midpoint.gui.impl.page.admin.AbstractObjectMainPanel;
import com.evolveum.midpoint.gui.impl.page.admin.task.TaskDetailsModel;
import com.evolveum.midpoint.gui.impl.prism.panel.SingleContainerPanel;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.PanelDisplay;
import com.evolveum.midpoint.web.application.PanelInstance;
import com.evolveum.midpoint.web.application.PanelType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@PanelType(name = "work")
@PanelInstance(identifier = "work", applicableForType = TaskType.class, childOf = TaskActivityPanel.class, defaultPanel = true,
        display = @PanelDisplay(label = "ActivityDefinitionType.work", icon = GuiStyleConstants.CLASS_TASK_WORK_ICON, order = 10))
public class TaskWorkPanel extends AbstractObjectMainPanel<TaskType, TaskDetailsModel> {

    private static final Trace LOGGER = TraceManager.getTrace(TaskWorkPanel.class);
    private static final String ID_MAIN_PANEL = "main";
    private static final String ID_HANDLER = "handler";

    private static final String DOT_CLASS = TaskWorkPanel.class.getName() + ".";
    private static final String OPERATION_UPDATE_WRAPPER = DOT_CLASS + "updateWrapper";

    public TaskWorkPanel(String id, TaskDetailsModel model, ContainerPanelConfigurationType config) {
        super(id, model, config);
    }

    @Override
    protected void initLayout() {
        SingleContainerPanel activityDefinitionPanel = new SingleContainerPanel(ID_MAIN_PANEL, getObjectWrapperModel(), getPanelConfiguration()) {

            @Override
            protected ItemMandatoryHandler getMandatoryHandler() {
                return wrapper -> getMandatoryOverrideFor(wrapper);
            }
        };
        add(activityDefinitionPanel);
    }

    private boolean getMandatoryOverrideFor(ItemWrapper<?, ?> itemWrapper) {
        ItemPath conflictResolutionPath = ItemPath.create(TaskType.F_ACTIVITY, ActivityDefinitionType.F_WORK,
                WorkDefinitionsType.F_RECOMPUTATION, RecomputationWorkDefinitionType.F_EXECUTION_OPTIONS,
                ModelExecuteOptionsType.F_FOCUS_CONFLICT_RESOLUTION, ConflictResolutionType.F_ACTION);
        if (conflictResolutionPath.equivalent(itemWrapper.getPath().namedSegmentsOnly())) {
            return false;
        }

        return itemWrapper.isMandatory();
    }
}
