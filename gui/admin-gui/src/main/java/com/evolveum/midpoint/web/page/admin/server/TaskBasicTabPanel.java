/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.server;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.prism.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebPrismUtil;
import com.evolveum.midpoint.gui.impl.prism.*;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.TaskHandler;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.prism.ItemVisibility;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.model.PrismPropertyWrapperModel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;

import java.util.*;
import java.util.stream.Collectors;

public class TaskBasicTabPanel extends BasePanel<PrismObjectWrapper<TaskType>> implements RefreshableTabPanel {

    private static final transient Trace LOGGER = TraceManager.getTrace(TaskBasicTabPanel.class);
    private static final String ID_MAIN_PANEL = "main";
    private static final String ID_HANDLER = "handler";


    public TaskBasicTabPanel(String id, IModel<PrismObjectWrapper<TaskType>> model) {
        super(id, model);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private void initLayout() {
        ItemPanelSettings settings = new ItemPanelSettingsBuilder().editabilityHandler(wrapper -> getTask().getHandlerUri() == null).build();
        TaskHandlerSelectorPanel handlerSelectorPanel = new TaskHandlerSelectorPanel(ID_HANDLER, PrismPropertyWrapperModel.fromContainerWrapper(getModel(), TaskType.F_HANDLER_URI), settings) {
            @Override
            protected void onUpdatePerformed(AjaxRequestTarget target) {
                String newHandlerUri = getTask().getHandlerUri();
                if (StringUtils.isBlank(newHandlerUri) || !newHandlerUri.startsWith("http://")) {
                    LOGGER.trace("Nothing to do, handler still not set");
                    return;
                }

                TaskHandler taskHandler = getPageBase().getTaskManager().getHandler(newHandlerUri);
                if (taskHandler == null) {
                    LOGGER.trace("Nothing to do, cannot find TaskHandler for {}", newHandlerUri);
                    return;
                }

                if (!hasArchetypeAssignemnt()) {
                    try {
                        PrismContainerWrapper<AssignmentType> archetypeAssignment = TaskBasicTabPanel.this.getModelObject().findContainer(TaskType.F_ASSIGNMENT);
                        PrismContainerValue<AssignmentType> archetypeAssignmentValue = archetypeAssignment.getItem().createNewValue();
                        AssignmentType newArchetypeAssignment = archetypeAssignmentValue.asContainerable();
                        newArchetypeAssignment.setTargetRef(ObjectTypeUtil.createObjectRef(taskHandler.getArchetypeOid(), ObjectTypes.ARCHETYPE));
                        WebPrismUtil.createNewValueWrapper(archetypeAssignment, archetypeAssignmentValue, getPageBase(), target);
                    } catch (SchemaException e) {
                        LOGGER.error("Exception during assignment lookup, reason: {}", e.getMessage(), e);
                        getSession().error("Cannot set seleted handler: " + e.getMessage());
                        return;
                    }
                }

                updateHandlerPerformed(target);

            }
        };
        handlerSelectorPanel.add(new VisibleEnableBehaviour() {

            @Override
            public boolean isVisible() {
                return !hasArchetypeAssignemnt() || isSystemArchetypeAssignemnt() || isUtilityArchetypeAssignemnt();
            }

        });
        add(handlerSelectorPanel);

        ItemVisibilityHandler visibilityHandler = wrapper -> getBasicTabVisibility(wrapper.getPath());
        ItemEditabilityHandler editabilityHandler = wrapper -> getBasicTabEditability(wrapper.getPath());
        try {
            ItemPanelSettingsBuilder builder = new ItemPanelSettingsBuilder()
                    .visibilityHandler(visibilityHandler)
                    .editabilityHandler(editabilityHandler)
                    .mandatoryHandler(getItemMandatoryHandler())
                    .showOnTopLevel(true);
            Panel panel = getPageBase().initItemPanel(ID_MAIN_PANEL, TaskType.COMPLEX_TYPE, getModel(), builder.build());
            add(panel);
        } catch (SchemaException e) {
            LOGGER.error("Cannot create task basic panel: {}", e.getMessage(), e);
            getSession().error("Cannot create task basic panel"); // TODO opertion result? localization?
            throw new RestartResponseException(PageTasks.class);
        }

    }

    private ItemVisibility getBasicTabVisibility(ItemPath path) {
        if (ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_CLEANUP_POLICIES).equivalent(path)) {
            return ItemVisibility.HIDDEN;
        }

        if (TaskType.F_SUBTASK_REF.equivalent(path)) {
            return ItemVisibility.HIDDEN;
        }

        if (TaskType.F_SUBTYPE.equivalent(path)) {
            return ItemVisibility.HIDDEN;
        }

        if (TaskType.F_LIFECYCLE_STATE.equivalent(path)) {
            return ItemVisibility.HIDDEN;
        }

        if (TaskType.F_DIAGNOSTIC_INFORMATION.equivalent(path)) {
            return ItemVisibility.HIDDEN;
        }

        String taskHandler = getTask().getHandlerUri();

        if (taskHandler == null) {
            return ItemVisibility.AUTO;
        }

        List<ItemPath> pathsToShow = new ArrayList<>();
        if (taskHandler.endsWith("synchronization/task/delete/handler-3")) {
            pathsToShow = Arrays.asList(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_TYPE,
                    SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_QUERY,
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_OPTION_RAW));
        } else if (taskHandler.endsWith("model/execute-deltas/handler-3")) {
            pathsToShow = Arrays.asList(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_DELTA,
                    SchemaConstants.PATH_MODEL_EXTENSION_EXECUTE_OPTIONS,
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_OBJECT_DELTAS));
        } else if (taskHandler.endsWith("model/synchronization/task/execute/handler-3")) {
            pathsToShow = Arrays.asList(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_DELTA,
                    SchemaConstants.PATH_MODEL_EXTENSION_EXECUTE_OPTIONS,
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_WORKER_THREADS));
        } else if (taskHandler.endsWith("task/jdbc-ping/handler-3")) {
            //TODO
        } else if (taskHandler.endsWith("model/auditReindex/handler-3")) {
            //no extension attributes
        } else if (taskHandler.endsWith("task/lightweight-partitioning/handler-3")
                || taskHandler.endsWith("model/partitioned-focus-validity-scanner/handler-3")
                || taskHandler.endsWith("model/synchronization/task/partitioned-reconciliation/handler-3")
                || taskHandler.endsWith("task/generic-partitioning/handler-3")) {
            //TODO
        } else if (taskHandler.endsWith("task/workers-creation/handler-3")) {
            //TODO
        } else if (taskHandler.endsWith("task/workers-restart/handler-3")) {
            //no attributes
        } else if (taskHandler.endsWith("model/synchronization/task/delete-not-updated-shadow/handler-3")) {
            pathsToShow = Arrays.asList(ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_WORKER_THREADS),
                    //TODO notUpdatesShadowsDurtion
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_WORKER_THREADS),
                    TaskType.F_OBJECT_REF);
        } else if (taskHandler.endsWith("model/shadowRefresh/handler-3")) {
            pathsToShow = Arrays.asList(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_QUERY,
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_LAST_SCAN_TIMESTAMP_PROPERTY_NAME),
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_WORKER_THREADS));
        } else if (taskHandler.endsWith("model/object-integrity-check/handler-3")) {
            pathsToShow = Arrays.asList(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_QUERY,
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_SEARCH_OPTIONS),
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_WORKER_THREADS));
        } else if (taskHandler.endsWith("model/shadow-integrity-check/handler-3")) {
            pathsToShow = Arrays.asList(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_QUERY,
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_DIAGNOSE),
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_FIX),
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_DUPLICATE_SHADOWS_RESOLVER),
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_CHECK_DUPLICATES_ON_PRIMARY_IDENTIFIERS_ONLY),
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_DRY_RUN),
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_WORKER_THREADS));
        } else if (taskHandler.endsWith("model/reindex/handler-3")) {
            pathsToShow = Arrays.asList(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_QUERY,
                    SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_TYPE,
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_WORKER_THREADS));
        } else if (taskHandler.endsWith("model/trigger/scanner/handler-3")) {
            pathsToShow = Arrays.asList(ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_LAST_SCAN_TIMESTAMP_PROPERTY_NAME),
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_WORKER_THREADS));
        } else if (taskHandler.endsWith("model/focus-validity-scanner/handler-3model/partitioned-focus-validity-scanner/handler-3#1")
                    || taskHandler.endsWith("model/partitioned-focus-validity-scanner/handler-3#2")) {
            pathsToShow = Arrays.asList(ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_LAST_SCAN_TIMESTAMP_PROPERTY_NAME),
                    // TODO policyRule ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.PO),
                    SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_TYPE,
                    ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_WORKER_THREADS));
        }

        return shouldShowItem(path, pathsToShow);

    }

    private ItemVisibility shouldShowItem(ItemPath path, List<ItemPath> pathsToShow) {
        if (!path.startsWithName(TaskType.F_EXTENSION)) {
            return ItemVisibility.AUTO;
        }

        for (ItemPath pathToShow : pathsToShow) {
            if (pathToShow.equivalent(path)) {
                return ItemVisibility.AUTO;
            }
        }

        return ItemVisibility.HIDDEN;

    }

    private boolean getBasicTabEditability(ItemPath path) {
        if (WebComponentUtil.isRunningTask(getTask())) {
            return false;
        }

        if ((!hasArchetypeAssignemnt() || isSystemArchetypeAssignemnt() || isUtilityArchetypeAssignemnt()) && getTask().getHandlerUri() == null) {
            return false;
        }

        List<ItemPath> pathsToHide = Arrays.asList(TaskType.F_EXECUTION_STATUS, TaskType.F_NODE, TaskType.F_NODE_AS_OBSERVED,TaskType.F_RESULT_STATUS,
                TaskType.F_RESULT, TaskType.F_NEXT_RUN_START_TIMESTAMP, TaskType.F_NEXT_RETRY_TIMESTAMP, TaskType.F_UNPAUSE_ACTION, TaskType.F_TASK_IDENTIFIER,
                TaskType.F_PARENT, TaskType.F_WAITING_REASON, TaskType.F_STATE_BEFORE_SUSPEND, TaskType.F_CATEGORY, TaskType.F_OTHER_HANDLERS_URI_STACK,
                TaskType.F_CHANNEL, TaskType.F_DEPENDENT_TASK_REF, TaskType.F_LAST_RUN_START_TIMESTAMP, TaskType.F_LAST_RUN_FINISH_TIMESTAMP, TaskType.F_COMPLETION_TIMESTAMP
        );

        for (ItemPath pathToHide : pathsToHide) {
            if (pathToHide.equivalent(path)) {
                return false;
            }
        }

        return true;

    }

    private ItemMandatoryHandler getItemMandatoryHandler() {
        return itemWrapper -> {
            if (TaskType.F_RECURRENCE.equivalent(itemWrapper.getPath())) {
                return false;
            }
            return itemWrapper.isMandatory();
        };
    }

    private boolean hasArchetypeAssignemnt() {
        TaskType task = getTask();
        if (task.getAssignment() == null) {
            return false;
        }
        List<AssignmentType> archetypeAssignments = task.getAssignment()
                .stream()
                    .filter(assignmentType -> WebComponentUtil.isArchetypeAssignment(assignmentType)).collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(archetypeAssignments);
    }

    private boolean isSystemArchetypeAssignemnt() {
        TaskType task = getTask();
        if (task.getAssignment() == null) {
            return false;
        }
        List<AssignmentType> archetypeAssignments = task.getAssignment()
                .stream()
                    .filter(assignmentType -> WebComponentUtil.isArchetypeAssignment(assignmentType)
                            && SystemObjectsType.ARCHETYPE_SYSTEM_TASK.value().equals(assignmentType.getTargetRef().getOid()))
                .collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(archetypeAssignments);
    }

    private boolean isUtilityArchetypeAssignemnt() {
        TaskType task = getTask();
        if (task.getAssignment() == null) {
            return false;
        }
        List<AssignmentType> archetypeAssignments = task.getAssignment()
                .stream()
                .filter(assignmentType -> WebComponentUtil.isArchetypeAssignment(assignmentType)
                        && SystemObjectsType.ARCHETYPE_UTILITY_TASK.value().equals(assignmentType.getTargetRef().getOid()))
                .collect(Collectors.toList());
        return CollectionUtils.isNotEmpty(archetypeAssignments);
    }

    @Override
    public Collection<Component> getComponentsToUpdate() {
        return Collections.singleton(this);
    }

    private TaskType getTask() {
        return getModelObject().getObject().asObjectable();
    }

    protected void updateHandlerPerformed(AjaxRequestTarget target) {

    }
}
