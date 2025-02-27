/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.intest.sync;

import static com.evolveum.midpoint.model.api.ModelPublicConstants.RECONCILIATION_REMAINING_SHADOWS_PATH;
import static com.evolveum.midpoint.model.api.ModelPublicConstants.RECONCILIATION_RESOURCE_OBJECTS_PATH;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationExclusionReasonType.PROTECTED;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationExclusionReasonType.SYNCHRONIZATION_NOT_NEEDED;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationSituationType.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.model.impl.sync.tasks.recon.ReconciliationActivityHandler;

import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.schema.processor.ResourceObjectClassDefinition;
import com.evolveum.midpoint.schema.processor.ResourceSchemaFactory;

import org.apache.commons.lang.mutable.MutableInt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.icf.dummy.resource.BreakMode;
import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.icf.dummy.resource.DummyResource;
import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditEventStage;
import com.evolveum.midpoint.audit.api.AuditEventType;
import com.evolveum.midpoint.model.common.stringpolicy.ValuePolicyProcessor;
import com.evolveum.midpoint.model.impl.sync.tasks.recon.DebugReconciliationResultListener;
import com.evolveum.midpoint.model.intest.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.internals.InternalCounters;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.internals.InternalOperationClasses;
import com.evolveum.midpoint.schema.processor.ObjectFactory;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.test.ProvisioningScriptSpec;
import com.evolveum.midpoint.test.TestResource;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

/**
 * @author semancik
 */
@SuppressWarnings("SpellCheckingInspection")
@ContextConfiguration(locations = { "classpath:ctx-model-intest-test-main.xml" })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestImportRecon extends AbstractInitializedModelIntegrationTest {

    private static final File TEST_DIR = new File("src/test/resources/sync");

    private static final TestResource<UserType> USER_IMPORTER = new TestResource<>(
            TEST_DIR, "user-importer.xml", "00000000-1111-1111-1111-000000000002");

    private static final String ACCOUNT_OTIS_NAME = "otis";
    private static final String ACCOUNT_OTIS_FULLNAME = "Otis";

    private static final File ACCOUNT_STAN_FILE = new File(TEST_DIR, "account-stan-dummy.xml");
    private static final String ACCOUNT_STAN_OID = "22220000-2200-0000-0000-444400004455";
    private static final String ACCOUNT_STAN_NAME = "stan";
    private static final String ACCOUNT_STAN_FULLNAME = "Stan the Salesman";

    private static final String ACCOUNT_RUM_NAME = "rum";

    private static final String ACCOUNT_MURRAY_NAME = "murray";

    private static final String ACCOUNT_CAPSIZE_NAME = "capsize";
    private static final String ACCOUNT_CAPSIZE_FULLNAME = "Kata Capsize";

    private static final String USER_AUGUSTUS_NAME = "augustus";

    private static class AccountTestResource extends TestResource<ShadowType> {
        private final String name;
        @SuppressWarnings({ "FieldCanBeLocal", "unused" })
        private final String fullName;

        private AccountTestResource(File dir, String fileName, String oid, String name, String fullName) {
            super(dir, fileName, oid);
            this.name = name;
            this.fullName = fullName;
        }
    }

    private static final AccountTestResource ACCOUNT_AUGUSTUS = new AccountTestResource(TEST_DIR, "account-augustus-dummy.xml", "22220000-2200-0000-0000-444400004457", "augustus", "Augustus DeWaat");
    private static final AccountTestResource ACCOUNT_TAUGUSTUS = new AccountTestResource(TEST_DIR, "account-taugustus-dummy.xml", "22220000-2200-0000-0000-444400004456", "Taugustus", "Augustus DeWaat");
    private static final AccountTestResource ACCOUNT_KENNY = new AccountTestResource(TEST_DIR, "account-kenny-dummy.xml", "22220000-2200-0000-0000-444400004461", "kenny", "Kenny Falmouth");

    private static final String USER_PALIDO_NAME = "palido";

    private static final AccountTestResource ACCOUNT_TPALIDO = new AccountTestResource(TEST_DIR, "account-tpalido-dummy.xml", "22220000-2200-0000-0000-444400004462", "Tpalido", "Palido Domingo");
    private static final AccountTestResource ACCOUNT_LECHIMP = new AccountTestResource(TEST_DIR, "account-lechimp-dummy.xml", "22220000-2200-0000-0000-444400004463", "lechimp", "Captain LeChimp");
    private static final AccountTestResource ACCOUNT_TLECHIMP = new AccountTestResource(TEST_DIR, "account-tlechimp-dummy.xml", "22220000-2200-0000-0000-444400004464", "Tlechimp", "Captain LeChimp");
    private static final AccountTestResource ACCOUNT_ANDRE = new AccountTestResource(TEST_DIR, "account-andre-dummy.xml", "22220000-2200-0000-0000-444400004465", "andre", "King Andre");
    private static final AccountTestResource ACCOUNT_TANDRE = new AccountTestResource(TEST_DIR, "account-tandre-dummy.xml", "22220000-2200-0000-0000-444400004466", "Tandre", "King Andre");

    private static final String USER_LAFOOT_NAME = "lafoot";
    private static final AccountTestResource ACCOUNT_TLAFOOT = new AccountTestResource(TEST_DIR, "account-tlafoot-dummy.xml", "22220000-2200-0000-0000-444400004467", "Tlafoot", "Effete LaFoot");
    private static final AccountTestResource ACCOUNT_CRUFF = new AccountTestResource(TEST_DIR, "account-cruff-dummy.xml", "22220000-2200-0000-0000-444400004468", "cruff", "Cruff");

    private static final String ACCOUNT_HTM_NAME = "htm";
    private static final String ACCOUNT_HTM_FULL_NAME = "Horatio Torquemada Marley";

    // AZURE resource. It disables unmatched accounts.
    // It also has several objectType definitions that are designed to confuse
    // the code that determines refined schema definitions
    private static final File RESOURCE_DUMMY_AZURE_FILE = new File(TEST_DIR, "resource-dummy-azure.xml");
    private static final String RESOURCE_DUMMY_AZURE_OID = "10000000-0000-0000-0000-00000000a204";
    private static final String RESOURCE_DUMMY_AZURE_NAME = "azure";

    private static final QName DUMMY_ACCOUNT_OBJECT_CLASS = new QName(RESOURCE_DUMMY_NAMESPACE, "AccountObjectClass");

    // LIME dummy resource. This is a pure authoritative resource. It has only inbound mappings.
    private static final File RESOURCE_DUMMY_LIME_FILE = new File(TEST_DIR, "resource-dummy-lime.xml");
    private static final String RESOURCE_DUMMY_LIME_OID = "10000000-0000-0000-0000-000000131404";
    private static final String RESOURCE_DUMMY_LIME_NAME = "lime";
    private static final String RESOURCE_DUMMY_LIME_NAMESPACE = MidPointConstants.NS_RI;

    private static final QName DUMMY_LIME_ACCOUNT_OBJECT_CLASS = new QName(RESOURCE_DUMMY_LIME_NAMESPACE, "AccountObjectClass");

    private static final TestResource<ObjectTemplateType> USER_TEMPLATE_LIME = new TestResource<>(
            TEST_DIR, "user-template-lime.xml", "3cf43520-241d-11e6-afa5-a377b674950d");
    private static final TestResource<RoleType> ROLE_IMPORTER = new TestResource<>(
            TEST_DIR, "role-importer.xml", "00000000-1111-1111-1111-000000000004");
    private static final TestResource<RoleType> ROLE_CORPSE = new TestResource<>(
            TEST_DIR, "role-corpse.xml", "1c64c778-e7ac-11e5-b91a-9f44177e2359");

    private static final TestResource<ValuePolicyType> PASSWORD_POLICY_LOWER_CASE_ALPHA_AZURE = new TestResource<>(
            TEST_DIR, "password-policy-azure.xml", "81818181-76e0-59e2-8888-3d4f02d3fffd");
    private static final TestResource<TaskType> TASK_RECONCILE_DUMMY_SINGLE = new TestResource<>(
            TEST_DIR, "task-reconcile-dummy-single.xml", "10000000-0000-0000-5656-565600000004");
    private static final TestResource<TaskType> TASK_RECONCILE_DUMMY_FILTER = new TestResource<>(
            TEST_DIR, "task-reconcile-dummy-filter.xml", "10000000-0000-0000-5656-565600000014");
    private static final TestResource<TaskType> TASK_RECONCILE_DUMMY_AZURE = new TestResource<>(
            TEST_DIR, "task-reconcile-dummy-azure.xml", "10000000-0000-0000-5656-56560000a204");
    private static final TestResource<TaskType> TASK_RECONCILE_DUMMY_LIME = new TestResource<>(
            TEST_DIR, "task-reconcile-dummy-lime.xml", "10000000-0000-0000-5656-565600131204");
    private static final TestResource<TaskType> TASK_IMPORT_DUMMY_LIME_LIMITED_LEGACY = new TestResource<>(
            TEST_DIR, "task-import-dummy-lime-limited-legacy.xml", "4e2f83b8-5312-4924-af7e-52805ad20b3e");
    private static final TestResource<TaskType> TASK_IMPORT_DUMMY_LIME_LIMITED = new TestResource<>(
            TEST_DIR, "task-import-dummy-lime-limited.xml", "db3b4438-67a8-4614-a320-382b4cbace41");
    private static final TestResource<TaskType> TASK_DELETE_DUMMY_SHADOWS = new TestResource<>(
            TEST_DIR, "task-delete-dummy-shadows.xml", "abaab842-18be-11e5-9416-001e8c717e5b");
    private static final TestResource<TaskType> TASK_DELETE_DUMMY_ACCOUNTS = new TestResource<>(
            TEST_DIR, "task-delete-dummy-accounts.xml", "ab28a334-2aca-11e5-afe7-001e8c717e5b");

    private static final String GROUP_CORPSES_NAME = "corpses";

    private static final String ACCOUNT_CAPSIZE_PASSWORD = "is0mud01d";

    private static final int NUMBER_OF_IMPORTED_USERS = 2;

    @Autowired
    private ValuePolicyProcessor valuePolicyProcessor;

    private DummyResource dummyResourceAzure;
    private DummyResourceContoller dummyResourceCtlAzure;
    private ResourceType resourceDummyAzureType;
    private PrismObject<ResourceType> resourceDummyAzure;

    private DummyResource dummyResourceLime;
    private DummyResourceContoller dummyResourceCtlLime;
    private PrismObject<ResourceType> resourceDummyLime;

    @Autowired private ReconciliationActivityHandler reconciliationActivityHandler;

    private DebugReconciliationResultListener reconciliationResultListener;

    PrismObject<UserType> userImporter;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);

        reconciliationResultListener = new DebugReconciliationResultListener();
        reconciliationActivityHandler.setReconciliationResultListener(reconciliationResultListener);

        // Object templates (must be imported before resource, otherwise there are validation warnigns)
        repoAddObjectFromFile(USER_TEMPLATE_LIME.file, initResult);

        dummyResourceCtlAzure = DummyResourceContoller.create(RESOURCE_DUMMY_AZURE_NAME, resourceDummyAzure);
        dummyResourceCtlAzure.extendSchemaPirate();
        dummyResourceCtlAzure.addOrgTop();
        dummyResourceAzure = dummyResourceCtlAzure.getDummyResource();
        resourceDummyAzure = importAndGetObjectFromFile(ResourceType.class, getDummyResourceAzureFile(), RESOURCE_DUMMY_AZURE_OID, initTask, initResult);
        resourceDummyAzureType = resourceDummyAzure.asObjectable();
        dummyResourceCtlAzure.setResource(resourceDummyAzure);

        dummyResourceCtlLime = DummyResourceContoller.create(RESOURCE_DUMMY_LIME_NAME, resourceDummyLime);
        dummyResourceCtlLime.extendSchemaPirate();
        dummyResourceLime = dummyResourceCtlLime.getDummyResource();
        resourceDummyLime = importAndGetObjectFromFile(ResourceType.class, getDummyResourceLimeFile(), RESOURCE_DUMMY_LIME_OID, initTask, initResult);
        dummyResourceCtlLime.setResource(resourceDummyLime);

        // Create an account that midPoint does not know about yet
        getDummyResourceController().addAccount(USER_RAPP_USERNAME, USER_RAPP_FULLNAME, "Scabb Island");
        getDummyResource().getAccountByUsername(USER_RAPP_USERNAME)
                .replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "The Elaine");

        dummyResourceCtlLime.addAccount(USER_RAPP_USERNAME, USER_RAPP_FULLNAME, "Scabb Island");
        dummyResourceLime.getAccountByUsername(USER_RAPP_USERNAME)
                .replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "The Elaine");
        dummyResourceCtlLime.addAccount(ACCOUNT_RUM_NAME, "Rum Rogers");
        dummyResourceCtlLime.addAccount(ACCOUNT_MURRAY_NAME, "Murray");

        // Groups
        dummyResourceCtlAzure.addGroup(GROUP_CORPSES_NAME);

        // Roles
        repoAddObjectFromFile(ROLE_CORPSE.file, initResult);
        repoAddObjectFromFile(ROLE_IMPORTER.file, initResult);

        // Password policy
        repoAddObjectFromFile(PASSWORD_POLICY_GLOBAL_FILE, initResult);
        repoAddObjectFromFile(PASSWORD_POLICY_LOWER_CASE_ALPHA_AZURE.file, initResult);

        applyPasswordPolicy(PASSWORD_POLICY_GLOBAL_OID, SECURITY_POLICY_OID, initTask, initResult);

        // Users
        userImporter = repoAddObjectFromFile(USER_IMPORTER.file, initResult);
        // And a user that will be correlated to that account
        repoAddObjectFromFile(USER_RAPP_FILE, initResult);

        PrismObject<ShadowType> accountStan = PrismTestUtil.parseObject(ACCOUNT_STAN_FILE);
        provisioningService.addObject(accountStan, null, null, initTask, initResult);

        addObject(SHADOW_GROUP_DUMMY_TESTERS_FILE, initTask, initResult);

        InternalMonitor.reset();
        InternalMonitor.setTrace(InternalOperationClasses.SHADOW_FETCH_OPERATIONS, true);

