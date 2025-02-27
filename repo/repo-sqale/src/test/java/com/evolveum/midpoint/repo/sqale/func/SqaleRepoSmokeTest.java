/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.func;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase.DEFAULT_SCHEMA_NAME;

import java.sql.SQLException;
import java.util.*;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditEventStage;
import com.evolveum.midpoint.audit.api.AuditEventType;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.repo.api.DeleteObjectResult;
import com.evolveum.midpoint.repo.api.RepoModifyOptions;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.sqale.SqaleRepoBaseTest;
import com.evolveum.midpoint.repo.sqale.jsonb.Jsonb;
import com.evolveum.midpoint.repo.sqale.qmodel.common.QContainer;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.MUser;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.QUser;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObject;
import com.evolveum.midpoint.repo.sqale.qmodel.org.QOrgClosure;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.QReference;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.perfmon.SqlPerformanceMonitorImpl;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sqlbase.querydsl.SqlRecorder;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.test.NullTaskImpl;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventRecordType;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventStageType;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Contains a few tests doing stuff all over the repository including a few lower level
 * (sub-repo-API) tests around Querydsl and our adaptation of it.
 * Each test method is completely self-contained.
 */
public class SqaleRepoSmokeTest extends SqaleRepoBaseTest {

    public static final byte[] JPEG_PHOTO = { 0, 1, 2 }; // not really a JPEG, of course

    @AfterMethod
    public void methodCleanup() {
        queryRecorder.stopRecording();
    }

    @Test
    public void test000Sanity() {
        assertThat(repositoryService).isNotNull();

        // DB should be empty
        assertCount(QObject.CLASS, 0);
        assertCount(QContainer.CLASS, 0);
        assertCount(QReference.CLASS, 0);
        // we just want the table and count, we don't care about "bean" type here
        FlexibleRelationalPathBase<?> oidTable = new FlexibleRelationalPathBase<>(
                void.class, "oid", DEFAULT_SCHEMA_NAME, "m_object_oid");
        assertCount(oidTable, 0);

        // selects check also mapping to M-classes
        assertThat(select(aliasFor(QObject.CLASS))).isEmpty();
        assertThat(select(aliasFor(QContainer.CLASS))).isEmpty();
        assertThat(select(aliasFor(QReference.CLASS))).isEmpty();
    }

    @Test
    public void test010RepositorySelfTest() {
        OperationResult result = createOperationResult();

        when("repository self test is called");
        repositoryService.repositorySelfTest(result);

        expect("operation is successful and contains info about round-trip time to DB");
        assertThatOperationResult(result).isSuccess();
        assertThat(result.getLastSubresult().getReturn("database-round-trip-ms"))
                .isNotNull()
                .hasSize(1);
    }

    @Test
    public void test020TestOrgClosureConsistency() throws Exception {
        OperationResult result = createOperationResult();

        given("reset closure");
        refreshOrgClosureForce();
        long baseCount = count(new QOrgClosure());

        and("user belonging to org hierarchy");
        OrgType orgRoot = new OrgType().name("orgRoot" + getTestNumber());
        String rootOid = repositoryService.addObject(orgRoot.asPrismObject(), null, result);
        OrgType org = new OrgType().name("org" + getTestNumber())
                .parentOrgRef(rootOid, OrgType.COMPLEX_TYPE);
        String orgOid = repositoryService.addObject(org.asPrismObject(), null, result);
        UserType user = new UserType().name("user" + getTestNumber())
                .parentOrgRef(orgOid, OrgType.COMPLEX_TYPE);
        repositoryService.addObject(user.asPrismObject(), null, result);

        when("testOrgClosureConsistency() is called with rebuild flag");
        repositoryService.testOrgClosureConsistency(true, result);

        expect("operation is successful and contains info about closure");
        assertThatOperationResult(result).isSuccess();
        OperationResult subresult = result.getLastSubresult();
        assertThat(subresult.getReturnSingle("closure-count")).isEqualTo(String.valueOf(baseCount));
        assertThat(subresult.getReturnSingle("expected-count"))
                // two equality rows for each org + 1 for parent reference
                .isEqualTo(String.valueOf(baseCount + 3));
        assertThat(subresult.getReturnSingle("rebuild-done")).isEqualTo("true");

        and("closure is rebuilt");
        assertThat(count(new QOrgClosure())).isEqualTo(baseCount + 3); // as explained above
    }

