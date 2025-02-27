/*
 * Copyright (c) 2018-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.test.asserter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.*;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.test.asserter.prism.PrismObjectAsserter;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("UnusedReturnValue")
public class ShadowAsserter<RA> extends PrismObjectAsserter<ShadowType, RA> {

    public ShadowAsserter(PrismObject<ShadowType> shadow) {
        super(shadow);
    }

    public ShadowAsserter(PrismObject<ShadowType> shadow, String details) {
        super(shadow, details);
    }

    public ShadowAsserter(PrismObject<ShadowType> shadow, RA returnAsserter, String details) {
        super(shadow, returnAsserter, details);
    }

    public static ShadowAsserter<Void> forShadow(PrismObject<ShadowType> shadow) {
        return new ShadowAsserter<>(shadow);
    }

    public static ShadowAsserter<Void> forShadow(PrismObject<ShadowType> shadow, String details) {
        return new ShadowAsserter<>(shadow, details);
    }

    @Override
    public ShadowAsserter<RA> assertOid() {
        super.assertOid();
        return this;
    }

    @Override
    public ShadowAsserter<RA> assertNoOid() {
        super.assertNoOid();
        return this;
    }

    @Override
    public ShadowAsserter<RA> assertOid(String expected) {
        super.assertOid(expected);
        return this;
    }

    @Override
    public ShadowAsserter<RA> assertName() {
        super.assertName();
        return this;
    }

    @Override
    public ShadowAsserter<RA> assertName(String expectedOrig) {
        super.assertName(expectedOrig);
        return this;
    }

    @Override
    public ShadowAsserter<RA> assertLifecycleState(String expected) {
        super.assertLifecycleState(expected);
        return this;
    }

    @Override
    public ShadowAsserter<RA> assertActiveLifecycleState() {
        super.assertActiveLifecycleState();
        return this;
    }

    public ShadowAsserter<RA> assertObjectClass() {
        assertNotNull("No objectClass in " + desc(), getObject().asObjectable().getObjectClass());
        return this;
    }

    public ShadowAsserter<RA> assertObjectClass(QName expected) {
        PrismAsserts.assertMatchesQName("Wrong objectClass in " + desc(), expected, getObject().asObjectable().getObjectClass());
        return this;
    }

    public ShadowAsserter<RA> assertKind() {
        assertNotNull("No kind in " + desc(), getObject().asObjectable().getKind());
        return this;
    }

    public ShadowAsserter<RA> assertKind(ShadowKindType expected) {
        assertEquals("Wrong kind in " + desc(), expected, getObject().asObjectable().getKind());
        return this;
    }

    public ShadowAsserter<RA> assertIntent(String expected) {
        assertEquals("Wrong intent in " + desc(), expected, getObject().asObjectable().getIntent());
        return this;
    }

    public ShadowAsserter<RA> assertTag(String expected) {
        assertEquals("Wrong tag in " + desc(), expected, getObject().asObjectable().getTag());
        return this;
    }

    public ShadowAsserter<RA> assertTagIsOid() {
        assertEquals("Wrong tag in " + desc(), getObject().getOid(), getObject().asObjectable().getTag());
        return this;
    }

    public ShadowAsserter<RA> assertPrimaryIdentifierValue(String expected) {
        assertEquals("Wrong primaryIdentifierValue in " + desc(), expected, getObject().asObjectable().getPrimaryIdentifierValue());
        return this;
    }

    public ShadowAsserter<RA> assertNoPrimaryIdentifierValue() {
        assertNull("Unexpected primaryIdentifierValue in " + desc(), getObject().asObjectable().getPrimaryIdentifierValue());
        return this;
    }

    public ShadowAsserter<RA> assertIteration(Integer expected) {
        assertEquals("Wrong iteration in " + desc(), expected, getObject().asObjectable().getIteration());
        return this;
    }

    public ShadowAsserter<RA> assertIterationToken(String expected) {
        assertEquals("Wrong iteration token in " + desc(),
                expected, getObject().asObjectable().getIterationToken());
        return this;
    }

    public ShadowAsserter<RA> assertSynchronizationSituation(SynchronizationSituationType expected) {
        assertEquals("Wrong synchronization situation in " + desc(),
                expected, getObject().asObjectable().getSynchronizationSituation());
        return this;
    }

    public ShadowAsserter<RA> assertAdministrativeStatus(ActivationStatusType expected) {
        ActivationType activation = getActivation();
        if (activation == null) {
            if (expected == null) {
                return this;
            } else {
                fail("No activation in " + desc());
            }
        }
        assertEquals("Wrong activation administrativeStatus in " + desc(),
                expected, activation.getAdministrativeStatus());
        return this;
    }

    public ShadowAsserter<RA> assertResource(String expectedResourceOid) {
        ObjectReferenceType resourceRef = getObject().asObjectable().getResourceRef();
        if (resourceRef == null) {
            fail("No resourceRef in " + desc());
        }
        assertEquals("Wrong resourceRef OID in " + desc(), expectedResourceOid, resourceRef.getOid());
        return this;
    }

    private ActivationType getActivation() {
        return getObject().asObjectable().getActivation();
    }

    public ShadowAsserter<RA> assertBasicRepoProperties() {
        assertOid();
        assertName();
        assertObjectClass();
        attributes().assertAny();
        return this;
    }

    public ShadowAsserter<RA> assertDead() {
        assertIsDead(true);
        return this;
    }

    public ShadowAsserter<RA> assertNotDead() {
        Boolean isDead = getObject().asObjectable().isDead();
        if (isDead != null && isDead) {
            fail("Wrong isDead in " + desc() + ", expected null or false, but was true");
        }
        return this;
    }

    public ShadowAsserter<RA> assertIsDead(Boolean expected) {
        assertEquals("Wrong isDead in " + desc(), expected, getObject().asObjectable().isDead());
        assertNoPrimaryIdentifierValue();
        return this;
    }

    public ShadowAsserter<RA> assertIsExists() {
        Boolean isExists = getObject().asObjectable().isExists();
        if (isExists != null && !isExists) {
            fail("Wrong isExists in " + desc() + ", expected null or true, but was false");
        }
        return this;
    }

    public ShadowAsserter<RA> assertIsNotExists() {
        assertIsExists(false);
        return this;
    }

    public ShadowAsserter<RA> assertIsExists(Boolean expected) {
        assertEquals("Wrong isExists in " + desc(), expected, getObject().asObjectable().isExists());
        return this;
    }

    public ShadowAsserter<RA> assertConception() {
        assertNotDead();
        assertIsNotExists();
        return this;
    }

    // We cannot really distinguish gestation and life now. But maybe later.
    public ShadowAsserter<RA> assertGestation() {
        assertNotDead();
        assertIsExists();
        return this;
    }

    public ShadowAsserter<RA> assertLive() {
        assertNotDead();
        assertIsExists();
        return this;
    }

    public ShadowAsserter<RA> assertTombstone() {
        assertDead();
        assertIsNotExists();
        return this;
    }

    // We cannot really distinguish corpse and tombstone now. But maybe later.
    public ShadowAsserter<RA> assertCorpse() {
        assertDead();
        assertIsNotExists();
        return this;
    }

    public PendingOperationsAsserter<RA> pendingOperations() {
        PendingOperationsAsserter<RA> asserter = new PendingOperationsAsserter<>(this, getDetails());
        copySetupTo(asserter);
        return asserter;
    }

    public ShadowAsserter<RA> hasUnfinishedPendingOperations() {
        pendingOperations()
                .assertUnfinishedOperation();
        return this;
    }

    public ShadowAttributesAsserter<RA> attributes() {
        ShadowAttributesAsserter<RA> asserter = new ShadowAttributesAsserter<>(this, getDetails());
        copySetupTo(asserter);
        return asserter;
    }

    public ShadowAsserter<RA> assertNoAttributes() {
        assertNull("Unexpected attributes in " + desc(), getObject().findContainer(ShadowType.F_ATTRIBUTES));
        return this;
    }

    public ShadowAssociationsAsserter<RA> associations() {
        ShadowAssociationsAsserter<RA> asserter = new ShadowAssociationsAsserter<>(this, getDetails());
        copySetupTo(asserter);
        return asserter;
    }

    public ShadowAsserter<RA> assertNoAssociations() {
        assertNull("Unexpected associations in " + desc(), getObject().findContainer(ShadowType.F_ASSOCIATION));
        return this;
    }

    public ShadowAsserter<RA> assertNoLegacyConsistency() {
        // Nothing to do. Those are gone in midPoint 4.0.
        return this;
    }

    public ShadowAsserter<RA> display() {
        super.display();
        return this;
    }

    public ShadowAsserter<RA> display(String message) {
        super.display(message);
        return this;
    }

    public ShadowAsserter<RA> assertOidDifferentThan(String oid) {
        super.assertOidDifferentThan(oid);
        return this;
    }

    public ShadowAsserter<RA> assertNoPassword() {
        PrismProperty<PolyStringType> passValProp = getPasswordValueProperty();
        assertNull("Unexpected password value property in " + desc() + ": " + passValProp, passValProp);
        return this;
    }

    private PrismProperty<PolyStringType> getPasswordValueProperty() {
        return getObject().findProperty(SchemaConstants.PATH_PASSWORD_VALUE);
    }

    @Override
    public ShadowAsserter<RA> assertNoTrigger() {
        super.assertNoTrigger();
        return this;
    }

    public ShadowAsserter<RA> assertMatchReferenceId(String id) throws SchemaException {
        assertThat(getIdMatchCorrelatorStateRequired().getReferenceId())
                .as("referenceId")
                .isEqualTo(id);
        return this;
    }

    public ShadowAsserter<RA> assertHasMatchReferenceId() throws SchemaException {
        assertThat(getIdMatchCorrelatorStateRequired().getReferenceId())
                .as("referenceId")
                .isNotNull();
        return this;
    }

    public ShadowAsserter<RA> assertMatchRequestId(String id) throws SchemaException {
        assertThat(getIdMatchCorrelatorStateRequired().getMatchRequestId())
                .as("matchRequestId")
                .isEqualTo(id);
        return this;
    }

    public ShadowAsserter<RA> assertHasMatchRequestId() throws SchemaException {
        assertThat(getIdMatchCorrelatorStateRequired().getMatchRequestId())
                .as("matchRequestId")
                .isNotNull();
        return this;
    }

    private @NotNull ShadowCorrelationStateType getCorrelationStateRequired() {
        return Objects.requireNonNull(
                getObjectable().getCorrelation(), () -> "No correlation state in " + desc());
    }

    private @NotNull AbstractCorrelatorStateType getCorrelatorStateRequired() {
        return Objects.requireNonNull(
                getCorrelationStateRequired().getCorrelatorState(), () -> "No correlator state in " + desc());
    }

    private @NotNull IdMatchCorrelatorStateType getIdMatchCorrelatorStateRequired() throws SchemaException {
        return MiscUtil.castSafely(
                getCorrelatorStateRequired(), IdMatchCorrelatorStateType.class);
    }

    /**
     * Temporary: until correlation state asserter is implemented.
     */
    public ShadowAsserter<RA> assertCorrelationSituation(CorrelationSituationType expected) {
        assertThat(getCorrelationSituation()).as("correlation situation").isEqualTo(expected);
        return this;
    }

    private CorrelationSituationType getCorrelationSituation() {
        ShadowCorrelationStateType correlation = getObjectable().getCorrelation();
        return correlation != null ? correlation.getSituation() : null;
    }

    public ShadowAsserter<RA> assertPotentialOwnerOptions(int expected) {
        assertThat(getPotentialOwnerOptions())
                .as("potential owner options")
                .hasSize(expected);
        return this;
    }

    private List<ResourceObjectOwnerOptionType> getPotentialOwnerOptions() {
        ShadowCorrelationStateType state = getObjectable().getCorrelation();
        if (state == null || state.getOwnerOptions() == null) {
            return List.of();
        } else {
            return state.getOwnerOptions().getOption();
        }
    }
}