//        DebugUtil.setDetailedDebugDump(true);
    }

    @Override
    protected int getNumberOfUsers() {
        return super.getNumberOfUsers() + NUMBER_OF_IMPORTED_USERS;
    }

    private File getDummyResourceLimeFile() {
        return RESOURCE_DUMMY_LIME_FILE;
    }

    private File getDummyResourceAzureFile() {
        return RESOURCE_DUMMY_AZURE_FILE;
    }

    protected PrismObject<UserType> getDefaultActor() {
        return userAdministrator;
    }

    protected void loginImportUser() throws CommonException {
        loginAdministrator();
    }

    @Test
    public void test001SanityAzure() throws Exception {
        displayDumpable("Dummy resource azure", dummyResourceAzure);

        // WHEN
        ResourceSchema resourceSchemaAzure = ResourceSchemaFactory.getRawSchema(resourceDummyAzureType);

        displayDumpable("Dummy azure resource schema", resourceSchemaAzure);

        // THEN
        dummyResourceCtlAzure.assertDummyResourceSchemaSanityExtended(resourceSchemaAzure);

        ResourceObjectClassDefinition orgOcDef =
                resourceSchemaAzure.findObjectClassDefinition(dummyResourceCtlAzure.getOrgObjectClassQName());
        assertNotNull("No org object class def in azure resource schema", orgOcDef);
    }

    @Test
    public void test002SanityAzureRefined() throws Exception {
        // WHEN
        ResourceSchema refinedSchemaAzure = ResourceSchemaFactory.getCompleteSchema(resourceDummyAzureType);

        displayDumpable("Dummy azure refined schema", refinedSchemaAzure);

        // THEN
        dummyResourceCtlAzure.assertRefinedSchemaSanity(refinedSchemaAzure);

        ResourceObjectClassDefinition orgOcDef =
                refinedSchemaAzure.findObjectClassDefinition(dummyResourceCtlAzure.getOrgObjectClassQName());
        assertNotNull("No org object class def in azure refined schema", orgOcDef);
    }

    /**
     * Single-user import.
     */
    @Test
    public void test100ImportStanFromResourceDummy() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Preconditions
        assertUsers(getNumberOfUsers());
        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        loginImportUser();

        // WHEN
        when();
        modelService.importFromResource(ACCOUNT_STAN_OID, task, result);

        // THEN
        then();
        display(result);
        assertSuccess(result);

        loginAdministrator();

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        PrismObject<UserType> userStan = assertImportedUserByUsername(ACCOUNT_STAN_NAME, RESOURCE_DUMMY_OID);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        assertUsers(getNumberOfUsers() + 1);

        assertPasswordCompliesWithPolicy(userStan, PASSWORD_POLICY_GLOBAL_OID); // MID-4028

        // Check audit
        assertImportAuditModifications(1);
    }

    /**
     * Background import.
     */
    @Test
    public void test150ImportFromResourceDummy() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Preconditions
        List<PrismObject<UserType>> usersBefore = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users before import", usersBefore);
        assertEquals("Unexpected number of users", getNumberOfUsers() + 1, usersBefore.size());

        PrismObject<UserType> rapp = getUser(USER_RAPP_OID);
        assertNotNull("No rapp", rapp);
        // Rapp has dummy account but it is not linked
        assertLiveLinks(rapp, 0);

        loginImportUser();

        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        modelService.importFromResource(RESOURCE_DUMMY_OID, DUMMY_ACCOUNT_OBJECT_CLASS, task, result);

        // THEN
        then();
        OperationResult subresult = result.getLastSubresult();
        TestUtil.assertInProgress("importAccountsFromResource result", subresult);

        loginAdministrator();

        waitForTaskFinish(task, false, 40000);

        // THEN
        then();
        TestUtil.assertSuccess(task.getResult());

        dumpStatistics(task);
        assertTask(task, "task after")
                .display()
                .rootSynchronizationInformation()
                    .display()
                    .assertTransition(LINKED, LINKED, LINKED, null, 1, 0, 0) // stan
                    .assertTransition(null, LINKED, LINKED, null, 2, 0, 0) // guybrush, elaine
                    .assertTransition(null, UNLINKED, LINKED, null, 1, 0, 0) // rapp
                    .assertTransition(null, UNMATCHED, LINKED, null, 1, 0, 0) // ht
                    .assertTransition(null, null, null, PROTECTED, 0, 0, 2) // daviejones, calypso
                    .assertTransitions(5)
                    .end()
                .rootItemProcessingInformation()
                    .display()
                    .assertTotalCounts(7, 0)
                    .end()
                .assertProgress(7);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 3);

        List<PrismObject<UserType>> usersAfter = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after import", usersAfter);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_STAN_NAME, RESOURCE_DUMMY_OID);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        PrismObject<UserType> userRappAfter = getUser(USER_RAPP_OID);
        display("User rapp after", userRappAfter);
        PrismAsserts.assertPropertyValue(userRappAfter, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        assertEquals("Unexpected number of users", getNumberOfUsers() + 2, usersAfter.size());

        // Check audit
        assertImportAuditModifications(4);
    }

    /**
     * Background import.
     */
    @Test
    public void test155ImportFromResourceDummyAgain() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        loginImportUser();

        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        modelService.importFromResource(RESOURCE_DUMMY_OID, DUMMY_ACCOUNT_OBJECT_CLASS, task, result);

        // THEN
        then();
        OperationResult subresult = result.getLastSubresult();
        TestUtil.assertInProgress("importAccountsFromResource result", subresult);

        loginAdministrator();

        waitForTaskFinish(task, false, 40000);

        // THEN
        then();
        assertSuccess(task.getResult());

        dumpStatistics(task);
        assertTask(task, "task after")
                .display()
                .rootSynchronizationInformation()
                    .display()
                    .assertTransition(LINKED, LINKED, LINKED, null, 5, 0, 0) // stan, guybrush, elaine, rapp, ht
                    .assertTransition(null, null, null, PROTECTED, 0, 0, 2) // daviejones, calypso
                    .assertTransitions(2)
                    .end()
                .rootItemProcessingInformation()
                    .display()
                    .assertTotalCounts(7, 0)
                    .end()
                .assertProgress(7);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 3);

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after import", users);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_STAN_NAME, RESOURCE_DUMMY_OID);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        PrismObject<UserType> userRappAfter = getUser(USER_RAPP_OID);
        display("User rapp after", userRappAfter);
        PrismAsserts.assertPropertyValue(userRappAfter, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        assertEquals("Unexpected number of users", getNumberOfUsers() + 2, users.size());

        // Check audit
        assertImportAuditModifications(0);
    }

    /**
     * Background import.
     */
    @Test
    public void test160ImportFromResourceDummyLime() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Preconditions
        assertUsers(getNumberOfUsers() + 2);
        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        displayDumpable("Rapp lime account before", dummyResourceLime.getAccountByUsername(USER_RAPP_USERNAME));

        PrismObject<UserType> userRappBefore = getUser(USER_RAPP_OID);
        display("User rapp before", userRappBefore);
        PrismAsserts.assertPropertyValue(userRappBefore, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        loginImportUser();

        // WHEN
        when();
        modelService.importFromResource(RESOURCE_DUMMY_LIME_OID, DUMMY_LIME_ACCOUNT_OBJECT_CLASS, task, result);

        // THEN
        then();
        OperationResult subresult = result.getLastSubresult();
        TestUtil.assertInProgress("importAccountsFromResource result", subresult);

        loginAdministrator();

        waitForTaskFinish(task, false, 40000);

        // THEN
        then();
        TestUtil.assertSuccess(task.getResult());

        dumpStatistics(task);
        assertTask(task, "task after")
                .display()
                .rootSynchronizationInformation()
                    .display()
                    .assertTransition(null, UNLINKED, LINKED, null, 1, 0, 0) // rapp
                    .assertTransition(null, UNMATCHED, LINKED, null, 2, 0, 0) // rum, murray
                    .assertTransitions(2)
                    .end()
                .rootItemProcessingInformation()
                    .display()
                    .assertTotalCounts(3, 0)
                    .end()
                .assertProgress(3);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 2);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_STAN_NAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_RUM_NAME, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_MURRAY_NAME, RESOURCE_DUMMY_LIME_OID);

        PrismObject<UserType> userRappAfter = getUser(USER_RAPP_OID);
        display("User rapp after", userRappAfter);
        PrismAsserts.assertPropertyValue(userRappAfter, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        displayDumpable("Rapp lime account after", dummyResourceLime.getAccountByUsername(USER_RAPP_USERNAME));

        assertUsers(getNumberOfUsers() + 4);

        // Check audit
        assertImportAuditModifications(3);
    }

    /**
     * Import limited to single object: MID-6798. (Legacy specification.)
     */
    @Test
    public void test161aImportFromResourceDummyLimeLimitedLegacy() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Preconditions
        assertUsers(getNumberOfUsers() + 4);
        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        loginImportUser();

        when();

        addObject(TASK_IMPORT_DUMMY_LIME_LIMITED_LEGACY.file, task, result);
        loginAdministrator();
        waitForTaskFinish(TASK_IMPORT_DUMMY_LIME_LIMITED_LEGACY.oid, false);

        then();

        Task importTask = taskManager.getTaskPlain(TASK_IMPORT_DUMMY_LIME_LIMITED_LEGACY.oid, result);

        dumpStatistics(importTask);
        assertTask(importTask, "task after")
                .display()
                .rootActivityState()
                    .itemProcessingStatistics()
                        .display()
                        .assertTotalCounts(1, 0)
                    .end()
                    .progress()
                        .display()
                        .assertSuccessCount(1, false)
                    .end()
                .end()
                .assertProgress(1);
    }

    /**
     * Import limited to single object: MID-6798. (Activity-based specification.)
     */
    @Test
    public void test161bImportFromResourceDummyLimeLimited() throws Exception {
        given();
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Preconditions
        assertUsers(getNumberOfUsers() + 4);
        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        loginImportUser();

        when();

        addObject(TASK_IMPORT_DUMMY_LIME_LIMITED.file, task, result);
        loginAdministrator();
        waitForTaskFinish(TASK_IMPORT_DUMMY_LIME_LIMITED.oid, false);

        then();

        Task importTask = taskManager.getTaskPlain(TASK_IMPORT_DUMMY_LIME_LIMITED.oid, result);

        dumpStatistics(importTask);
        assertTask(importTask, "task after")
                .display()
                .rootActivityState()
                    .itemProcessingStatistics()
                        .display()
                        .assertTotalCounts(1, 0)
                    .end()
                    .progress()
                        .display()
                        .assertSuccessCount(1, false)
                    .end()
                .end()
                .assertProgress(1);
    }

    /**
     * MID-2427
     */
    @Test
    public void test162ImportFromResourceDummyLimeRappOrganizationScummBar() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        DummyAccount accountRappLimeBefore = dummyResourceLime.getAccountByUsername(USER_RAPP_USERNAME);
        accountRappLimeBefore.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME,
                ORG_SCUMM_BAR_NAME);
        displayDumpable("Rapp lime account before", accountRappLimeBefore);

        // Preconditions

        PrismObject<UserType> userRappBefore = getUser(USER_RAPP_OID);
        display("User rapp before", userRappBefore);
        PrismAsserts.assertPropertyValue(userRappBefore, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));
        assertNoAssignments(userRappBefore);

        assertUsers(getNumberOfUsers() + 4);

        loginImportUser();

        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        modelService.importFromResource(RESOURCE_DUMMY_LIME_OID, DUMMY_LIME_ACCOUNT_OBJECT_CLASS, task, result);

        // THEN
        then();
        OperationResult subresult = result.getLastSubresult();
        TestUtil.assertInProgress("importAccountsFromResource result", subresult);

        loginAdministrator();

        waitForTaskFinish(task, false, 40000);

        // THEN
        then();
        TestUtil.assertSuccess(task.getResult());

        dumpStatistics(task);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 2);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_STAN_NAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_RUM_NAME, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_MURRAY_NAME, RESOURCE_DUMMY_LIME_OID);

        PrismObject<UserType> userRappAfter = getUser(USER_RAPP_OID);
        display("User rapp after", userRappAfter);
        PrismAsserts.assertPropertyValue(userRappAfter, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));
        PrismAsserts.assertPropertyValue(userRappAfter, UserType.F_ORGANIZATION,
                createPolyString(ORG_SCUMM_BAR_NAME));

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        DummyAccount accountRappLimeAfter = dummyResourceLime.getAccountByUsername(USER_RAPP_USERNAME);
        displayDumpable("Rapp lime account after", accountRappLimeAfter);
        assertAssignedOrg(userRappAfter, ORG_SCUMM_BAR_OID);
        assertAssignments(userRappAfter, 1);

        assertUsers(getNumberOfUsers() + 4);

        // Check audit
        assertImportAuditModifications(1);
    }

    /**
     * MID-2427
     */
    @Test
    public void test164ImportFromResourceDummyLimeRappOrganizationNull() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        DummyAccount accountRappLimeBefore = dummyResourceLime.getAccountByUsername(USER_RAPP_USERNAME);
        accountRappLimeBefore.replaceAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME /* no value */);
        displayDumpable("Rapp lime account before", accountRappLimeBefore);

        // Preconditions

        PrismObject<UserType> userRappBefore = getUser(USER_RAPP_OID);
        display("User rapp before", userRappBefore);
        PrismAsserts.assertPropertyValue(userRappBefore, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));
        PrismAsserts.assertPropertyValue(userRappBefore, UserType.F_ORGANIZATION,
                createPolyString(ORG_SCUMM_BAR_NAME));
        assertAssignedOrg(userRappBefore, ORG_SCUMM_BAR_OID);
        assertAssignments(userRappBefore, 1);

        assertUsers(getNumberOfUsers() + 4);

        loginImportUser();

        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        modelService.importFromResource(RESOURCE_DUMMY_LIME_OID, DUMMY_LIME_ACCOUNT_OBJECT_CLASS, task, result);

        // THEN
        then();
        OperationResult subresult = result.getLastSubresult();
        TestUtil.assertInProgress("importAccountsFromResource result", subresult);

        loginAdministrator();

        waitForTaskFinish(task, false, 40000);

        // THEN
        then();
        assertSuccess(task.getResult());

        dumpStatistics(task);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 2);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_STAN_NAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_RUM_NAME, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_MURRAY_NAME, RESOURCE_DUMMY_LIME_OID);

        PrismObject<UserType> userRappAfter = getUser(USER_RAPP_OID);
        display("User rapp after", userRappAfter);
        PrismAsserts.assertPropertyValue(userRappAfter, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));
        PrismAsserts.assertNoItem(userRappAfter, UserType.F_ORGANIZATION);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        DummyAccount accountRappLimeAfter = dummyResourceLime.getAccountByUsername(USER_RAPP_USERNAME);
        displayDumpable("Rapp lime account after", accountRappLimeAfter);
        assertNoAssignments(userRappAfter);

        assertUsers(getNumberOfUsers() + 4);

        // Check audit
        assertImportAuditModifications(1);
    }

    @Test
    public void test200ReconcileDummy() throws Exception {
        // GIVEN
        loginAdministrator();

        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        given("Lets do some local changes on dummy resource");
        DummyAccount guybrushDummyAccount = getDummyResource().getAccountByUsername(ACCOUNT_GUYBRUSH_DUMMY_USERNAME);

        // fullname has a normal outbound mapping, this change should NOT be corrected
        guybrushDummyAccount.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Dubrish Freepweed");

        // location has strong outbound mapping, this change should be corrected
        guybrushDummyAccount.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME, "The Forbidded Dodecahedron");

        // Weapon has a weak mapping, this change should be left as it is
        guybrushDummyAccount.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME, "Feather duster");

        // Drink is not tolerant. The extra values should be removed
        guybrushDummyAccount.addAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, "water");

        // Quote is tolerant. The extra values should stay as they are
        guybrushDummyAccount.addAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME, "I want to be a pirate!");

        // Calypso is protected, this should not reconcile
        DummyAccount calypsoDummyAccount = getDummyResource().getAccountByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        calypsoDummyAccount.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Calypso");

        PrismObject<UserType> userRappBefore = getUser(USER_RAPP_OID);
        display("User rapp before", userRappBefore);
        PrismAsserts.assertPropertyValue(userRappBefore, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        getDummyResource().purgeScriptHistory();
        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);
        reconciliationResultListener.clear();

        when("reconciliation task is run");
        importObjectFromFile(TASK_RECONCILE_DUMMY_SINGLE.file);

        then("task should be OK");

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_OID, false);
        dumpStatistics(taskAfter);

        // @formatter:off
        assertTask(taskAfter, "task after")
                .display()
                .activityState(RECONCILIATION_RESOURCE_OBJECTS_PATH)
                    .synchronizationStatistics()
                        .display()
                        .assertTransition(LINKED, LINKED, LINKED, null, 5, 0, 0)
                        .assertTransition(null, null, null, PROTECTED, 0, 0, 2)
                        .assertTransitions(2)
                    .end()
                    .itemProcessingStatistics()
                        .display()
                        .assertTotalCounts(5, 0, 2)
                    .end()
                    .progress()
                        .display()
                        .assertCommitted(5, 0, 2)
                        .assertNoUncommitted()
                    .end()
                .end()
                .activityState(RECONCILIATION_REMAINING_SHADOWS_PATH)
                    .itemProcessingStatistics()
                        .display()
                        .assertTotalCounts(0, 0, 2)
                    .end()
                    .progress()
                        .display()
                        .assertCommitted(0, 0, 2)
                        .assertNoUncommitted()
                    .end()
                .end()
                .assertProgress(9);
        // @formatter:on

        and("given number of fetch operations should be there");
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 6);

        and("reconciliation result listener should contain correct counters");
        reconciliationResultListener.assertResult(RESOURCE_DUMMY_OID, 0, 7, 0, 2);

        and("users should be as expected");
        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after import", users);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);

        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);

        and("attributes on the resource should be as expected");
        // Guybrushes fullname should NOT be corrected back to real fullname
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Dubrish Freepweed");
        // Guybrushes location should be corrected back to real value
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME,
                "Melee Island");
        // Guybrushes weapon should be left untouched
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME,
                "Feather duster");
        // Guybrushes drink should be corrected
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME,
                "rum");
        // Guybrushes quotes should be left untouched
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME,
                "Arr!", "I want to be a pirate!");

        and("user rapp should be as expected");
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        PrismObject<UserType> userRappAfter = getUser(USER_RAPP_OID);
        display("User rapp after", userRappAfter);
        PrismAsserts.assertPropertyValue(userRappAfter, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        and("herman should be there");
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);

        and("users for protected accounts should NOT be imported nor touched");
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        // Calypso is protected account. Reconciliation should not touch it
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        and("here we display misc things");
        assertEquals("Unexpected number of users", getNumberOfUsers() + 4, users.size());

        displayValue("Dummy resource", getDummyResource().debugDump());

        and("appropriate scripts should be executed");
        display("Script history", getDummyResource().getScriptHistory());

        ArrayList<ProvisioningScriptSpec> scripts = new ArrayList<>();
        addReconScripts(scripts, ACCOUNT_HERMAN_DUMMY_USERNAME, "Herman Toothrot", false);
        addReconScripts(scripts, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, "Guybrush Threepwood", true);
        addReconScripts(scripts, ACCOUNT_ELAINE_DUMMY_USERNAME, "Elaine Marley", false);
        addReconScripts(scripts, USER_RAPP_USERNAME, USER_RAPP_FULLNAME, false);
        addReconScripts(scripts, ACCOUNT_STAN_NAME, ACCOUNT_STAN_FULLNAME, false);
        IntegrationTestTools.assertScripts(getDummyResource().getScriptHistory(), scripts.toArray(new ProvisioningScriptSpec[0]));

        assertReconAuditModifications(1, TASK_RECONCILE_DUMMY_OID);

        // Task result
        PrismObject<TaskType> reconTaskAfter = getTask(TASK_RECONCILE_DUMMY_OID);
        OperationResultType reconTaskResult = reconTaskAfter.asObjectable().getResult();
        display("Recon task result", reconTaskResult);
        TestUtil.assertSuccess(reconTaskResult);
    }

    @Test
    public void test210ReconcileDummyBroken() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Lets do some local changes on dummy resource ...
        DummyAccount guybrushDummyAccount = getDummyResource().getAccountByUsername(ACCOUNT_GUYBRUSH_DUMMY_USERNAME);

        // location has strong outbound mapping, this change should be corrected
        guybrushDummyAccount.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME, "Phatt Island");

        // BREAK it!
        getDummyResource().setBreakMode(BreakMode.NETWORK);

        getDummyResource().purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_OID);
        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_OID, false, DEFAULT_TASK_WAIT_TIMEOUT, true);

        // THEN
        then();

        dumpStatistics(taskAfter);

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconciliation (broken resource)", users);

        // Total error in the recon process. No reasonable result here.
