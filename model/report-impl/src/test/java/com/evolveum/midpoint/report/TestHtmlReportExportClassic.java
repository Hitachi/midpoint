/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.report;

import java.io.File;
import java.util.List;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.TestResource;
import com.evolveum.midpoint.test.util.MidPointTestConstants;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author skublik
 */

public class TestHtmlReportExportClassic extends EmptyReportIntegrationTest {

    private static final int USERS = 50;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);

        repoAdd(TASK_EXPORT_CLASSIC, initResult);

        repoAdd(REPORT_AUDIT_COLLECTION_WITH_DEFAULT_COLUMN, initResult);
        repoAdd(REPORT_AUDIT_COLLECTION_WITH_VIEW, initResult);
        repoAdd(REPORT_AUDIT_COLLECTION_WITH_DOUBLE_VIEW, initResult);
        repoAdd(REPORT_AUDIT_COLLECTION_WITH_CONDITION, initResult);
        repoAdd(REPORT_AUDIT_COLLECTION_EMPTY, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_WITH_DEFAULT_COLUMN, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_WITH_VIEW, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_WITH_DOUBLE_VIEW, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_WITH_FILTER, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_WITH_FILTER_AND_BASIC_COLLECTION, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_WITH_CONDITION, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_EMPTY, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_FILTER_BASIC_COLLECTION_WITHOUT_VIEW, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_WITH_PARAM, initResult);
        repoAdd(REPORT_OBJECT_COLLECTION_WITH_SUBREPORT_PARAM, initResult);
        repoAdd(REPORT_USER_LIST, initResult);
        repoAdd(REPORT_USER_LIST_SCRIPT, initResult);
        repoAdd(REPORT_DASHBOARD_WITH_DEFAULT_COLUMN, initResult);
        repoAdd(REPORT_DASHBOARD_WITH_VIEW, initResult);
        repoAdd(REPORT_DASHBOARD_WITH_TRIPLE_VIEW, initResult);
        repoAdd(REPORT_DASHBOARD_EMPTY, initResult);

        repoAdd(OBJECT_COLLECTION_ALL_AUDIT_RECORDS, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_AUDIT_RECORDS_WITH_VIEW, initResult);
        repoAdd(OBJECT_COLLECTION_AUDIT_EMPTY, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_USERS, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_USERS_WITH_VIEW, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_ROLES, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_ROLES_WITH_VIEW, initResult);
        repoAdd(OBJECT_COLLECTION_BASIC_FILTER, initResult);
        repoAdd(OBJECT_COLLECTION_EMPTY, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_RESOURCE, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_RESOURCE_WITH_VIEW, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_ASSIGNMENT_HOLDER, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_ASSIGNMENT_HOLDER_WITH_VIEW, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_TASK, initResult);
        repoAdd(OBJECT_COLLECTION_ALL_TASK_WITH_VIEW, initResult);
        repoAdd(OBJECT_COLLECTION_SHADOW_OF_RESOURCE, initResult);
        repoAdd(OBJECT_COLLECTION_SHADOW_OF_RESOURCE_WITH_VIEW, initResult);

        repoAdd(DASHBOARD_DEFAULT_COLUMNS, initResult);
        repoAdd(DASHBOARD_WITH_VIEW, initResult);
        repoAdd(DASHBOARD_WITH_TRIPLE_VIEW, initResult);
        repoAdd(DASHBOARD_EMPTY, initResult);

        createUsers(USERS, initTask, initResult);
    }

    @Test
    public void test001DashboardReportWithDefaultColumn() throws Exception {
        runTest(REPORT_DASHBOARD_WITH_DEFAULT_COLUMN);
    }

    @Test
    public void test002DashboardReportWithView() throws Exception {
        runTest(REPORT_DASHBOARD_WITH_VIEW);
    }

    @Test
    public void test003DashboardReportWithTripleView() throws Exception {
        runTest(REPORT_DASHBOARD_WITH_TRIPLE_VIEW);
    }

    @Test
    public void test004DashboardReportEmpty() throws Exception {
        runTest(REPORT_DASHBOARD_EMPTY);
    }

    @Test
    public void test005DashboardReportAndStoredWidgetData() throws Exception {
        given();

        Task task = getTestTask();
        OperationResult result = task.getResult();

        modifyObjectReplaceProperty(ReportType.class,
                REPORT_DASHBOARD_WITH_DEFAULT_COLUMN.oid,
                ItemPath.create(ReportType.F_DASHBOARD, DashboardReportEngineConfigurationType.F_STORE_EXPORTED_WIDGET_DATA),
                task,
                result,
                StoreExportedWidgetDataType.ONLY_WIDGET
        );

        when();

        runExportTaskClassic(REPORT_DASHBOARD_WITH_DEFAULT_COLUMN, result);
        waitForTaskCloseOrSuspend(TASK_EXPORT_CLASSIC.oid);

        then();

        assertTask(TASK_EXPORT_CLASSIC.oid, "after")
                .assertSuccess()
                .display();

        PrismObject<ReportType> report = getObject(ReportType.class, REPORT_DASHBOARD_WITH_DEFAULT_COLUMN.oid);
        File outputFile = findOutputFile(report);
        displayValue("Found report file", outputFile);
        assertTrue("Output file for " + report + " exists", outputFile == null);

        PrismObject<DashboardType> dashboard = getObject(DashboardType.class, DASHBOARD_DEFAULT_COLUMNS.oid);
        dashboard.asObjectable().getWidget().forEach(widget -> {
            assertTrue(
                    "Stored data in widget " + widget.getIdentifier() + " is null",
                    widget.getData() != null && widget.getData().getStoredData() != null
            );
        });
    }

    @Test
    public void test100AuditCollectionReportWithDefaultColumn() throws Exception {
        runTest(REPORT_AUDIT_COLLECTION_WITH_DEFAULT_COLUMN);
    }

    @Test
    public void test101AuditCollectionReportWithView() throws Exception {
        runTest(REPORT_AUDIT_COLLECTION_WITH_VIEW);
    }

    @Test
    public void test102AuditCollectionReportWithDoubleView() throws Exception {
        runTest(REPORT_AUDIT_COLLECTION_WITH_DOUBLE_VIEW);
    }

    @Test
    public void test103AuditCollectionReportWithCondition() throws Exception {
        runTest(REPORT_AUDIT_COLLECTION_WITH_CONDITION);
    }

    @Test
    public void test104AuditCollectionReportEmpty() throws Exception {
        runTest(REPORT_AUDIT_COLLECTION_EMPTY);
    }

    @Test
    public void test110ObjectCollectionReportWithDefaultColumn() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_WITH_DEFAULT_COLUMN);
    }

    @Test
    public void test112ObjectCollectionReportWithView() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_WITH_VIEW);
    }

    @Test
    public void test113ObjectCollectionReportWithDoubleView() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_WITH_DOUBLE_VIEW);
    }

    @Test
    public void test114ObjectCollectionReportWithFilter() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_WITH_FILTER);
    }

    @Test
    public void test115ObjectCollectionReportWithFilterAndBasicCollection() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_WITH_FILTER_AND_BASIC_COLLECTION);
    }

    @Test
    public void test116ObjectCollectionReportWithCondition() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_WITH_CONDITION);
    }

    @Test
    public void test117ObjectCollectionEmptyReport() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_EMPTY);
    }

    @Test
    public void test118ObjectCollectionReportWithFilterAndBasicCollectionWithoutView() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_FILTER_BASIC_COLLECTION_WITHOUT_VIEW);
    }

    @Test
    public void test119ObjectCollectionWithParamReport() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_WITH_PARAM);
    }

    @Test
    public void test120ObjectCollectionWithSubreportParamReport() throws Exception {
        runTest(REPORT_OBJECT_COLLECTION_WITH_SUBREPORT_PARAM);
    }

    @Test
    public void test121RunMidpointUsers() throws Exception {
        runTest(REPORT_USER_LIST);
    }

    @Test
    public void test122RunMidpointUsersScript() throws Exception {
        if (!isOsUnix()) {
            displaySkip();
            return;
        }
        runTest(REPORT_USER_LIST_SCRIPT);
        File targetFile = new File(MidPointTestConstants.TARGET_DIR_PATH, "report-users");
        assertTrue("Target file is not there", targetFile.exists());
    }

    @Override
    protected FileFormatConfigurationType getFileFormatConfiguration() {
        FileFormatConfigurationType config = new FileFormatConfigurationType();
        config.setType(FileFormatTypeType.HTML);
        return config;
    }

    private void runTest(TestResource<ReportType> reportResource) throws Exception {
        given();

        Task task = getTestTask();
        OperationResult result = task.getResult();

        when();

        dummyTransport.clearMessages();

        runExportTaskClassic(reportResource, result);
        waitForTaskCloseOrSuspend(TASK_EXPORT_CLASSIC.oid);

        then();

        assertTask(TASK_EXPORT_CLASSIC.oid, "after")
                .assertSuccess()
                .display();

        assertNotificationMessage(reportResource.getObjectable(), "text/html");

        PrismObject<ReportType> report = getObject(ReportType.class, reportResource.oid);
        List<String> lines = getLinesOfOutputFile(report);

        if (lines.size() < 10) {
            fail("Html report too short ("+lines.size()+" lines)");
        }
    }
}