    @Test
    public void test021OrgClosureIsRefreshedBeforeOrgFilterQuery() throws Exception {
        OperationResult result = createOperationResult();

        given("reset closure");
        refreshOrgClosureForce();
        long baseCount = count(new QOrgClosure());

        given("user belonging to org hierarchy");
        OrgType orgRoot = new OrgType().name("orgRoot" + getTestNumber());
        String rootOid = repositoryService.addObject(orgRoot.asPrismObject(), null, result);
        OrgType org = new OrgType().name("org" + getTestNumber())
                .parentOrgRef(rootOid, OrgType.COMPLEX_TYPE);
        String orgOid = repositoryService.addObject(org.asPrismObject(), null, result);
        UserType user = new UserType().name("user" + getTestNumber())
                .parentOrgRef(orgOid, OrgType.COMPLEX_TYPE);
        String userOid = repositoryService.addObject(user.asPrismObject(), null, result);
        assertThat(count(new QOrgClosure())).isEqualTo(baseCount); // not refreshed yet

        when("query with org filter is used");
        SearchResultList<PrismObject<UserType>> users = repositoryService.searchObjects(
                UserType.class, prismContext.queryFor(UserType.class).isChildOf(rootOid).build(),
                null, result);

        expect("operation is successful and returns proper results");
        assertThatOperationResult(result).isSuccess();
        assertThat(users).hasSize(1)
                .extracting(p -> p.asObjectable().getOid())
                .containsExactlyInAnyOrder(userOid);

        and("closure is rebuilt");
        assertThat(count(new QOrgClosure())).isEqualTo(baseCount + 3); // see previous test
    }

    @Test
    public void test100AddObject() throws ObjectAlreadyExistsException, SchemaException {
        OperationResult result = createOperationResult();

        given("cleared performance information");
        clearPerformanceMonitor();

        when("correct object is added to the repository");
        UserType user = new UserType()
                .name("user" + getTestNumber());
        String userOid = repositoryService.addObject(user.asPrismObject(), null, result);

        then("added object is assigned OID and operation is success");
        assertThat(userOid).isNotNull();
        assertThat(user.getOid()).isEqualTo(userOid);
        assertThat(selectObjectByOid(QUser.class, userOid)).isNotNull();
        assertThatOperationResult(result).isSuccess();
        assertSingleOperationRecorded(REPO_OP_PREFIX + RepositoryService.OP_ADD_OBJECT);
    }

    @Test
    public void test110DeleteObject() throws Exception {
        OperationResult result = createOperationResult();

        given("existing user");
        UserType user = new UserType()
                .name("user" + getTestNumber());
        String userOid = repositoryService.addObject(user.asPrismObject(), null, result);

        and("cleared performance information");
        SqlPerformanceMonitorImpl pm = repositoryService.getPerformanceMonitor();
        pm.clearGlobalPerformanceInformation();

        when("user is deleted from the repository");
        DeleteObjectResult deleteResult =
                repositoryService.deleteObject(UserType.class, userOid, result);

        then("added object is assigned OID and operation is success");
        assertThat(deleteResult).isNotNull();
        assertThatOperationResult(result).isSuccess();
        assertThat(selectNullableObjectByOid(QUser.class, userOid)).isNull();
        assertSingleOperationRecorded(REPO_OP_PREFIX + RepositoryService.OP_DELETE_OBJECT);
    }