//        reconciliationTaskResultListener.assertResult(RESOURCE_DUMMY_OID, 0, 7, 1, 0);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        assertEquals("Unexpected number of users", getNumberOfUsers() + 4, users.size());

        displayValue("Dummy resource", getDummyResource().debugDump());

        display("Script history", getDummyResource().getScriptHistory());

        // no scripts
        IntegrationTestTools.assertScripts(getDummyResource().getScriptHistory());

        // Task result
        PrismObject<TaskType> reconTaskAfter = getTask(TASK_RECONCILE_DUMMY_OID);
        OperationResultType reconTaskResult = reconTaskAfter.asObjectable().getResult();
        display("Recon task result", reconTaskResult);
        TestUtil.assertFailure(reconTaskResult);

        // Check audit
        displayDumpable("Audit", dummyAuditService);

        dummyAuditService.assertRecords(0);
    }

    /**
     * Simply re-run recon after the resource is fixed. This should correct the data.
     */
    @Test
    public void test219ReconcileDummyFixed() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Fix it!
        getDummyResource().setBreakMode(BreakMode.NONE);

        getDummyResource().purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_OID);
        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_OID, false, DEFAULT_TASK_WAIT_TIMEOUT, true);

        // THEN
        then();

        dumpStatistics(taskAfter);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 6);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_OID, 0, 7, 0, 2);

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after import", users);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Dubrish Freepweed");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME,
                "Melee Island");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME,
                "Feather duster");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME,
                "rum");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME,
                "Arr!", "I want to be a pirate!");

        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertEquals("Unexpected number of users", getNumberOfUsers() + 4, users.size());

        displayValue("Dummy resource", getDummyResource().debugDump());

        display("Script history", getDummyResource().getScriptHistory());

        ArrayList<ProvisioningScriptSpec> scripts = new ArrayList<>();
        addReconScripts(scripts, ACCOUNT_HERMAN_DUMMY_USERNAME, "Herman Toothrot", false);
        addReconScripts(scripts, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, "Guybrush Threepwood", true);
        addReconScripts(scripts, ACCOUNT_ELAINE_DUMMY_USERNAME, "Elaine Marley", false);
        addReconScripts(scripts, USER_RAPP_USERNAME, USER_RAPP_FULLNAME, false);
        addReconScripts(scripts, ACCOUNT_STAN_NAME, ACCOUNT_STAN_FULLNAME, false);
        IntegrationTestTools.assertScripts(getDummyResource().getScriptHistory(), scripts.toArray(new ProvisioningScriptSpec[0]));

        assertReconAuditModifications(1, TASK_RECONCILE_DUMMY_OID);

        // Task result
        PrismObject<TaskType> reconTaskAfter = getTask(TASK_RECONCILE_DUMMY_OID);
        OperationResultType reconTaskResult = reconTaskAfter.asObjectable().getResult();
        display("Recon task result", reconTaskResult);
        TestUtil.assertSuccess(reconTaskResult);
    }

    /**
     * The resource itself works, just the guybrush account is broken.
     */
    @Test
    public void test220ReconcileDummyBrokenGuybrush() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Lets do some local changes on dummy resource ...
        DummyAccount guybrushDummyAccount = getDummyResource().getAccountByUsername(ACCOUNT_GUYBRUSH_DUMMY_USERNAME);

        // location has strong outbound mapping, this change should be corrected
        guybrushDummyAccount.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME, "Forbidden Dodecahedron");

        // BREAK it!
        getDummyResource().setBreakMode(BreakMode.NONE);
        guybrushDummyAccount.setModifyBreakMode(BreakMode.NETWORK);

        getDummyResource().purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_OID);
        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_OID, false, DEFAULT_TASK_WAIT_TIMEOUT, true);

        // THEN
        then();

        dumpStatistics(taskAfter);
        assertTask(taskAfter, "task after")
                .display()
                .activityState(RECONCILIATION_RESOURCE_OBJECTS_PATH)
                    .display()
                    .synchronizationStatistics()
                        .assertTransition(LINKED, LINKED, LINKED, null, 4, 1, 0) // error is guybrush
                        .assertTransition(null, null, null, PROTECTED, 0, 0, 2)
                        .assertTransitions(2)
                        .end()
                    .itemProcessingStatistics()
                        .display()
                        .assertTotalCounts(6, 1)
                    .end()
                    .progress()
                        .display() // TODO asserts
                    .end()
                ;
                //.assertProgress(7); // TODO - specify meaning of progress for reconciliation tasks

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconciliation (broken resource account)", users);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_OID, 0, 7, 1, 2);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        assertEquals("Unexpected number of users", getNumberOfUsers() + 4, users.size());

        displayValue("Dummy resource", getDummyResource().debugDump());

        display("Script history", getDummyResource().getScriptHistory());
        ArrayList<ProvisioningScriptSpec> scripts = new ArrayList<>();
        addReconScripts(scripts, ACCOUNT_HERMAN_DUMMY_USERNAME, "Herman Toothrot", false);
        // Guybrush is broken.
        addReconScripts(scripts, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, "Guybrush Threepwood", true, false);
        addReconScripts(scripts, ACCOUNT_ELAINE_DUMMY_USERNAME, "Elaine Marley", false);
        addReconScripts(scripts, USER_RAPP_USERNAME, USER_RAPP_FULLNAME, false);
        addReconScripts(scripts, ACCOUNT_STAN_NAME, ACCOUNT_STAN_FULLNAME, false);
        IntegrationTestTools.assertScripts(getDummyResource().getScriptHistory(), scripts.toArray(new ProvisioningScriptSpec[0]));

        // Task result
        PrismObject<TaskType> reconTaskAfter = getTask(TASK_RECONCILE_DUMMY_OID);
        OperationResultType reconTaskResult = reconTaskAfter.asObjectable().getResult();
        display("Recon task result", reconTaskResult);
        TestUtil.assertStatus(reconTaskResult, OperationResultStatusType.PARTIAL_ERROR);

