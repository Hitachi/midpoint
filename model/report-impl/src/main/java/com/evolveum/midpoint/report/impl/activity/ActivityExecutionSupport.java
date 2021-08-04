/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.report.impl.activity;

import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.repo.common.ObjectResolver;
import com.evolveum.midpoint.repo.common.activity.Activity;
import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.repo.common.activity.execution.ExecutionInstantiationContext;
import com.evolveum.midpoint.repo.common.activity.state.ActivityState;
import com.evolveum.midpoint.repo.common.task.CommonTaskBeans;
import com.evolveum.midpoint.report.impl.ReportServiceImpl;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.RunningTask;

import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportExportWorkStateType;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportType;

import org.jetbrains.annotations.NotNull;

import static com.evolveum.midpoint.schema.result.OperationResultStatus.FATAL_ERROR;
import static com.evolveum.midpoint.task.api.TaskRunResult.TaskRunResultStatus.PERMANENT_ERROR;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.ReportExportWorkStateType.F_REPORT_DATA_REF;

import static java.util.Objects.requireNonNull;

/**
 * Contains common functionality for both activity executions (data creation + data aggregation).
 * This is an experiment - using object composition instead of inheritance.
 *
 * TODO better name
 */
class ActivityExecutionSupport extends AbstractReportActivitySupport {

    @NotNull private final Activity<DistributedReportExportWorkDefinition, DistributedReportExportActivityHandler> activity;

    /**
     * Global report data - point of aggregation.
     */
    private ObjectReferenceType globalReportDataRef;

    ActivityExecutionSupport(
            ExecutionInstantiationContext<DistributedReportExportWorkDefinition, DistributedReportExportActivityHandler> context) {
        super(context);
        activity = context.getActivity();
    }

    void initializeExecution(OperationResult result) throws CommonException, ActivityExecutionException {
        super.initializeExecution(result);
        globalReportDataRef = fetchGlobalReportDataRef(result);
    }

    private @NotNull ObjectReferenceType fetchGlobalReportDataRef(OperationResult result)
            throws SchemaException, ObjectNotFoundException, ActivityExecutionException {
        ActivityState activityState =
                ActivityState.getActivityStateUpwards(
                        activity.getPath().allExceptLast(),
                        runningTask,
                        ReportExportWorkStateType.COMPLEX_TYPE,
                        beans,
                        result);
        ObjectReferenceType globalReportDataRef = activityState.getWorkStateReferenceRealValue(F_REPORT_DATA_REF);
        if (globalReportDataRef == null) {
            throw new ActivityExecutionException("No global report data reference in " + activityState,
                    FATAL_ERROR, PERMANENT_ERROR);
        }
        return globalReportDataRef;
    }

    @Override
    protected ObjectResolver getObjectResolver() {
        return activity.getHandler().objectResolver;
    }

    @Override
    protected AbstractReportWorkDefinition getWorkDefinition() {
        return activity.getWorkDefinition();
    }

    @Override
    protected ReportServiceImpl getReportService() {
        return activity.getHandler().reportService;
    }

    /**
     * Should be called only after initialization.
     */
    @NotNull ObjectReferenceType getGlobalReportDataRef() {
        return requireNonNull(globalReportDataRef);
    }
}