    @Test
    public void test200GetObject() throws Exception {
        OperationResult result = createOperationResult();

        given("existing user and cleared performance information");
        UserType user = new UserType()
                .name("user" + getTestNumber());
        String userOid = repositoryService.addObject(user.asPrismObject(), null, result);
        SqlPerformanceMonitorImpl pm = repositoryService.getPerformanceMonitor();
        pm.clearGlobalPerformanceInformation();

        when("getObject is called for known OID");
        PrismObject<UserType> object =
                repositoryService.getObject(UserType.class, userOid, null, result);

        then("object is obtained and performance monitor is updated");
        assertThatOperationResult(result).isSuccess();
        assertThat(object).isNotNull();
        assertSingleOperationRecorded(REPO_OP_PREFIX + RepositoryService.OP_GET_OBJECT);
    }

    @Test
    public void test201GetObjectWrongOid() {
        OperationResult result = createOperationResult();

        expect("getObject for non-existent OID throws exception");
        assertThatThrownBy(() -> repositoryService.getObject(
                UserType.class, UUID.randomUUID().toString(), null, result))
                .isInstanceOf(ObjectNotFoundException.class);

        and("operation result is fatal error");
        assertThatOperationResult(result).isFatalError();
    }

    @Test
    public void test202GetObjectWrongOidNonFatal() {
        OperationResult result = createOperationResult();

        expect("getObject for non-existent OID with allow-not-found options throws exception");
        assertThatThrownBy(() -> repositoryService.getObject(
                UserType.class, UUID.randomUUID().toString(),
                SelectorOptions.createCollection(GetOperationOptions.createAllowNotFound()),
                result))
                .isInstanceOf(ObjectNotFoundException.class);

        and("operation result is success with minor partial error");
        assertThatOperationResult(result).isSuccess();
    }

    @Test
    public void test203GetObjectExistingOidWrongType() throws Exception {
        OperationResult result = createOperationResult();

        given("existing user");
        UserType user = new UserType().name("user" + getTestNumber());
        String userOid = repositoryService.addObject(user.asPrismObject(), null, result);

        expect("getObject called with right OID with wrong object type throws");
        assertThatThrownBy(() -> repositoryService.getObject(
                DashboardType.class, userOid, null, result))
                .isInstanceOf(ObjectNotFoundException.class);

        and("object is obtained and performance monitor is updated");
        assertThatOperationResult(result).isFatalError();
    }

    @Test
    public void test210GetVersion() throws Exception {
        OperationResult result = createOperationResult();

        given("existing user and cleared performance information");
        UserType user = new UserType()
                .name("user" + getTestNumber());
        String userOid = repositoryService.addObject(user.asPrismObject(), null, result);
        SqlPerformanceMonitorImpl pm = repositoryService.getPerformanceMonitor();
        pm.clearGlobalPerformanceInformation();

        when("getVersion is called for known OID");
        String version = repositoryService.getVersion(UserType.class, userOid, result);

        then("non-null version string is obtained and performance monitor is updated");
        assertThatOperationResult(result).isSuccess();
        assertThat(version).isNotNull();
        assertSingleOperationRecorded(REPO_OP_PREFIX + RepositoryService.OP_GET_VERSION);
    }

    @Test
    public void test211GetVersionFailure() {
        OperationResult result = createOperationResult();

        expect("getVersion for non-existent OID throws exception");
        assertThatThrownBy(() -> repositoryService.getVersion(
                UserType.class, UUID.randomUUID().toString(), result))
                .isInstanceOf(ObjectNotFoundException.class);

        and("operation result is fatal error");
        assertThatOperationResult(result).isFatalError();
    }