//        OperationResult reconTaskOpResult = OperationResult.createOperationResult(reconTaskResult);
//        // TODO reconsider this
//        OperationResult statistics = reconTaskOpResult
//                .findSubResultStrictly("com.evolveum.midpoint.common.operation.reconciliation.part2")
//                .findSubResultStrictly("com.evolveum.midpoint.common.operation.reconciliation.statistics");
//        assertTrue("Errors not mentioned in the task message", statistics.getMessage().contains("got 1 error"));

        // Check audit
        displayDumpable("Audit", dummyAuditService);
        assertReconAuditModifications(1, TASK_RECONCILE_DUMMY_OID);
    }

    /**
     * Simply re-run recon after the resource is fixed. This should correct the data.
     */
    @Test
    public void test229ReconcileDummyFixed() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Fix it!
        getDummyResource().setBreakMode(BreakMode.NONE);
        getDummyResource().getAccountByUsername(ACCOUNT_GUYBRUSH_DUMMY_USERNAME).setModifyBreakMode(BreakMode.NONE);

        getDummyResource().purgeScriptHistory();
        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_OID);
        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_OID, false, DEFAULT_TASK_WAIT_TIMEOUT, true);

        // THEN
        then();

        dumpStatistics(taskAfter);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 6);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_OID, 0, 7, 0, 2);

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after import", users);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Dubrish Freepweed");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME,
                "Melee Island");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME,
                "Feather duster");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME,
                "rum");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME,
                "Arr!", "I want to be a pirate!");

        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        PrismObject<UserType> userRappAfter = getUser(USER_RAPP_OID);
        display("User rapp after", userRappAfter);
        PrismAsserts.assertPropertyValue(userRappAfter, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME, RESOURCE_DUMMY_OID);
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertEquals("Unexpected number of users", getNumberOfUsers() + 4, users.size());

        displayValue("Dummy resource", getDummyResource().debugDump());

        display("Script history", getDummyResource().getScriptHistory());

        ArrayList<ProvisioningScriptSpec> scripts = new ArrayList<>();
        addReconScripts(scripts, ACCOUNT_HERMAN_DUMMY_USERNAME, "Herman Toothrot", false);
        addReconScripts(scripts, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, "Guybrush Threepwood", true);
        addReconScripts(scripts, ACCOUNT_ELAINE_DUMMY_USERNAME, "Elaine Marley", false);
        addReconScripts(scripts, USER_RAPP_USERNAME, USER_RAPP_FULLNAME, false);
        addReconScripts(scripts, ACCOUNT_STAN_NAME, ACCOUNT_STAN_FULLNAME, false);
        IntegrationTestTools.assertScripts(getDummyResource().getScriptHistory(), scripts.toArray(new ProvisioningScriptSpec[0]));

        assertReconAuditModifications(1, TASK_RECONCILE_DUMMY_OID);

        // Task result
        PrismObject<TaskType> reconTaskAfter = getTask(TASK_RECONCILE_DUMMY_OID);
        OperationResultType reconTaskResult = reconTaskAfter.asObjectable().getResult();
        display("Recon task result", reconTaskResult);
        TestUtil.assertSuccess(reconTaskResult);
    }

    @Test
    public void test230ReconcileDummyRename() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        getDummyResource().setBreakMode(BreakMode.NONE);
        getDummyResource().getAccountByUsername(ACCOUNT_GUYBRUSH_DUMMY_USERNAME).setModifyBreakMode(BreakMode.NONE);

        PrismObject<UserType> userHerman = findUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        String hermanShadowOid = getSingleLinkOid(userHerman);

        assertShadows(14);

        getDummyResource().renameAccount(ACCOUNT_HERMAN_DUMMY_USERNAME, ACCOUNT_HERMAN_DUMMY_USERNAME, ACCOUNT_HTM_NAME);
        DummyAccount dummyAccountHtm = getDummyAccount(null, ACCOUNT_HTM_NAME);
        dummyAccountHtm.replaceAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, ACCOUNT_HTM_FULL_NAME);

        getDummyResource().purgeScriptHistory();
        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_OID);
        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_OID, false, DEFAULT_TASK_WAIT_TIMEOUT, true);

        // THEN
        then();

        dumpStatistics(taskAfter);

        // @formatter:off
        assertTask(taskAfter, "task after")
                .display()
                .activityState(RECONCILIATION_RESOURCE_OBJECTS_PATH)
                    .synchronizationStatistics()
                        .display()
                        .assertTransition(LINKED, LINKED, LINKED, null, 4, 0, 0) // guybrush, elaine, rapp, stan
                        .assertTransition(null, UNMATCHED, LINKED, null, 1, 0, 0) // htm (new name for ht)
                        .assertTransition(null, null, null, PROTECTED, 0, 0, 2) // daviejones, calypso
                        .assertTransitions(3)
                    .end()
                    .itemProcessingStatistics()
                        .display()
                        .assertTotalCounts(5, 0, 2)
                    .end()
                    .progress()
                        .display()
                        .assertCommitted(5, 0, 2)
                        .assertNoUncommitted()
                    .end()
                .end()
                .activityState(RECONCILIATION_REMAINING_SHADOWS_PATH)
                    .synchronizationStatistics()
                        .display()
                        // for ht (old name for htm)
                        .assertTransition(LINKED, DELETED, DELETED, null, 1, 0, 0)
                        // two protected accounts (daviejones, calypso)
                        .assertTransition(null, null, null, PROTECTED, 0, 0, 2)
                        .assertTransitions(2)
                    .end()
                    .itemProcessingStatistics()
                        .display()
                        .assertTotalCounts(1, 0, 2) // 1 renamed, 2 protected
                    .end()
                    .progress()
                        .display()
                        .assertCommitted(1, 0, 2)
                        .assertNoUncommitted()
                    .end()
                .end()
                .assertProgress(10);
        // @formatter:on

        dumpShadowSituations(RESOURCE_DUMMY_OID, result);

        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 6);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_OID, 0, 7, 0, 3);

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after import", users);

        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME); // not deleted. reaction=unlink

        assertRepoShadow(hermanShadowOid)
                .assertTombstone();

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Dubrish Freepweed");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME,
                "Melee Island");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_WEAPON_NAME,
                "Feather duster");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME,
                "rum");
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_QUOTE_NAME,
                "Arr!", "I want to be a pirate!");

        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertEquals("Unexpected number of users", getNumberOfUsers() + 5, users.size());

        displayValue("Dummy resource", getDummyResource().debugDump());

        display("Script history", getDummyResource().getScriptHistory());

        ArrayList<ProvisioningScriptSpec> scripts = new ArrayList<>();
        addReconScripts(scripts, ACCOUNT_GUYBRUSH_DUMMY_USERNAME, "Guybrush Threepwood", false);
        addReconScripts(scripts, ACCOUNT_ELAINE_DUMMY_USERNAME, "Elaine Marley", false);
        addReconScripts(scripts, USER_RAPP_USERNAME, USER_RAPP_FULLNAME, false);
        addReconScripts(scripts, ACCOUNT_STAN_NAME, ACCOUNT_STAN_FULLNAME, false);
        addReconScripts(scripts, ACCOUNT_HTM_NAME, ACCOUNT_HTM_FULL_NAME, true);
        IntegrationTestTools.assertScripts(getDummyResource().getScriptHistory(), scripts.toArray(new ProvisioningScriptSpec[0]));

        assertReconAuditModifications(1, TASK_RECONCILE_DUMMY_OID);

        assertShadows(15);

        // Task result
        PrismObject<TaskType> reconTaskAfter = getTask(TASK_RECONCILE_DUMMY_OID);
        OperationResultType reconTaskResult = reconTaskAfter.asObjectable().getResult();
        display("Recon task result", reconTaskResult);

        // There's (expected) "object not found" error related to ht that was renamed.
        TestUtil.assertSuccess("reconciliation", reconTaskResult, 4);
    }

    private void addReconScripts(Collection<ProvisioningScriptSpec> scripts, String username, String fullName, boolean modified) {
        addReconScripts(scripts, username, fullName, modified, true);
    }

    private void addReconScripts(Collection<ProvisioningScriptSpec> scripts, String username, String fullName,
            boolean modified, boolean afterRecon) {
        // before recon
        ProvisioningScriptSpec script = new ProvisioningScriptSpec("The vorpal blade went snicker-snack!");
        script.addArgSingle("who", username);
        scripts.add(script);

        if (modified) {
            script = new ProvisioningScriptSpec("Beware the Jabberwock, my son!");
            script.addArgSingle("howMuch", null);
            script.addArgSingle("howLong", "from here to there");
            script.addArgSingle("who", username);
            script.addArgSingle("whatchacallit", fullName);
            scripts.add(script);
        }

        if (afterRecon) {
            // after recon
            script = new ProvisioningScriptSpec("He left it dead, and with its head");
            script.addArgSingle("how", "enabled");
            scripts.add(script);
        }
    }

    /**
     * Create illegal (non-correlable) account. See that it is disabled.
     */
    @Test
    public void test300ReconcileDummyAzureAddAccountOtis() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);
        getDummyResource().setBreakMode(BreakMode.NONE);
        dummyResourceAzure.setBreakMode(BreakMode.NONE);

        // Create some illegal account
        dummyResourceCtlAzure.addAccount(ACCOUNT_OTIS_NAME, ACCOUNT_OTIS_FULLNAME);
        displayDumpable("Otis account before", dummyResourceAzure.getAccountByUsername(ACCOUNT_OTIS_NAME));

        dummyResourceAzure.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        importObjectFromFile(TASK_RECONCILE_DUMMY_AZURE.file);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_AZURE.oid, false);

        then();

        dumpStatistics(taskAfter);

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconcile", users);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_AZURE_OID, 0, 1, 0, 0);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);

        // Otis
        assertNoImportedUserByUsername(ACCOUNT_OTIS_NAME);
        displayDumpable("Otis account after", dummyResourceAzure.getAccountByUsername(ACCOUNT_OTIS_NAME));
        assertDummyAccount(RESOURCE_DUMMY_AZURE_NAME, ACCOUNT_OTIS_NAME, ACCOUNT_OTIS_FULLNAME, false);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        // Calypso is protected account. Reconciliation should not touch it
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertEquals("Unexpected number of users", getNumberOfUsers() + 5, users.size());

        displayValue("Dummy resource (azure)", dummyResourceAzure.debugDump());

        assertReconAuditModifications(1, TASK_RECONCILE_DUMMY_AZURE.oid);

        assertShadows(17);
    }

    @Test
    public void test310ReconcileDummyAzureAgain() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);
        getDummyResource().setBreakMode(BreakMode.NONE);
        dummyResourceAzure.setBreakMode(BreakMode.NONE);

        PrismObject<TaskType> reconTask = getTask(TASK_RECONCILE_DUMMY_AZURE.oid);
        display("Recon task", reconTask);

        dummyResourceAzure.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_AZURE.oid);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_AZURE.oid, false);

        dumpStatistics(taskAfter);

        then();
        reconciliationResultListener.assertResult(RESOURCE_DUMMY_AZURE_OID, 0, 1, 0, 0);

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconcile", users);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);

        // Otis
        assertNoImportedUserByUsername(ACCOUNT_OTIS_NAME);
        assertDummyAccount(RESOURCE_DUMMY_AZURE_NAME, ACCOUNT_OTIS_NAME, ACCOUNT_OTIS_FULLNAME, false);

        PrismObject<UserType> userRappAfter = getUser(USER_RAPP_OID);
        display("User rapp after", userRappAfter);
        PrismAsserts.assertPropertyValue(userRappAfter, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        // Calypso is protected account. Reconciliation should not touch it
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertEquals("Unexpected number of users", getNumberOfUsers() + 5, users.size());

        displayValue("Dummy resource (azure)", dummyResourceAzure.debugDump());

        assertReconAuditModifications(0, TASK_RECONCILE_DUMMY_AZURE.oid);

        assertShadows(17);
    }

    @Test
    public void test320ReconcileDummyAzureDeleteOtis() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);
        getDummyResource().setBreakMode(BreakMode.NONE);
        dummyResourceAzure.setBreakMode(BreakMode.NONE);

        assertShadows(17);

        PrismObject<ShadowType> otisShadow = findShadowByName(ShadowKindType.ACCOUNT, SchemaConstants.INTENT_DEFAULT, ACCOUNT_OTIS_NAME, resourceDummyAzure, result);

        dummyResourceAzure.deleteAccountByName(ACCOUNT_OTIS_NAME);

        dummyResourceAzure.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_AZURE.oid);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_AZURE.oid, false);

        dumpStatistics(taskAfter);

        then();

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconcile", users);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_AZURE_OID, 0, 0, 0, 1);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);

        // Otis
        assertNoImportedUserByUsername(ACCOUNT_OTIS_NAME);
        assertNoDummyAccount(RESOURCE_DUMMY_AZURE_NAME, ACCOUNT_OTIS_NAME);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        // Calypso is protected account. Reconciliation should not touch it
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertRepoShadow(otisShadow.getOid())
                .assertTombstone();

        assertShadows(17);

        assertEquals("Unexpected number of users", getNumberOfUsers() + 5, users.size());

        displayValue("Dummy resource (azure)", dummyResourceAzure.debugDump());

        assertReconAuditModifications(0, TASK_RECONCILE_DUMMY_AZURE.oid);
    }

    /**
     * Create account that will correlate to existing user.
     * See that it is linked and modified.
     * MID-4997
     */
    @Test
    public void test330ReconcileDummyAzureAddAccountRapp() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);
        getDummyResource().setBreakMode(BreakMode.NONE);
        dummyResourceAzure.setBreakMode(BreakMode.NONE);

        dummyResourceCtlAzure.addAccount(USER_RAPP_USERNAME, USER_RAPP_FULLNAME);
        displayDumpable("Rapp azure account before", dummyResourceAzure.getAccountByUsername(USER_RAPP_USERNAME));

        PrismObject<UserType> userRappBefore = getUser(USER_RAPP_OID);
        display("User rapp before", userRappBefore);
        PrismAsserts.assertPropertyValue(userRappBefore, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        dummyResourceAzure.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_AZURE.oid);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_AZURE.oid, false);

        dumpStatistics(taskAfter);

        then();

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconcile", users);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_AZURE_OID, 0, 1, 0, 1);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);

        // Rapp
        displayDumpable("Rapp azure account after", dummyResourceAzure.getAccountByUsername(USER_RAPP_USERNAME));
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID, RESOURCE_DUMMY_AZURE_OID);
        assertDummyAccount(RESOURCE_DUMMY_AZURE_NAME, USER_RAPP_USERNAME, USER_RAPP_FULLNAME, true);
        assertDummyAccountAttribute(RESOURCE_DUMMY_AZURE_NAME, USER_RAPP_USERNAME,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "The crew of The Elaine");

        //Checking password policy
        PrismObject<UserType> userRapp = findUserByUsername(USER_RAPP_USERNAME);
        assertNotNull("No user Rapp", userRapp);
        UserType userTypeRapp = userRapp.asObjectable();

        assertNotNull("User Rapp has no credentials", userTypeRapp.getCredentials());
        PasswordType password = userTypeRapp.getCredentials().getPassword();
        assertNotNull("User Rapp has no password", password);

        ProtectedStringType passwordType = password.getValue();

        String stringPassword = null;
        if (passwordType.getClearValue() == null) {
            stringPassword = protector.decryptString(passwordType);
        }

        assertNotNull("No clear text password", stringPassword);
        assertTrue("Rapp's password is supposed to contain letter a: " + stringPassword, stringPassword.contains("a"));

        PrismObject<ValuePolicyType> passwordPolicy = getObjectViaRepo(ValuePolicyType.class, PASSWORD_POLICY_LOWER_CASE_ALPHA_AZURE.oid);

        valuePolicyProcessor.validateValue(
                stringPassword, passwordPolicy.asObjectable(),
                createUserOriginResolver(userRapp), getTestNameShort(), task, result);
        boolean isPasswordValid = result.isAcceptable();
                assertTrue("Password doesn't satisfy password policy, generated password: " + stringPassword, isPasswordValid);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        // Calypso is protected account. Reconciliation should not touch it
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertEquals("Unexpected number of users", getNumberOfUsers() + 5, users.size());

        displayValue("Dummy resource (azure)", dummyResourceAzure.debugDump());

        assertReconAuditModifications(2, TASK_RECONCILE_DUMMY_AZURE.oid); // password via inbounds is generated twice
    }

    /**
     * Make a repository modification of the user Rapp. Run recon. See that the
     * account is modified.
     */
    @Test
    public void test332ModifyUserRappAndReconcileDummyAzure() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);
        getDummyResource().setBreakMode(BreakMode.NONE);
        dummyResourceAzure.setBreakMode(BreakMode.NONE);

        displayDumpable("Rapp azure account before", dummyResourceAzure.getAccountByUsername(USER_RAPP_USERNAME));
        assertDummyAccountAttribute(RESOURCE_DUMMY_AZURE_NAME, USER_RAPP_USERNAME,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "The crew of The Elaine");

        PrismObject<UserType> userRappBefore = getUser(USER_RAPP_OID);
        display("User rapp before", userRappBefore);
        PrismAsserts.assertPropertyValue(userRappBefore, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of The Elaine"));

        ObjectDelta<UserType> userRappDelta = prismContext.deltaFactory().object()
                .createModificationReplaceProperty(UserType.class, USER_RAPP_OID,
                        UserType.F_ORGANIZATIONAL_UNIT, createPolyString("The six feet under crew"));
        repositoryService.modifyObject(UserType.class, USER_RAPP_OID, userRappDelta.getModifications(), result);

        userRappBefore = getUser(USER_RAPP_OID);
        display("User rapp before (modified)", userRappBefore);
        PrismAsserts.assertPropertyValue(userRappBefore, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The six feet under crew"));

        dummyResourceAzure.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_AZURE.oid);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_AZURE.oid, false);

        dumpStatistics(taskAfter);

        then();

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconcile", users);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_AZURE_OID, 0, 1, 0, 1);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);

        // Rapp
        displayDumpable("Rapp azure account after", dummyResourceAzure.getAccountByUsername(USER_RAPP_USERNAME));
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID, RESOURCE_DUMMY_AZURE_OID);
        assertDummyAccount(RESOURCE_DUMMY_AZURE_NAME, USER_RAPP_USERNAME, USER_RAPP_FULLNAME, true);
        assertDummyAccountAttribute(RESOURCE_DUMMY_AZURE_NAME, USER_RAPP_USERNAME,
                DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, "The six feet under crew");

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        // Calypso is protected account. Reconciliation should not touch it
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertEquals("Unexpected number of users", getNumberOfUsers() + 5, users.size());

        displayValue("Dummy resource (azure)", dummyResourceAzure.debugDump());

        assertReconAuditModifications(2, TASK_RECONCILE_DUMMY_AZURE.oid);
    }

    /**
     * Make a repository modification of the user Rapp: assign role corpse.
     * Run recon. See that the account is modified (added to group).
     * There is associationTargetSearch expression in the role. Make sure that the
     * search is done properly (has baseContext).
     */
    @Test
    public void test334AssignRoleCorpseToRappAndReconcileDummyAzure() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.POSITIVE);
        getDummyResource().setBreakMode(BreakMode.NONE);
        dummyResourceAzure.setBreakMode(BreakMode.NONE);

        displayDumpable("Rapp azure account before", dummyResourceAzure.getAccountByUsername(USER_RAPP_USERNAME));
        assertNoDummyGroupMember(RESOURCE_DUMMY_AZURE_NAME, GROUP_CORPSES_NAME, USER_RAPP_USERNAME);

        ObjectDelta<UserType> userRappDelta = createAssignmentUserDelta(USER_RAPP_OID, ROLE_CORPSE.oid,
                RoleType.COMPLEX_TYPE, null, null, true);
        repositoryService.modifyObject(UserType.class, USER_RAPP_OID, userRappDelta.getModifications(), result);

        PrismObject<UserType> userRappBefore = getUser(USER_RAPP_OID);
        display("User rapp before (modified)", userRappBefore);

        dummyResourceAzure.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_AZURE.oid);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_AZURE.oid, false);

        dumpStatistics(taskAfter);

        then();

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconcile", users);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_AZURE_OID, 0, 1, 0, 1);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);

        // Rapp
        displayDumpable("Rapp azure account after", dummyResourceAzure.getAccountByUsername(USER_RAPP_USERNAME));
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID, RESOURCE_DUMMY_AZURE_OID);
        assertDummyAccount(RESOURCE_DUMMY_AZURE_NAME, USER_RAPP_USERNAME, USER_RAPP_FULLNAME, true);
        assertDummyGroupMember(RESOURCE_DUMMY_AZURE_NAME, GROUP_CORPSES_NAME, USER_RAPP_USERNAME);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        // Calypso is protected account. Reconciliation should not touch it
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertEquals("Unexpected number of users", getNumberOfUsers() + 5, users.size());

        displayValue("Dummy resource (azure)", dummyResourceAzure.debugDump());

        assertReconAuditModifications(2, TASK_RECONCILE_DUMMY_AZURE.oid); // password via inbounds is generated twice
    }

    /**
     * Account `rapp` is deleted.
     *
     * Among other things, the shadow transition to DELETED sync state should be reported (MID-7724).
     *
     * In the third reconciliation stage (remainingShadows) two accounts are processed:
     *
     * 1. dead `otis` account (deleted in {@link #test320ReconcileDummyAzureDeleteOtis()})
     *     (actually it is questionable if we should process dead shadows in that stage!)
     * 2. now-deleted `rapp` account
     */
    @Test
    public void test339ReconcileDummyAzureDeleteRapp() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);
        getDummyResource().setBreakMode(BreakMode.NONE);
        dummyResourceAzure.setBreakMode(BreakMode.NONE);

        assertShadows(19);

        // Remove the assignment. It may do bad things later.
        ObjectDelta<UserType> userRappDelta = createAssignmentUserDelta(USER_RAPP_OID, ROLE_CORPSE.oid,
                RoleType.COMPLEX_TYPE, null, null, false);
        repositoryService.modifyObject(UserType.class, USER_RAPP_OID, userRappDelta.getModifications(), result);

        PrismObject<ShadowType> rappShadow = findShadowByName(ShadowKindType.ACCOUNT,
                SchemaConstants.INTENT_DEFAULT, USER_RAPP_USERNAME, resourceDummyAzure, result);

        dummyResourceAzure.deleteAccountByName(USER_RAPP_USERNAME);

        dummyResourceAzure.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
