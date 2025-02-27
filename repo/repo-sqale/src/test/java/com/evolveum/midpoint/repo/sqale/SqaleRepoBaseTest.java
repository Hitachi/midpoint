/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.xml.namespace.QName;

import com.querydsl.core.types.Predicate;
import com.querydsl.sql.SQLQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.BeforeClass;

import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.S_FilterEntryOrEmpty;
import com.evolveum.midpoint.prism.query.builder.S_FilterExit;
import com.evolveum.midpoint.repo.api.perf.OperationPerformanceInformation;
import com.evolveum.midpoint.repo.sqale.audit.SqaleAuditService;
import com.evolveum.midpoint.repo.sqale.qmodel.common.QUri;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItem;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItemHolderType;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.QExtItem;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObject;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObjectType;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObject;
import com.evolveum.midpoint.repo.sqale.qmodel.ref.MReference;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.perfmon.SqlPerformanceMonitorImpl;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sqlbase.querydsl.SqlRecorder;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.test.util.AbstractSpringTest;
import com.evolveum.midpoint.test.util.InfraTestMixin;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAttributesType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;

@ContextConfiguration(locations = { "../../../../../ctx-test.xml" })
public class SqaleRepoBaseTest extends AbstractSpringTest
        implements InfraTestMixin {

    public static final String REPO_OP_PREFIX = SqaleRepositoryService.class.getSimpleName() + '.';
    public static final String AUDIT_OP_PREFIX = SqaleAuditService.class.getSimpleName() + '.';

    private static final int QUERY_BUFFER_SIZE = 1000;
    public static final String SYSTEM_PROPERTY_SKIP_DB_CLEAR = "skipDbClear";

    private static boolean cacheTablesCleared = false;

    @Autowired protected SqaleRepositoryService repositoryService;
    @Autowired protected SqaleRepoContext sqlRepoContext;
    @Autowired protected SqaleRepositoryConfiguration repositoryConfiguration;
    @Autowired protected PrismContext prismContext;
    @Autowired protected RelationRegistry relationRegistry;

    @Autowired protected AuditService auditService;

    protected SqlRecorder queryRecorder;

    @BeforeClass
    public void initDatabase() throws Exception {
        queryRecorder = new SqlRecorder(QUERY_BUFFER_SIZE);
        sqlRepoContext.setQuerydslSqlListener(queryRecorder);

        if (System.getProperty(SYSTEM_PROPERTY_SKIP_DB_CLEAR) == null) {
            clearDatabase();
        }
    }

    /**
     * Database cleanup for Sqale tests only.
     * Check TestSqaleRepositoryBeanConfig.clearDatabase(SqaleRepoContext) for integration tests.
     */
    private void clearDatabase() {
        try (JdbcSession jdbcSession = startTransaction()) {
            // object delete cascades to sub-rows of the "object aggregate"

            jdbcSession.executeStatement("TRUNCATE m_object CASCADE;");
            // truncate does not run ON DELETE trigger, many refs/container tables are not cleaned
            jdbcSession.executeStatement("TRUNCATE m_object_oid CASCADE;");
            // but after truncating m_object_oid it cleans all the tables

            // audit is cleaned on-demand using clearAudit()

            /*
            Truncates are much faster than this delete probably because it works row by row:
            long count = jdbcSession.newDelete(QObjectMapping.INSTANCE.defaultAlias()).execute();
            display("Deleted " + count + " objects from DB");
            */
            jdbcSession.commit();
            display("OBJECT tables cleared");
        }

        // this is "suite" scope code, but @BeforeSuite can't use injected fields
        if (!cacheTablesCleared) {
            try (JdbcSession jdbcSession = startTransaction()) {
                // URI cache must work even when default relation ID is not 0, so we can wipe it all.
                jdbcSession.executeStatement("TRUNCATE m_uri CASCADE;");
                jdbcSession.executeStatement("TRUNCATE m_ext_item CASCADE;");
                jdbcSession.commit();
            }

            sqlRepoContext.clearCaches(); // uses its own transaction

            // It would work with URI cache cleared before every class, but that's not
            // how midPoint will work either.
            cacheTablesCleared = true;
            display("URI cache and Extension item catalog tables cleared");
        }
    }

    // Called on demand
    public void clearAudit() {
        try (JdbcSession jdbcSession = startTransaction()) {
            jdbcSession.executeStatement("TRUNCATE ma_audit_event CASCADE;");
            jdbcSession.commit();
            display("AUDIT tables cleared");
        }
    }

    /**
     * Returns default query instance (alias) for the specified query type.
     * Don't use this for multi-table types like references, use something like this instead:
     *
     * ----
     * QObjectReference r = QObjectReferenceMapping.INSTANCE_PROJECTION.defaultAlias();
     * ----
     */
    protected <R, Q extends FlexibleRelationalPathBase<R>> Q aliasFor(Class<Q> entityPath) {
        return sqlRepoContext.getMappingByQueryType(entityPath).defaultAlias();
    }

    /** Returns new named query instance (alias) for the specified query type. */
    protected <R, Q extends FlexibleRelationalPathBase<R>> Q aliasFor(
            Class<Q> entityPath, String name) {
        return sqlRepoContext.getMappingByQueryType(entityPath).newAlias(name);
    }

    protected <R, Q extends FlexibleRelationalPathBase<R>> void assertCount(
            Class<Q> queryType, long expectedCount) {
        assertCount(aliasFor(queryType), expectedCount);
    }

    protected <R, Q extends FlexibleRelationalPathBase<R>> void assertCount(
            Q path, long expectedCount, Predicate... conditions) {
        assertThat(count(path, conditions))
                .as("Count for %s where %s", path, Arrays.toString(conditions))
                .isEqualTo(expectedCount);
    }

    protected <R, Q extends FlexibleRelationalPathBase<R>> long count(
            Class<Q> queryType, Predicate... conditions) {
        return count(aliasFor(queryType), conditions);
    }

    protected <R, Q extends FlexibleRelationalPathBase<R>> long count(
            Q path, Predicate... conditions) {
        try (JdbcSession jdbcSession = startReadOnlyTransaction()) {
            SQLQuery<?> query = jdbcSession.newQuery()
                    .from(path)
                    .where(conditions);
            return query.fetchCount();
        }
    }

    protected <R, Q extends FlexibleRelationalPathBase<R>> List<R> select(
            Class<Q> queryType, Predicate... conditions) {
        return select(aliasFor(queryType), conditions);
    }

    protected <R, Q extends FlexibleRelationalPathBase<R>> List<R> select(
            Q path, Predicate... conditions) {
        try (JdbcSession jdbcSession = startReadOnlyTransaction()) {
            return jdbcSession.newQuery()
                    .from(path)
                    .where(conditions)
                    .select(path)
                    .fetch();
        }
    }

    protected <R, Q extends FlexibleRelationalPathBase<R>> @NotNull R selectOne(
            Q path, Predicate... conditions) {
        R row = selectOneNullable(path, conditions);
        assertThat(row).isNotNull();
        return row;
    }

    protected <R, Q extends FlexibleRelationalPathBase<R>> @Nullable R selectOneNullable(
            Q path, Predicate... conditions) {
        try (JdbcSession jdbcSession = startReadOnlyTransaction()) {
            return jdbcSession.newQuery()
                    .from(path)
                    .where(conditions)
                    .select(path)
                    .fetchOne();
        }
    }

    protected <R extends MObject, Q extends QObject<R>> @NotNull R selectObjectByOid(
            Class<Q> queryType, String oid) {
        return selectObjectByOid(queryType, UUID.fromString(oid));
    }

    protected <R extends MObject, Q extends QObject<R>> @NotNull R selectObjectByOid(
            Class<Q> queryType, UUID oid) {
        R row = selectNullableObjectByOid(queryType, oid);
        assertThat(row).isNotNull();
        return row;
    }

    protected <R extends MObject, Q extends QObject<R>> @Nullable R selectNullableObjectByOid(
            Class<Q> queryType, String oid) {
        return selectNullableObjectByOid(queryType, UUID.fromString(oid));
    }

    protected <R extends MObject, Q extends QObject<R>> @Nullable R selectNullableObjectByOid(
            Class<Q> queryType, UUID oid) {
        Q path = aliasFor(queryType);
        return selectOneNullable(path, path.oid.eq(oid));
    }

    protected String cachedUriById(Integer uriId) {
        QUri qUri = QUri.DEFAULT;
        return selectOne(qUri, qUri.id.eq(uriId)).uri;
    }

    protected Integer cachedUriId(QName qName) {
        return cachedUriId(QNameUtil.qNameToUri(qName));
    }

    protected Integer cachedUriId(String uri) {
        QUri qUri = QUri.DEFAULT;
        return selectOne(qUri, qUri.uri.eq(uri)).id;
    }

    protected void assertCachedUri(Integer uriId, QName qName) {
        assertCachedUri(uriId, QNameUtil.qNameToUri(qName));
    }

    protected void assertCachedUri(Integer uriId, String uri) {
        assertThat(uriId)
                .withFailMessage("Unexpected NULL ID for cached URI %s", uri)
                .isNotNull();
        assertThat(cachedUriById(uriId)).isEqualTo(uri);
    }

    /** Resolves multiple URI IDs to the URI strings. */
    protected String[] resolveCachedUriIds(Integer[] uriIds) {
        return Stream.of(uriIds)
                .map(id -> cachedUriById(id))
                .toArray(String[]::new);
    }

    /** Sets original and normalized name for provided {@link MObject}. */
    protected void setName(MObject object, String origName) {
        object.nameOrig = origName;
        object.nameNorm = prismContext.getDefaultPolyStringNormalizer().normalize(origName);
    }

    protected java.util.function.Predicate<? super Referencable> refMatcher(
            UUID targetOid, QName relation) {
        return ref -> ref.getOid().equals(targetOid.toString())
                && ref.getRelation().equals(relation);
    }

    protected java.util.function.Predicate<? super MReference> refRowMatcher(
            UUID targetOid, QName relation) {
        return ref -> ref.targetOid.equals(targetOid)
                && cachedUriById(ref.relationId).equals(QNameUtil.qNameToUri(relation));
    }

    protected java.util.function.Predicate<? super MReference> refRowMatcher(
            UUID targetOid, MObjectType targetType, QName relation) {
        return ref -> ref.targetOid.equals(targetOid)
                && ref.targetType == targetType
                && cachedUriById(ref.relationId).equals(QNameUtil.qNameToUri(relation));
    }

    protected void assertSingleOperationRecorded(String opKind) {
        assertOperationRecordedCount(opKind, 1);
    }

    protected void assertOperationRecordedCount(String opKind, int count) {
        Map<String, OperationPerformanceInformation> pmAllData =
                getPerformanceMonitor()
                        .getGlobalPerformanceInformation().getAllData();
        OperationPerformanceInformation operationInfo = pmAllData.get(opKind);
        if (count != 0) {
            assertThat(operationInfo)
                    .withFailMessage("OperationPerformanceInformation for opKind '%s' is missing!", opKind)
                    .isNotNull();
            assertThat(operationInfo.getInvocationCount()).isEqualTo(count);
            assertThat(operationInfo.getExecutionCount()).isEqualTo(count);
        } else {
            assertThat(operationInfo).isNull();
        }
    }

    /** Creates a reference with specified type and default relation. */
    protected PrismReferenceValue ref(String targetOid, QName targetType) {
        return ref(targetOid, targetType, null);
    }

    /** Creates a reference with specified target type and relation. */
    protected PrismReferenceValue ref(String targetOid, QName targetType, QName relation) {
        return prismContext.itemFactory()
                .createReferenceValue(targetOid, targetType).relation(relation);
    }

    // region extension support
    @SafeVarargs
    protected final <V> void addExtensionValue(
            Containerable extContainer, String itemName, V... values) throws SchemaException {
        PrismContainerValue<?> pcv = extContainer.asPrismContainerValue();
        ItemDefinition<?> itemDefinition =
                pcv.getDefinition().findItemDefinition(new ItemName(itemName));
        assertThat(itemDefinition)
                .withFailMessage("No definition found for item name '%s' in %s", itemName, pcv)
                .isNotNull();
        if (itemDefinition instanceof PrismReferenceDefinition) {
            PrismReference ref = (PrismReference) itemDefinition.instantiate();
            for (V value : values) {
                ref.add(value instanceof PrismReferenceValue
                        ? (PrismReferenceValue) value
                        : ((Referencable) value).asReferenceValue());
            }
            pcv.add(ref);
        } else {
            //noinspection unchecked
            PrismProperty<V> property = (PrismProperty<V>) itemDefinition.instantiate();
            property.setRealValues(values);
            pcv.add(property);
        }
    }

    protected String extensionKey(Containerable extContainer, String itemName) {
        return extKey(extContainer, itemName, MExtItemHolderType.EXTENSION);
    }

    /** Returns extension item key (from m_ext_item table) for the specified shadow attribute. */
    protected String shadowAttributeKey(Containerable extContainer, String itemName) {
        return extKey(extContainer, itemName, MExtItemHolderType.ATTRIBUTES);
    }

    @NotNull
    private String extKey(Containerable extContainer, String itemName, MExtItemHolderType holder) {
        PrismContainerValue<?> pcv = extContainer.asPrismContainerValue();
        ItemDefinition<?> def = pcv.getDefinition().findItemDefinition(new ItemName(itemName));
        MExtItem.Key key = MExtItem.keyFrom(def, holder);
        try (JdbcSession jdbcSession = startReadOnlyTransaction()) {
            QExtItem ei = QExtItem.DEFAULT;
            return jdbcSession.newQuery()
                    .from(ei)
                    .where(ei.itemName.eq(key.itemName)
                            .and(ei.valueType.eq(key.valueType))
                            .and(ei.holderType.eq(key.holderType))
                            .and(ei.cardinality.eq(key.cardinality)))
                    .select(ei.id)
                    .fetchFirst()
                    .toString();
        }
    }

    /** Returns performance monitor from repository service, override to get the one from audit. */
    protected SqlPerformanceMonitorImpl getPerformanceMonitor() {
        return repositoryService.getPerformanceMonitor();
    }

    protected void clearPerformanceMonitor() {
        SqlPerformanceMonitorImpl pm = getPerformanceMonitor();
        pm.clearGlobalPerformanceInformation();
        assertThat(pm.getGlobalPerformanceInformation().getAllData()).isEmpty();
    }

    protected void refreshOrgClosureForce() {
        try (JdbcSession jdbcSession = startTransaction()) {
            jdbcSession.executeStatement("CALL m_refresh_org_closure(true)");
            jdbcSession.commit();
        }
    }

    /** Low-level shortcut for {@link SqaleRepositoryService#searchObjects}, no checks. */
    @SafeVarargs
    @NotNull
    protected final <T extends ObjectType> SearchResultList<T> repositorySearchObjects(
            @NotNull Class<T> type,
            ObjectQuery query,
            OperationResult operationResult,
            SelectorOptions<GetOperationOptions>... selectorOptions)
            throws SchemaException {
        return repositoryService.searchObjects(
                        type,
                        query,
                        selectorOptions != null && selectorOptions.length != 0
                                ? List.of(selectorOptions) : null,
                        operationResult)
                .map(p -> p.asObjectable());
    }

    /** Search objects using Axiom query language. */
    @SafeVarargs
    @NotNull
    protected final <T extends ObjectType> SearchResultList<T> searchObjects(
            @NotNull Class<T> type,
            String query,
            OperationResult operationResult,
            SelectorOptions<GetOperationOptions>... selectorOptions)
            throws SchemaException {
        ObjectFilter objectFilter = prismContext.createQueryParser().parseQuery(type, query);
        ObjectQuery objectQuery = prismContext.queryFactory().createQuery(objectFilter);
        return searchObjects(type, objectQuery, operationResult, selectorOptions);
    }

    protected SearchResultList<UserType> searchUsersTest(String description,
            Function<S_FilterEntryOrEmpty, S_FilterExit> filter, String... expectedOids)
            throws SchemaException {
        return searchObjectTest(description, UserType.class, filter, expectedOids);
    }

    protected <T extends ObjectType> SearchResultList<T> searchObjectTest(
            String description, Class<T> type,
            Function<S_FilterEntryOrEmpty, S_FilterExit> filter, String... expectedOids)
            throws SchemaException {
        String typeName = type.getSimpleName().replaceAll("Type$", "").toLowerCase();
        when("searching for " + typeName + "(s) " + description);
        OperationResult operationResult = createOperationResult();
        SearchResultList<T> result = searchObjects(type,
                filter.apply(prismContext.queryFor(type)).build(),
                operationResult);

        then(typeName + "(s) " + description + " are returned");
        assertThatOperationResult(operationResult).isSuccess();
        assertThat(result)
                .extracting(o -> o.getOid())
                .containsExactlyInAnyOrder(expectedOids);
        return result;
    }

    /** Search objects using {@link ObjectQuery}, including various logs and sanity checks. */
    @SafeVarargs
    @NotNull
    protected final <T extends ObjectType> SearchResultList<T> searchObjects(
            @NotNull Class<T> type,
            ObjectQuery query,
            OperationResult operationResult,
            SelectorOptions<GetOperationOptions>... selectorOptions)
            throws SchemaException {
        display("QUERY: " + query);
        QueryType queryType = prismContext.getQueryConverter().createQueryType(query);
        String serializedQuery = prismContext.xmlSerializer().serializeAnyData(
                queryType, SchemaConstants.MODEL_EXTENSION_OBJECT_QUERY);
        display("Serialized QUERY: " + serializedQuery);

        // sanity check if it's re-parsable
        assertThat(prismContext.parserFor(serializedQuery).parseRealValue(QueryType.class))
                .isNotNull();
        return repositorySearchObjects(type, query, operationResult, selectorOptions);
    }

    protected <T extends Containerable> SearchResultList<T> searchContainerTest(
            String description, Class<T> type, Function<S_FilterEntryOrEmpty, S_FilterExit> filter)
            throws SchemaException {
        String typeName = type.getSimpleName().replaceAll("Type$", "").toLowerCase();
        when("searching for " + typeName + "(s) " + description);
        OperationResult operationResult = createOperationResult();
        SearchResultList<T> result = searchContainers(type,
                filter.apply(prismContext.queryFor(type)).build(),
                operationResult);

        then(typeName + "(s) " + description + " are returned");
        assertThatOperationResult(operationResult).isSuccess();
        return result;
    }

    /** Search containers using {@link ObjectQuery}. */
    @SafeVarargs
    @NotNull
    private <T extends Containerable> SearchResultList<T> searchContainers(
            @NotNull Class<T> type,
            ObjectQuery query,
            OperationResult operationResult,
            SelectorOptions<GetOperationOptions>... selectorOptions)
            throws SchemaException {
        display("QUERY: " + query);
        QueryType queryType = prismContext.getQueryConverter().createQueryType(query);
        String serializedQuery = prismContext.xmlSerializer().serializeAnyData(
                queryType, SchemaConstants.MODEL_EXTENSION_OBJECT_QUERY);
        display("Serialized QUERY: " + serializedQuery);

        // sanity check if it's re-parsable
        assertThat(prismContext.parserFor(serializedQuery).parseRealValue(QueryType.class))
                .isNotNull();
        return repositoryService.searchContainers(
                type,
                query,
                selectorOptions != null && selectorOptions.length != 0
                        ? List.of(selectorOptions) : null,
                operationResult);
    }

    /** Parses object from byte array form and returns its real value (not Prism structure). */
    @NotNull
    protected <T> T parseFullObject(byte[] fullObject) {
        try {
            return prismContext.parserFor(new String(fullObject, StandardCharsets.UTF_8))
                    .parseRealValue();
        } catch (SchemaException e) {
            // to support lambdas
            throw new RuntimeException(e);
        }
    }

    protected @NotNull Collection<SelectorOptions<GetOperationOptions>> retrieveGetOptions(ItemName... paths) {
        GetOperationOptionsBuilder options = SchemaService.get().getOperationOptionsBuilder();
        for (ItemName path : paths) {
            options.item(path).retrieve();
        }
        return options.build();
    }

    @NotNull
    protected <C extends Containerable> String serializeFullObject(C containerable) throws SchemaException {
        return prismContext.serializerFor(repositoryConfiguration.getFullObjectFormat())
                .options(SerializationOptions
                        .createSerializeReferenceNamesForNullOids()
                        .skipIndexOnly(true)
                        .skipTransient(true)
                        .skipWhitespaces(true))
                .serialize(containerable.asPrismContainerValue());
    }

    protected void compactOperationResult(OperationResult operationResult) {
        operationResult.computeStatus();
        operationResult.cleanupResultDeeply();
        operationResult.setSummarizeSuccesses(true);
        operationResult.summarize();
    }

    /**
     * Helper to make setting shadow attributes easier.
     *
     * * Creates mutable container definition for shadow attributes.
     * * Initializes PC+PCV for shadow attributes in the provided shadow object.
     * * Using {@link #set(QName, QName, int, int, Object...)} one can "define" new attribute
     * and set it in the same step.
     *
     * This is not a builder, just a stateless wrapper around the shadow object and each set
     * has immediate effect.
     */
    public class ShadowAttributesHelper {

        private final ShadowAttributesType attributesContainer;
        private final MutablePrismContainerDefinition<Containerable> attrsDefinition;

        /**
         * Creates the attribute helper for the shadow, adding attributes container to the shadow.
         * The container can be later obtained by {@link #attributesContainer()} if/when needed.
         */
        public ShadowAttributesHelper(ShadowType object) throws SchemaException {
            attributesContainer = new ShadowAttributesType();
            // let's create the container+PCV inside the shadow object
            object.attributes(attributesContainer);

            MutableComplexTypeDefinition ctd = prismContext.definitionFactory()
                    .createComplexTypeDefinition(ShadowAttributesType.COMPLEX_TYPE);
            //noinspection unchecked
            attrsDefinition = (MutablePrismContainerDefinition<Containerable>)
                    prismContext.definitionFactory()
                            .createContainerDefinition(ShadowType.F_ATTRIBUTES, ctd);
            object.asPrismObject().findContainer(ShadowType.F_ATTRIBUTES)
                    .applyDefinition(attrsDefinition, true);
        }

        /** Creates definition for attribute (first parameters) and sets the value(s) (vararg). */
        @SafeVarargs
        public final <V> ShadowAttributesHelper set(
                QName attributeName, QName type, int minOccurrence, int maxOccurrence,
                V... values) throws SchemaException {
            attrsDefinition.createPropertyDefinition(attributeName, type, minOccurrence, maxOccurrence);
            addExtensionValue(attributesContainer, attributeName.getLocalPart(), values);
            return this;
        }

        /**
         * Simplified version of {@link #set(QName, QName, int, int, Object...)} method.
         * Uses 0 for minOccurrence and maxOccurrence is 1 if one or no value is provided,
         * otherwise it's set to -1.
         */
        @SafeVarargs
        public final <V> ShadowAttributesHelper set(
                QName attributeName, QName type, V... values) throws SchemaException {
            return set(attributeName, type, 0, values.length <= 1 ? 1 : -1, values);
        }

        /** Returns shadow attributes container likely needed later in the assert section. */
        public ShadowAttributesType attributesContainer() {
            return attributesContainer;
        }

        /** For tests searching by shadow attribute using {@code item(ItemPath, ItemDefinition}. */
        public ItemDefinition<?> getDefinition(ItemName attributeName) {
            return attrsDefinition.findItemDefinition(attributeName);
        }
    }
    // endregion

    protected JdbcSession startTransaction() {
        return sqlRepoContext.newJdbcSession().startTransaction();
    }

    protected JdbcSession startReadOnlyTransaction() {
        return sqlRepoContext.newJdbcSession().startReadOnlyTransaction();
    }
}