    @Test
    public void test220PhotoPersistenceAdd()
            throws SchemaException, ObjectAlreadyExistsException, ObjectNotFoundException {
        OperationResult result = createOperationResult();

        when("user with photo is persisted");
        UserType user = new UserType()
                .name("user" + getTestNumber())
                .jpegPhoto(JPEG_PHOTO);
        String userOid = repositoryService.addObject(user.asPrismObject(), null, result);
        assertThatOperationResult(result).isSuccess();

        then("photo is stored in row, but not in fullObject");
        MUser row = selectObjectByOid(QUser.class, UUID.fromString(userOid));
        assertThat(row.photo).isEqualTo(JPEG_PHOTO);
        UserType fullObjectUser = parseFullObject(row.fullObject);
        assertThat(fullObjectUser.getJpegPhoto()).isNull();

        and("user obtained without special options does not have the photo");
        UserType userWithoutPhoto =
                repositoryService.getObject(UserType.class, userOid, null, result)
                        .asObjectable();
        assertThat(userWithoutPhoto.getJpegPhoto()).isNull();

        and("user obtained with options to fetch photo has the photo");
        Collection<SelectorOptions<GetOperationOptions>> photoOptions = SchemaService.get()
                .getOperationOptionsBuilder().item(FocusType.F_JPEG_PHOTO).retrieve().build();
        UserType userWithPhoto =
                repositoryService.getObject(UserType.class, userOid, photoOptions, result)
                        .asObjectable();
        assertThat(userWithPhoto.getJpegPhoto()).isEqualTo(JPEG_PHOTO);
        assertThat(userWithPhoto.asPrismObject().findProperty(FocusType.F_JPEG_PHOTO).isIncomplete()).isFalse();
    }

    @Test
    public void test221PhotoPersistenceModify()
            throws SchemaException, ObjectAlreadyExistsException, ObjectNotFoundException {
        OperationResult result = createOperationResult();

        given("user without photo");
        UserType user = new UserType()
                .name("user" + getTestNumber());
        String userOid = repositoryService.addObject(user.asPrismObject(), null, result);

        when("photo is added for the user");
        //noinspection PrimitiveArrayArgumentToVarargsMethod
        repositoryService.modifyObject(UserType.class, userOid,
                prismContext.deltaFor(UserType.class)
                        .item(UserType.F_JPEG_PHOTO).add(JPEG_PHOTO)
                        .asObjectDelta(userOid).getModifications(),
                result);
        assertThatOperationResult(result).isSuccess();

        then("photo is stored in row, but not in fullObject");
        MUser row = selectObjectByOid(QUser.class, UUID.fromString(userOid));
        assertThat(row.photo).isEqualTo(JPEG_PHOTO);
        UserType fullObjectUser = parseFullObject(row.fullObject);
        assertThat(fullObjectUser.getJpegPhoto()).isNull();
    }

    @Test
    public void test222PhotoPersistenceReindex()
            throws SchemaException, ObjectAlreadyExistsException, ObjectNotFoundException {
        OperationResult result = createOperationResult();

        given("user with photo");
        UserType user = new UserType()
                .name("user" + getTestNumber())
                .jpegPhoto(JPEG_PHOTO);
        String userOid = repositoryService.addObject(user.asPrismObject(), null, result);

        when("user is reindexed");
        repositoryService.modifyObject(UserType.class, userOid,
                List.of(), RepoModifyOptions.createForceReindex(), result);
        assertThatOperationResult(result).isSuccess();

        then("photo is still in row, but not in fullObject");
        MUser row = selectObjectByOid(QUser.class, UUID.fromString(userOid));
        assertThat(row.photo).isEqualTo(JPEG_PHOTO);
        UserType fullObjectUser = parseFullObject(row.fullObject);
        assertThat(fullObjectUser.getJpegPhoto()).isNull();
    }

    @Test
    public void test300AddDiagnosticInformation() throws Exception {
        OperationResult result = createOperationResult();

        given("a task (or any object actually) without diag info");
        TaskType task = new TaskType()
                .name("task" + getTestNumber());
        String taskOid = repositoryService.addObject(task.asPrismObject(), null, result);
        PrismObject<TaskType> taskFromDb =
                repositoryService.getObject(TaskType.class, taskOid, null, result);
        assertThat(taskFromDb.asObjectable().getDiagnosticInformation()).isNullOrEmpty();

        when("adding diagnostic info");
        DiagnosticInformationType event = new DiagnosticInformationType()
                .timestamp(XmlTypeConverter.createXMLGregorianCalendar(new Date()))
                .type(SchemaConstants.TASK_THREAD_DUMP_URI)
                .cause("cause")
                .nodeIdentifier("node-id")
                .content("dump");
        repositoryService.addDiagnosticInformation(TaskType.class, taskOid, event, result);

        then("operation is success and the info is there");
        assertThatOperationResult(result).isSuccess();
        taskFromDb = repositoryService.getObject(TaskType.class, taskOid, null, result);
        assertThat(taskFromDb).isNotNull();
        assertThat(taskFromDb.asObjectable().getDiagnosticInformation())
                .isNotEmpty()
                .anyMatch(d -> d.getType().equals(SchemaConstants.TASK_THREAD_DUMP_URI));
    }