//        setGlobalTracingOverride(createModelLoggingTracingProfile());
        restartTask(TASK_RECONCILE_DUMMY_AZURE.oid);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_AZURE.oid, false);

//        unsetGlobalTracingOverride();

        dumpStatistics(taskAfter);

        then();

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconcile", users);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_AZURE_OID, 0, 0, 0, 2);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);

        // Rapp
        assertNoImportedUserByUsername(ACCOUNT_OTIS_NAME);
        assertNoDummyAccount(RESOURCE_DUMMY_AZURE_NAME, USER_RAPP_USERNAME);

        assertNoDummyAccount(RESOURCE_DUMMY_AZURE_NAME, ACCOUNT_OTIS_NAME);
        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);
        // Calypso is protected account. Reconciliation should not touch it
        assertDummyAccountAttribute(null, ACCOUNT_CALYPSO_DUMMY_USERNAME, DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME,
                "Calypso");

        assertRepoShadow(rappShadow.getOid())
                .assertTombstone();

        assertShadows(19);

        assertEquals("Unexpected number of users", getNumberOfUsers() + 5, users.size());

        displayValue("Dummy resource (azure)", dummyResourceAzure.debugDump());

        // deleting linkRef
        assertReconAuditModifications(1, TASK_RECONCILE_DUMMY_AZURE.oid);

        // @formatter:off
        assertTask(TASK_RECONCILE_DUMMY_AZURE.oid, "after")
                .display()
                .synchronizationInformation(RECONCILIATION_REMAINING_SHADOWS_PATH)
                    .display()
                    // otis (dead shadow) - maybe in the future we won't process such accounts here
                    .assertTransition(DELETED, null, null, SYNCHRONIZATION_NOT_NEEDED, 0, 0, 1)
                    .assertTransition(LINKED, DELETED, DELETED, null, 1, 0, 0) // rapp
                    .assertTransitions(2)
                .end();
        // @formatter:on
    }

    @Test
    public void test400ReconcileDummyLimeAddAccount() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Create some illegal account
        DummyAccount accountKate = dummyResourceCtlLime.addAccount(ACCOUNT_CAPSIZE_NAME, ACCOUNT_CAPSIZE_FULLNAME);
        accountKate.setPassword(ACCOUNT_CAPSIZE_PASSWORD);

        dummyResourceLime.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        importObjectFromFile(TASK_RECONCILE_DUMMY_LIME.file);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_LIME.oid, false);

        dumpStatistics(taskAfter);

        then();

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconcile", users);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_LIME_OID, 0, 4, 0, 0);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);

        // Kate Capsize: user should be created
        assertImportedUserByUsername(ACCOUNT_CAPSIZE_NAME, RESOURCE_DUMMY_LIME_OID);
        PrismObject<UserType> userAfter = findUserByUsername(ACCOUNT_CAPSIZE_NAME);
        assertPassword(userAfter, ACCOUNT_CAPSIZE_PASSWORD);

        assertEquals("Unexpected number of users", getNumberOfUsers() + 6, users.size());

        displayValue("Dummy resource (lime)", dummyResourceLime.debugDump());

        // Audit record structure is somehow complex here.
        // I am not sure about the correct number of mods, but 3 looks good.
        assertReconAuditModifications(3, TASK_RECONCILE_DUMMY_LIME.oid);
    }

    @Test
    public void test401ReconcileDummyLimeKateOnlyEmpty() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        DummyAccount accountKate = dummyResourceLime.getAccountByUsername(ACCOUNT_CAPSIZE_NAME);
        accountKate.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, "");

        PrismObject<UserType> userBefore = findUserByUsername(ACCOUNT_CAPSIZE_NAME);
        PrismAsserts.assertNoItem(userBefore, UserType.F_COST_CENTER);

        dummyResourceLime.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        reconcileUser(userBefore.getOid(), task, result);

        // THEN
        then();
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = findUserByUsername(ACCOUNT_CAPSIZE_NAME);
        display("User after reconcile", userAfter);

        PrismAsserts.assertPropertyValue(userAfter, UserType.F_COST_CENTER, "");

        displayDumpable("Audit", dummyAuditService);
        dummyAuditService.assertRecords(2);
        dummyAuditService.assertSimpleRecordSanity();
        dummyAuditService.assertAnyRequestDeltas();
        dummyAuditService.assertExecutionDeltas(1);
        dummyAuditService.assertHasDelta(ChangeType.MODIFY, UserType.class);
        PrismAsserts.assertModifications(dummyAuditService.getExecutionDelta(0).getObjectDelta(), 7);
        dummyAuditService.assertTarget(userBefore.getOid());
        dummyAuditService.assertExecutionSuccess();

        assertUsers(getNumberOfUsers() + 6);

        displayValue("Dummy resource (lime)", dummyResourceLime.debugDump());
    }

    @Test
    public void test402ReconcileDummyLimeKateOnlyGrog() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        DummyAccount accountKate = dummyResourceLime.getAccountByUsername(ACCOUNT_CAPSIZE_NAME);
        accountKate.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, "grog");

        PrismObject<UserType> userBefore = findUserByUsername(ACCOUNT_CAPSIZE_NAME);

        dummyResourceLime.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        reconcileUser(userBefore.getOid(), task, result);

        // THEN
        then();
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = findUserByUsername(ACCOUNT_CAPSIZE_NAME);
        display("User after reconcile", userAfter);

        PrismAsserts.assertPropertyValue(userAfter, UserType.F_COST_CENTER, "grog");

        displayDumpable("Audit", dummyAuditService);
        dummyAuditService.assertRecords(2);
        dummyAuditService.assertSimpleRecordSanity();
        dummyAuditService.assertAnyRequestDeltas();
        dummyAuditService.assertExecutionDeltas(1);
        dummyAuditService.assertHasDelta(ChangeType.MODIFY, UserType.class);
        PrismAsserts.assertModifications(dummyAuditService.getExecutionDelta(0).getObjectDelta(), 7);
        dummyAuditService.assertTarget(userBefore.getOid());
        dummyAuditService.assertExecutionSuccess();

        assertUsers(getNumberOfUsers() + 6);

        displayValue("Dummy resource (lime)", dummyResourceLime.debugDump());
    }

    @Test
    public void test403ReconcileDummyLimeKateOnlyNoValue() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        DummyAccount accountKate = dummyResourceLime.getAccountByUsername(ACCOUNT_CAPSIZE_NAME);
        accountKate.replaceAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME);
        displayValue("Dummy resource (lime)", dummyResourceLime.debugDump());

        PrismObject<UserType> userBefore = findUserByUsername(ACCOUNT_CAPSIZE_NAME);

        dummyResourceLime.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        reconcileUser(userBefore.getOid(), task, result);

        // THEN
        then();
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = findUserByUsername(ACCOUNT_CAPSIZE_NAME);
        display("User after reconcile", userAfter);

        PrismAsserts.assertNoItem(userAfter, UserType.F_COST_CENTER);

        displayDumpable("Audit", dummyAuditService);
        dummyAuditService.assertRecords(2);
        dummyAuditService.assertSimpleRecordSanity();
        dummyAuditService.assertAnyRequestDeltas();
        dummyAuditService.assertExecutionDeltas(1);
        dummyAuditService.assertHasDelta(ChangeType.MODIFY, UserType.class);
        PrismAsserts.assertModifications(dummyAuditService.getExecutionDelta(0).getObjectDelta(), 7);
        dummyAuditService.assertTarget(userBefore.getOid());
        dummyAuditService.assertExecutionSuccess();

        assertUsers(getNumberOfUsers() + 6);
    }

    @Test
    public void test404ReconcileDummyLimeKateOnlyRum() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        DummyAccount accountKate = dummyResourceLime.getAccountByUsername(ACCOUNT_CAPSIZE_NAME);
        accountKate.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, "rum");

        PrismObject<UserType> userBefore = findUserByUsername(ACCOUNT_CAPSIZE_NAME);

        dummyResourceLime.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        reconcileUser(userBefore.getOid(), task, result);

        // THEN
        then();
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = findUserByUsername(ACCOUNT_CAPSIZE_NAME);
        display("User after reconcile", userAfter);

        PrismAsserts.assertPropertyValue(userAfter, UserType.F_COST_CENTER, "rum");

        displayDumpable("Audit", dummyAuditService);
        dummyAuditService.assertRecords(2);
        dummyAuditService.assertSimpleRecordSanity();
        dummyAuditService.assertAnyRequestDeltas();
        dummyAuditService.assertExecutionDeltas(1);
        dummyAuditService.assertHasDelta(ChangeType.MODIFY, UserType.class);
        PrismAsserts.assertModifications(dummyAuditService.getExecutionDelta(0).getObjectDelta(), 7);
        dummyAuditService.assertTarget(userBefore.getOid());
        dummyAuditService.assertExecutionSuccess();

        assertUsers(getNumberOfUsers() + 6);

        displayValue("Dummy resource (lime)", dummyResourceLime.debugDump());
    }

    @Test
    public void test405ReconcileDummyLimeKateOnlyEmpty() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        DummyAccount accountKate = dummyResourceLime.getAccountByUsername(ACCOUNT_CAPSIZE_NAME);
        accountKate.replaceAttributeValue(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, "");

        PrismObject<UserType> userBefore = findUserByUsername(ACCOUNT_CAPSIZE_NAME);

        dummyResourceLime.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        reconcileUser(userBefore.getOid(), task, result);

        // THEN
        then();
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = findUserByUsername(ACCOUNT_CAPSIZE_NAME);
        display("User after reconcile", userAfter);

        PrismAsserts.assertPropertyValue(userAfter, UserType.F_COST_CENTER, "");

        displayDumpable("Audit", dummyAuditService);
        dummyAuditService.assertRecords(2);
        dummyAuditService.assertSimpleRecordSanity();
        dummyAuditService.assertAnyRequestDeltas();
        dummyAuditService.assertExecutionDeltas(1);
        dummyAuditService.assertHasDelta(ChangeType.MODIFY, UserType.class);
        PrismAsserts.assertModifications(dummyAuditService.getExecutionDelta(0).getObjectDelta(), 7);
        dummyAuditService.assertTarget(userBefore.getOid());
        dummyAuditService.assertExecutionSuccess();

        assertUsers(getNumberOfUsers() + 6);

        displayValue("Dummy resource (lime)", dummyResourceLime.debugDump());
    }

    @Test
    public void test406ReconcileDummyLimeKateOnlyEmptyAgain() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        PrismObject<UserType> userBefore = findUserByUsername(ACCOUNT_CAPSIZE_NAME);

        dummyResourceLime.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        reconcileUser(userBefore.getOid(), task, result);

        // THEN
        then();
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = findUserByUsername(ACCOUNT_CAPSIZE_NAME);
        display("User after reconcile", userAfter);

        PrismAsserts.assertPropertyValue(userAfter, UserType.F_COST_CENTER, "");

        displayDumpable("Audit", dummyAuditService);
        dummyAuditService.assertRecords(2);
        dummyAuditService.assertSimpleRecordSanity();
        dummyAuditService.assertAnyRequestDeltas();
        dummyAuditService.assertExecutionDeltas(0);
        dummyAuditService.assertTarget(userBefore.getOid());
        dummyAuditService.assertExecutionSuccess();

        assertUsers(getNumberOfUsers() + 6);

        displayValue("Dummy resource (lime)", dummyResourceLime.debugDump());
    }

    @Test
    public void test410ReconcileDummyLimeKatePassword() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        DummyAccount accountKate = dummyResourceLime.getAccountByUsername(ACCOUNT_CAPSIZE_NAME);
        accountKate.setPassword("d0d3c4h3dr0n");

        PrismObject<UserType> userBefore = findUserByUsername(ACCOUNT_CAPSIZE_NAME);

        dummyResourceLime.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        reconcileUser(userBefore.getOid(), task, result);

        // THEN
        then();
        assertSuccess(result);

        PrismObject<UserType> userAfter = findUserByUsername(ACCOUNT_CAPSIZE_NAME);
        display("User after reconcile", userAfter);

        assertPassword(userAfter, "d0d3c4h3dr0n");

        displayDumpable("Audit", dummyAuditService);
        dummyAuditService.assertRecords(2);
        dummyAuditService.assertSimpleRecordSanity();
        dummyAuditService.assertAnyRequestDeltas();
        dummyAuditService.assertExecutionDeltas(1);
        dummyAuditService.assertHasDelta(ChangeType.MODIFY, UserType.class);
        PrismAsserts.assertModifications(dummyAuditService.getExecutionDelta(0).getObjectDelta(), 11);
        dummyAuditService.assertTarget(userBefore.getOid());
        dummyAuditService.assertExecutionSuccess();

        assertUsers(getNumberOfUsers() + 6);

        displayValue("Dummy resource (lime)", dummyResourceLime.debugDump());
    }

    @Test
    public void test420ReconcileDummyLimeDeleteLinkedAccount() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Create some illegal account
        dummyResourceLime.deleteAccountByName(ACCOUNT_CAPSIZE_NAME);

        dummyResourceLime.purgeScriptHistory();
        dummyAuditService.clear();
        reconciliationResultListener.clear();

        // WHEN
        when();
        restartTask(TASK_RECONCILE_DUMMY_LIME.oid);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_LIME.oid, false);

        dumpStatistics(taskAfter);

        then();

        List<PrismObject<UserType>> users = modelService.searchObjects(UserType.class, null, null, task, result);
        display("Users after reconcile", users);

        reconciliationResultListener.assertResult(RESOURCE_DUMMY_LIME_OID, 0, 3, 0, 1);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);

        // Kate Capsize: user should be gone
        assertNoImportedUserByUsername(ACCOUNT_CAPSIZE_NAME);

        assertEquals("Unexpected number of users", getNumberOfUsers() + 5, users.size());

        displayValue("Dummy resource (lime)", dummyResourceLime.debugDump());

        // Audit record structure is somehow complex here.
        // I am not sure about the correct number of mods, but 3 looks good.
        assertReconAuditModifications(3, TASK_RECONCILE_DUMMY_LIME.oid);
    }

    /**
     * Imports a testing account (Taugustus)
     */
    @Test
    public void test500ImportTAugustusFromResourceDummy() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        PrismObject<ShadowType> accountTaugustus = PrismTestUtil.parseObject(ACCOUNT_TAUGUSTUS.file);
        provisioningService.addObject(accountTaugustus, null, null, task, result);

        // Preconditions
        assertUsers(getNumberOfUsers() + 5);

        loginImportUser();

        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        modelService.importFromResource(ACCOUNT_TAUGUSTUS.oid, task, result);

        // THEN
        then();
        assertSuccess(result);

        loginAdministrator();

        // First fetch: import handler reading the account
        // Second fetch: fetchback to correctly process inbound (import changes the account).
//        assertShadowFetchOperationCountIncrement(2);

        // WHY???
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_STAN_NAME, RESOURCE_DUMMY_OID);
        PrismObject<UserType> userAugustusAfter = assertImportedUserByUsername(USER_AUGUSTUS_NAME, RESOURCE_DUMMY_OID);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        assertUsers(getNumberOfUsers() + 6);

        assertShadowKindIntent(ACCOUNT_TAUGUSTUS.oid, ShadowKindType.ACCOUNT, INTENT_TEST);

        display("User augustus after", userAugustusAfter);
        assertLiveLinks(userAugustusAfter, 1);
        PrismAsserts.assertPropertyValue(userAugustusAfter, UserType.F_ORGANIZATIONAL_UNIT,
                createPolyString("The crew of Titanicum Augusticum"));

        assertImportAuditModifications(1);
    }

    /**
     * Imports a default account (augustus), it should be linked
     */
    @Test
    public void test502ImportAugustusFromResourceDummy() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        PrismObject<UserType> userAugustusBefore = findUserByUsername(USER_AUGUSTUS_NAME);
        display("User augustus before", userAugustusBefore);

        PrismObject<ShadowType> account = PrismTestUtil.parseObject(ACCOUNT_AUGUSTUS.file);
        provisioningService.addObject(account, null, null, task, result);
        display("Account augustus before", account);

        // Preconditions
        assertUsers(getNumberOfUsers() + 6);

        loginImportUser();

        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        modelService.importFromResource(ACCOUNT_AUGUSTUS.oid, task, result);

        // THEN
        then();
        assertSuccess(result);

        loginAdministrator();

        // First fetch: import handler reading the account
        // Second fetch: fetchback to correctly process inbound (import changes the account).