    @Test
    public void test400SqlLogger() throws Exception {
        if (!SqlRecorder.LOGGER.isDebugEnabled()) {
            throw new SkipException("We need debug on SqlRecorder logger for this test");
        }

        OperationResult result = createOperationResult();

        // INSERT + UPDATE
        queryRecorder.clearBufferAndStartRecording();
        String oid = repositoryService.addObject(
                new UserType().name("user" + getTestNumber()).asPrismObject(),
                null, result);

        // These assertions are quite implementation dependent, obviously.
        Queue<SqlRecorder.QueryEntry> queryBuffer = queryRecorder.getQueryBuffer();
        assertThat(queryBuffer).hasSize(2);
        SqlRecorder.QueryEntry entry = queryBuffer.remove();
        assertThat(entry.sql).startsWith("insert into m_user");

        entry = queryBuffer.remove();
        assertThat(entry.sql).startsWith("update m_user");
        assertThat(entry.params.get(2)).isEqualTo(oid); // param for where oid = ...

        // COUNT
        queryRecorder.clearBufferAndStartRecording();
        int count = repositoryService.countObjects(UserType.class, null, null, result);
        assertThat(count).isGreaterThanOrEqualTo(1); // at least user from above should be there

        queryBuffer = queryRecorder.getQueryBuffer();
        assertThat(queryBuffer).hasSize(1);
        entry = queryBuffer.remove();
        assertThat(entry.sql).startsWith("select count(*)");

        // normal select
        queryRecorder.clearBufferAndStartRecording();
        SearchResultList<PrismObject<UserType>> users =
                repositoryService.searchObjects(UserType.class, null, null, result);
        assertThat(users).isNotEmpty();

        queryBuffer = queryRecorder.getQueryBuffer();
        assertThat(queryBuffer).hasSize(1);
        entry = queryBuffer.remove();
        assertThat(entry.sql).startsWith("select u.oid, u.fullObject");
    }

    @Test
    public void test500ExecuteQueryDiagnostics() throws Exception {
        // also known as "Query Playground"
        OperationResult result = createOperationResult();

        given("some objects are in the repository");
        String name = "user" + getTestNumber();
        repositoryService.addObject(
                new UserType().name(name)
                        .activation(new ActivationType().administrativeStatus(ActivationStatusType.ENABLED))
                        .asPrismObject(),
                null, result);
        queryRecorder.clearBufferAndStartRecording();

        when("executeQueryDiagnostics is called with query");
        RepositoryQueryDiagRequest request = new RepositoryQueryDiagRequest();
        request.setType(UserType.class);
        request.setQuery(prismContext.queryFor(UserType.class)
                .item(UserType.F_NAME).eqPoly(name)
                .and()
                // custom types, reproduces MID-7425
                .item(UserType.F_ACTIVATION, ActivationType.F_ADMINISTRATIVE_STATUS)
                .eq(ActivationStatusType.ENABLED)
                .build());
        RepositoryQueryDiagResponse response = repositoryService.executeQueryDiagnostics(request, result);

        then("query is executed and low-level info returned");
        assertThat(response).isNotNull();
        assertThat(response.getQueryResult()).hasSize(1)
                .extracting(o -> ((PrismObject<?>) o).asObjectable().getName().getOrig())
                .containsExactly(name);
        assertThat(response.getImplementationLevelQuery()).asString()
                .isEqualToIgnoringWhitespace("select u.oid, u.fullObject from m_user u"
                        + " where u.nameNorm = ? and u.nameOrig = ? and u.administrativeStatus = ?"
                        + " limit ?");

        assertThat(queryRecorder.getQueryBuffer()).hasSize(1);
    }