//        assertShadowFetchOperationCountIncrement(2);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_STAN_NAME, RESOURCE_DUMMY_OID);
        PrismObject<UserType> userAugustusAfter = assertImportedUserByUsername(USER_AUGUSTUS_NAME, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_OID);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        assertUsers(getNumberOfUsers() + 6);

        assertShadowKindIntent(ACCOUNT_AUGUSTUS.oid, ShadowKindType.ACCOUNT, SchemaConstants.INTENT_DEFAULT);
        assertShadowKindIntent(ACCOUNT_TAUGUSTUS.oid, ShadowKindType.ACCOUNT, INTENT_TEST);

        display("User augustus after", userAugustusAfter);
        assertLiveLinks(userAugustusAfter, 2);
        // Gives wrong results now. See MID-2532
//        PrismAsserts.assertPropertyValue(userAugustusAfter, UserType.F_ORGANIZATIONAL_UNIT,
//                createPolyString("The crew of Titanicum Augusticum"),
//                createPolyString("The crew of Boatum Mailum"));

        assertImportAuditModifications(1);
    }

    /**
     * This should import all the intents in the object class
     */
    @Test
    public void test510ImportFromResourceDummy() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        PrismObject<ShadowType> account = PrismTestUtil.parseObject(ACCOUNT_KENNY.file);
        provisioningService.addObject(account, null, null, task, result);

        account = PrismTestUtil.parseObject(ACCOUNT_TPALIDO.file);
        provisioningService.addObject(account, null, null, task, result);

        account = PrismTestUtil.parseObject(ACCOUNT_LECHIMP.file);
        provisioningService.addObject(account, null, null, task, result);

        account = PrismTestUtil.parseObject(ACCOUNT_TLECHIMP.file);
        provisioningService.addObject(account, null, null, task, result);

        account = PrismTestUtil.parseObject(ACCOUNT_ANDRE.file);
        provisioningService.addObject(account, null, null, task, result);

        account = PrismTestUtil.parseObject(ACCOUNT_TANDRE.file);
        provisioningService.addObject(account, null, null, task, result);

        account = PrismTestUtil.parseObject(ACCOUNT_TLAFOOT.file);
        provisioningService.addObject(account, null, null, task, result);

        account = PrismTestUtil.parseObject(ACCOUNT_CRUFF.file);
        provisioningService.addObject(account, null, null, task, result);

        // Preconditions
        assertUsers(getNumberOfUsers() + 6);

        loginImportUser();

        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        modelService.importFromResource(RESOURCE_DUMMY_OID, DUMMY_ACCOUNT_OBJECT_CLASS, task, result);

        // THEN
        then();
        OperationResult subresult = result.getLastSubresult();
        TestUtil.assertInProgress("importAccountsFromResource result", subresult);

        loginAdministrator();

        waitForTaskFinish(task, false, 40000);

        dumpStatistics(task);

        // THEN
        then();
        TestUtil.assertSuccess(task.getResult());

        // First fetch: search in import handler
        // 6 fetches: fetchback to correctly process inbound (import changes the account).
        // The accounts are modified during import as there are also outbound mappings in
        // ther dummy resource. As the import is in fact just a recon the "fetchbacks" happens.
        // One is because of counting resource objects before importing them.
//        assertShadowFetchOperationCountIncrement(8);

        // WHY????