    @Test
    public void test501ExecuteQueryDiagnosticsTranslateOnly() {
        // also known as "Query Playground"
        OperationResult result = createOperationResult();

        given("diag request with translate only = true");
        queryRecorder.clearBufferAndStartRecording();
        RepositoryQueryDiagRequest request = new RepositoryQueryDiagRequest();
        request.setType(UserType.class);
        request.setTranslateOnly(true);
        request.setQuery(prismContext.queryFor(UserType.class)
                .item(UserType.F_NAME).eqPoly("whatever")
                .build());

        when("executeQueryDiagnostics is called with query");
        RepositoryQueryDiagResponse response = repositoryService.executeQueryDiagnostics(request, result);

        then("query is translated but not executed");
        assertThat(response).isNotNull();
        assertThat(response.getQueryResult()).isNullOrEmpty();
        assertThat(response.getImplementationLevelQuery()).asString()
                .isEqualToIgnoringWhitespace("select u.oid, u.fullObject from m_user u"
                        + " where u.nameNorm = ? and u.nameOrig = ?"
                        + " limit ?");

        assertThat(queryRecorder.getQueryBuffer()).isEmpty();
    }

    @Test
    public void test600AuditRecord() {
        given("audit event record");
        AuditEventRecord record = new AuditEventRecord(AuditEventType.ADD_OBJECT, AuditEventStage.EXECUTION);
        OperationResult result = createOperationResult();

        when("saving the event record");
        auditService.audit(record, NullTaskImpl.INSTANCE, result);

        then("operation is success and record ID is assigned");
        assertThatOperationResult(result).isSuccess();
        assertThat(record.getRepoId()).isNotNull();
    }

    @Test
    public void test601AuditRecordIgnoresProvidedId() {
        given("audit event record with repoId");
        AuditEventRecord record = new AuditEventRecord(AuditEventType.ADD_OBJECT, AuditEventStage.EXECUTION);
        record.setRepoId(-47L);
        OperationResult result = createOperationResult();

        when("saving the event record");
        auditService.audit(record, NullTaskImpl.INSTANCE, result);

        then("operation is success and record ID is assigned, disregarding the provided one");
        assertThatOperationResult(result).isSuccess();
        assertThat(record.getRepoId()).isNotNull()
                .isNotEqualTo(-47L);
    }

    @Test
    public void test610AuditForImportRespectsProvidedId() {
        given("audit event record with ID");
        clearAudit();
        // NOTE: AERType is used here, not AER for which the repoId is ignored by the service
        AuditEventRecordType record = new AuditEventRecordType()
                .eventType(AuditEventTypeType.ADD_OBJECT)
                .eventStage(AuditEventStageType.EXECUTION)
                .repoId(-47L)
                .timestamp(MiscUtil.asXMLGregorianCalendar(1L));
        OperationResult result = createOperationResult();

        when("saving the event record");
        auditService.audit(record, result);

        then("operation is success and the provided record ID is used");
        assertThatOperationResult(result).isSuccess();
        assertThat(record.getRepoId()).isEqualTo(-47L);
    }

    @Test
    public void test611AuditForImportRespectsProvidedIdEvenDuplicateForDifferentTimestamp() {
        given("audit event record with assigned already taken ID");
        clearAudit();
        AuditEventRecordType record = new AuditEventRecordType()
                .eventType(AuditEventTypeType.ADD_OBJECT)
                .eventStage(AuditEventStageType.EXECUTION)
                .repoId(-1L)
                .timestamp(MiscUtil.asXMLGregorianCalendar(1L));
        OperationResult result = createOperationResult();
        auditService.audit(record, result);
        assertThat(record.getRepoId()).isEqualTo(-1L);

        record = new AuditEventRecordType()
                .eventType(AuditEventTypeType.MODIFY_OBJECT)
                .eventStage(AuditEventStageType.EXECUTION)
                .repoId(-1L)
                .timestamp(MiscUtil.asXMLGregorianCalendar(2L)); // timestamp must be different

        when("saving the event record with taken ID");
        auditService.audit(record, result);

        then("operation is success and the provided record ID is reused");
        assertThatOperationResult(result).isSuccess();
        assertThat(record.getRepoId()).isEqualTo(-1L);
    }

    @Test
    public void test612AuditForImportWithNonUniqueIdAndTimestampFails() {
        given("audit event record with assigned already taken ID");
        clearAudit();
        AuditEventRecordType record = new AuditEventRecordType()
                .eventType(AuditEventTypeType.ADD_OBJECT)
                .eventStage(AuditEventStageType.EXECUTION)
                .repoId(-1L)
                .timestamp(MiscUtil.asXMLGregorianCalendar(1L));
        OperationResult result = createOperationResult();
        auditService.audit(record, result);
        assertThat(record.getRepoId()).isEqualTo(-1L);

        AuditEventRecordType record2 = new AuditEventRecordType()
                .eventType(AuditEventTypeType.MODIFY_OBJECT)
                .eventStage(AuditEventStageType.EXECUTION)
                .repoId(-1L)
                .timestamp(MiscUtil.asXMLGregorianCalendar(1L));

        expect("saving the event record throws");
        assertThatThrownBy(() -> auditService.audit(record2, result))
                .isInstanceOf(SystemException.class)
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);

        assertThatOperationResult(result).isFatalError();
    }

    // region low-level tests

    /** This tests our type mapper/converter classes and related column mapping. */
    @Test
    public void test900WorkingWithPgArraysJsonbAndBytea() {
        QUser u = aliasFor(QUser.class);
        MUser user = new MUser();

        String userName = "user" + getTestNumber();
        setName(user, userName);
        user.policySituations = new Integer[] { 1, 2 };
        user.subtypes = new String[] { "subtype1", "subtype2" };
        user.ext = new Jsonb("{\"key\" : \"value\",\n\"number\": 47} "); // more whitespaces/lines
        user.photo = new byte[] { 0, 1, 0, 1 };
        try (JdbcSession jdbcSession = startTransaction()) {
            jdbcSession.newInsert(u).populate(user).execute();
            jdbcSession.commit();
        }

        MUser row = selectOne(u, u.nameNorm.eq(userName));
        assertThat(row.policySituations).contains(1, 2);
        assertThat(row.subtypes).contains("subtype1", "subtype2");
        assertThat(row.ext.value).isEqualTo("{\"key\": \"value\", \"number\": 47}"); // normalized
        // byte[] is used for fullObject, there is no chance to miss a problem with it
        assertThat(row.photo).hasSize(4);

        // setting NULLs
        try (JdbcSession jdbcSession = startTransaction()) {
            jdbcSession.newUpdate(u)
                    .setNull(u.policySituations)
                    .set(u.subtypes, (String[]) null) // this should do the same
                    .setNull(u.ext)
                    .setNull(u.photo)
                    .where(u.oid.eq(row.oid))
                    .execute();
            jdbcSession.commit();
        }

        row = selectOne(u, u.nameNorm.eq(userName));
        assertThat(row.policySituations).isNull();
        assertThat(row.subtypes).isNull();
        assertThat(row.ext).isNull();
        // but we never set fullObject to null, so this is a good test for doing so with byte[]
        assertThat(row.photo).isNull();
    }

    @Test
    public void test999ConnectionIsValidCheck() {
        try (JdbcSession jdbcSession = sqlRepoContext.newJdbcSession()) {
            // Just to see that it works, this is used for keepalive (if set for HikariCP).
            jdbcSession.connection().isValid(10);
        } catch (SQLException e) {
            Assert.fail("Failing isValid check on JDBC connection", e);
        }
    }
    // endregion
}