//        assertShadowFetchOperationCountIncrement(4);

        assertImportedUserByOid(USER_ADMINISTRATOR_OID);
        assertImportedUserByOid(USER_JACK_OID);
        assertImportedUserByOid(USER_BARBOSSA_OID);
        assertImportedUserByOid(USER_GUYBRUSH_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByOid(USER_RAPP_OID, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_LIME_OID);
        assertImportedUserByUsername(ACCOUNT_HERMAN_DUMMY_USERNAME);
        assertImportedUserByUsername(ACCOUNT_HTM_NAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_STAN_NAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(USER_AUGUSTUS_NAME, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_KENNY.name, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(USER_PALIDO_NAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_LECHIMP.name, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_CRUFF.name, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(USER_LAFOOT_NAME, RESOURCE_DUMMY_OID);
        assertImportedUserByUsername(ACCOUNT_ANDRE.name, RESOURCE_DUMMY_OID, RESOURCE_DUMMY_OID);

        // These are protected accounts, they should not be imported
        assertNoImportedUserByUsername(ACCOUNT_DAVIEJONES_DUMMY_USERNAME);
        assertNoImportedUserByUsername(ACCOUNT_CALYPSO_DUMMY_USERNAME);

        assertUsers(getNumberOfUsers() + 12);

        assertShadowKindIntent(ACCOUNT_AUGUSTUS.oid, ShadowKindType.ACCOUNT, SchemaConstants.INTENT_DEFAULT);
        assertShadowKindIntent(ACCOUNT_TAUGUSTUS.oid, ShadowKindType.ACCOUNT, INTENT_TEST);
    }

    /**
     * This should reconcile only accounts matched by filter
     */
    @Test
    public void test520ReconResourceDummyFilter() throws Exception {
        // GIVEN
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.NONE);

        // Preconditions
        assertUsers(getNumberOfUsers() + 12);

        loginImportUser();

        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        // runPrivileged is necessary for TestImportReconAuthorizations as importObjectFromFile() is using raw operations
        runPrivileged(() -> {
            try {
                importObjectFromFile(TASK_RECONCILE_DUMMY_FILTER.file);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            return null;
        });

        Task taskAfter = waitForTaskFinish(TASK_RECONCILE_DUMMY_FILTER.oid, false, 40000);
        dumpStatistics(taskAfter);
        assertTask(taskAfter, "after")
                .activityState(RECONCILIATION_RESOURCE_OBJECTS_PATH)
                    .synchronizationStatistics()
                        .assertTransition(LINKED, LINKED, LINKED, null, 12, 0, 0)
                        .assertTransition(null, null, null, PROTECTED, 0, 0, 2)
                        .assertTransitions(2);
    }

    @Test
    public void test600SearchAllDummyAccounts() throws Exception {
        // GIVEN
        loginAdministrator();

        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectQuery query = ObjectQueryUtil.createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID,
                DUMMY_ACCOUNT_OBJECT_CLASS, prismContext);

        // WHEN
        when();
        SearchResultList<PrismObject<ShadowType>> objects = modelService.searchObjects(ShadowType.class, query, null, task, result);

        // THEN
        then();
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("Found", objects);

        assertEquals("Wrong number of objects found", 17, objects.size());
    }

    @Test
    public void test610SearchDummyAccountsNameSubstring() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        ObjectQuery query =
                ObjectQueryUtil.createResourceAndObjectClassFilterPrefix(RESOURCE_DUMMY_OID, DUMMY_ACCOUNT_OBJECT_CLASS, prismContext)
                        .and().item(
                                ItemPath.create(ShadowType.F_ATTRIBUTES, SchemaConstants.ICFS_NAME),
                                ObjectFactory.createResourceAttributeDefinition(SchemaConstants.ICFS_NAME, DOMUtil.XSD_STRING))
                        .contains("s")
                        .build();

        // WHEN
        when();
        SearchResultList<PrismObject<ShadowType>> objects = modelService.searchObjects(ShadowType.class, query, null, task, result);

        // THEN
        then();
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("Found", objects);

        assertEquals("Wrong number of objects found", 6, objects.size());
    }

    /**
     * Deleting dummy shadows in raw mode: searching in repo, and deleting from the repo.
     */
    @Test
    public void test900DeleteDummyShadows() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // Preconditions
        assertUsers(getNumberOfUsers() + 12);
        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        importObjectFromFile(TASK_DELETE_DUMMY_SHADOWS.file);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_DELETE_DUMMY_SHADOWS.oid, false, 20000);
        dumpStatistics(taskAfter);

        // THEN
        then();
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 0);

        // @formatter:off
        assertTask(TASK_DELETE_DUMMY_SHADOWS.oid, "after")
                .rootActivityState()
                    .itemProcessingStatistics()
                        .display()
                        .assertTotalCounts(18, 0, 0); // deleted also protected shadows
        // @formatter:on

        // Checking operation result internal structure.
        PrismObject<TaskType> deleteTask = getTask(TASK_DELETE_DUMMY_SHADOWS.oid);
        OperationResultType deleteTaskResultBean = deleteTask.asObjectable().getResult();
        display("Final delete task result", deleteTaskResultBean);

        TestUtil.assertSuccess(deleteTaskResultBean);
        OperationResult deleteTaskResult = OperationResult.createOperationResult(deleteTaskResultBean);
        TestUtil.assertSuccess(deleteTaskResult);

        String OP_PROCESS = "com.evolveum.midpoint.repo.common.activity.run.processing.ItemProcessingGatekeeper.process";
        List<OperationResult> processSearchResults = deleteTaskResult.findSubresultsDeeply(OP_PROCESS);
        assertThat(processSearchResults).as("'process item' operation results").hasSize(11);
        assertThat(processSearchResults.get(processSearchResults.size() - 1).getHiddenRecordsCount())
                .as("hidden operation results")
                .isEqualTo(8);

        assertUsers(getNumberOfUsers() + 12);

        assertDummyAccountShadows(0, true, task, result);
        assertDummyAccountShadows(17, false, task, result);
    }

    /**
     * Deleting dummy _accounts_ i.e. in non-raw mode: searching on resource, and deleting from the resource.
     */
    @Test
    public void test910DeleteDummyAccounts() throws Exception {
        // GIVEN
        Task task = getTestTask();
        OperationResult result = task.getResult();

        // Preconditions
        assertUsers(getNumberOfUsers() + 12);
        dummyAuditService.clear();
        rememberCounter(InternalCounters.SHADOW_FETCH_OPERATION_COUNT);

        // WHEN
        when();
        importObjectFromFile(TASK_DELETE_DUMMY_ACCOUNTS.file);

        // THEN
        then();

        Task taskAfter = waitForTaskFinish(TASK_DELETE_DUMMY_ACCOUNTS.oid, false, 20000);
        dumpStatistics(taskAfter);

        // THEN
        then();
        assertCounterIncrement(InternalCounters.SHADOW_FETCH_OPERATION_COUNT, 1); // One search operation

        // @formatter:off
        assertTask(TASK_DELETE_DUMMY_ACCOUNTS.oid, "after")
                .rootActivityState()
                    .itemProcessingStatistics()
                        .display()
                        .assertTotalCounts(15, 0, 2); // two protected accounts not deleted
        // @formatter:on

        // Operation result structure is currently not as neat as when pure repo access is used.
        // So let's skip these tests for now.

        assertUsers(getNumberOfUsers() + 12);

        assertDummyAccountShadows(2, true, task, result); // two protected accounts
        assertDummyAccountShadows(2, false, task, result);
    }

    private void assertDummyAccountShadows(int expected, boolean raw, Task task, OperationResult result) throws CommonException {
        ObjectQuery query = ObjectQueryUtil.createResourceAndObjectClassQuery(RESOURCE_DUMMY_OID,
                DUMMY_ACCOUNT_OBJECT_CLASS, prismContext);

        final MutableInt count = new MutableInt(0);
        ResultHandler<ShadowType> handler = (shadow, parentResult) -> {
            count.increment();
            display("Found", shadow);
            return true;
        };
        Collection<SelectorOptions<GetOperationOptions>> options = null;
        if (raw) {
            options = SelectorOptions.createCollection(GetOperationOptions.createRaw());
        }
        modelService.searchObjectsIterative(ShadowType.class, query, handler, options, task, result);
        assertEquals("Unexpected number of search results (raw=" + raw + ")", expected, count.getValue());
    }

    private void assertImportAuditModifications(int expectedModifications) {
        displayDumpable("Audit", dummyAuditService);

        List<AuditEventRecord> auditRecords = dummyAuditService.getRecords();

        int i = 0;
        int modifications = 0;
        for (; i < auditRecords.size() - 1; i += 2) {
            AuditEventRecord requestRecord = auditRecords.get(i);
            assertNotNull("No request audit record (" + i + ")", requestRecord);
            assertEquals("Got this instead of request audit record (" + i + "): " + requestRecord, AuditEventStage.REQUEST, requestRecord.getEventStage());
            Collection<ObjectDeltaOperation<? extends ObjectType>> requestDeltas = requestRecord.getDeltas();
            assertTrue("Unexpected delta in request audit record " + requestRecord,
                    requestDeltas.isEmpty() || requestDeltas.size() == 1 && requestDeltas.iterator().next().getObjectDelta().isAdd());

            AuditEventRecord executionRecord = auditRecords.get(i + 1);
            assertNotNull("No execution audit record (" + i + ")", executionRecord);
            assertEquals("Got this instead of execution audit record (" + i + "): " + executionRecord, AuditEventStage.EXECUTION, executionRecord.getEventStage());

            assertThat(executionRecord.getDeltas())
                    .withFailMessage("Empty deltas in execution audit record " + executionRecord)
                    .isNotEmpty();
            modifications++;

            // check next records
            while (i < auditRecords.size() - 2) {
                AuditEventRecord nextRecord = auditRecords.get(i + 2);
                if (nextRecord.getEventStage() == AuditEventStage.EXECUTION) {
                    // more than one execution record is OK
                    i++;
                } else {
                    break;
                }
            }

        }
        assertEquals("Unexpected number of audit modifications", expectedModifications, modifications);
    }

    private void assertReconAuditModifications(int expectedModifications, String taskOid) {
        displayDumpable("Audit (all records)", dummyAuditService);

        List<AuditEventRecord> auditRecords = dummyAuditService.getRecords();

        // Skip unrelated records (other tasks)
        auditRecords.removeIf(
                record -> record.getTaskOid() != null && !record.getTaskOid().equals(taskOid));

        // Skip unrelated records (raw changes)
        auditRecords.removeIf(
                record -> record.getEventType() == AuditEventType.EXECUTE_CHANGES_RAW);

        // Skip request-stage records
        auditRecords.removeIf(
                record -> record.getEventStage() == AuditEventStage.REQUEST);

        displayDumpable("Audit (relevant modifications)", dummyAuditService);

        for (AuditEventRecord record : auditRecords) {
            assertEquals("Non-execution audit record sneaked in: " + record,
                    AuditEventStage.EXECUTION, record.getEventStage());
            assertThat(record.getDeltas())
                    .withFailMessage("Empty deltas in execution audit record " + record)
                    .isNotEmpty();
        }
        assertEquals("Unexpected number of audit modifications", expectedModifications, auditRecords.size());
    }

    private void assertNoImportedUserByUsername(String username) throws CommonException {
        PrismObject<UserType> user = findUserByUsername(username);
        assertNull("User " + username + " sneaked in", user);
    }

    private void assertImportedUserByOid(String userOid, String... resourceOids) throws CommonException {
        PrismObject<UserType> user = getUser(userOid);
        assertNotNull("No user " + userOid, user);
        assertImportedUser(user, resourceOids);
    }

    private PrismObject<UserType> assertImportedUserByUsername(String username, String... resourceOids) throws CommonException {
        PrismObject<UserType> user = findUserByUsername(username);
        assertNotNull("No user " + username, user);
        assertImportedUser(user, resourceOids);
        return user;
    }

    private void assertImportedUser(PrismObject<UserType> user, String... resourceOids) throws CommonException {
        display("Imported user", user);
        assertLiveLinks(user, resourceOids.length);
        for (String resourceOid : resourceOids) {
            assertAccount(user, resourceOid);
        }
        assertAdministrativeStatusEnabled(user);
    }
}
