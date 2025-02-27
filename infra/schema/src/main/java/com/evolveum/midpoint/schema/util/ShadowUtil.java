/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.schema.util;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.*;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.SchemaService;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.processor.*;
import com.evolveum.midpoint.schema.processor.ObjectFactory;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static com.evolveum.midpoint.util.MiscUtil.stateCheck;

/**
 * Methods that would belong to the ResourceObjectShadowType class but cannot go there
 * because of JAXB.
 *
 * @author Radovan Semancik
 */
public class ShadowUtil {

    private static final Trace LOGGER = TraceManager.getTrace(ShadowUtil.class);

    public static Collection<ResourceAttribute<?>> getPrimaryIdentifiers(ShadowType shadowType) {
        return getPrimaryIdentifiers(shadowType.asPrismObject());
    }

    public static Collection<ResourceAttribute<?>> getPrimaryIdentifiers(PrismObject<? extends ShadowType> shadow) {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        if (attributesContainer == null) {
            return null;
        }
        return attributesContainer.getPrimaryIdentifiers();
    }

    public static Collection<ResourceAttribute<?>> getSecondaryIdentifiers(ShadowType shadowType) {
        return getSecondaryIdentifiers(shadowType.asPrismObject());
    }

    public static Collection<ResourceAttribute<?>> getSecondaryIdentifiers(PrismObject<? extends ShadowType> shadow) {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        if (attributesContainer == null) {
            return null;
        }
        return attributesContainer.getSecondaryIdentifiers();
    }

    public static ResourceAttribute<String> getSecondaryIdentifier(PrismObject<? extends ShadowType> shadow) throws SchemaException {
        Collection<ResourceAttribute<?>> secondaryIdentifiers = getSecondaryIdentifiers(shadow);
        if (secondaryIdentifiers == null || secondaryIdentifiers.isEmpty()) {
            return null;
        }
        if (secondaryIdentifiers.size() > 1) {
            throw new SchemaException("Too many secondary identifiers in "+shadow+": "+secondaryIdentifiers);
        }
        return (ResourceAttribute<String>) secondaryIdentifiers.iterator().next();
    }

    public static Collection<ResourceAttribute<?>> getSecondaryIdentifiers(
            @Nullable Collection<? extends ResourceAttribute<?>> identifiers,
            @NotNull ResourceObjectDefinition objectClassDefinition) throws SchemaException {
        if (identifiers == null) {
            return null;
        }
        Collection<ResourceAttribute<?>> secondaryIdentifiers = new ArrayList<>();
        for (ResourceAttribute<?> identifier: identifiers) {
            if (objectClassDefinition.isSecondaryIdentifier(identifier.getElementName())) {
                secondaryIdentifiers.add(identifier);
            }
        }
        return secondaryIdentifiers;
    }

    public static String getSecondaryIdentifierRealValue(PrismObject<? extends ShadowType> shadow) throws SchemaException {
        ResourceAttribute<String> secondaryIdentifier = getSecondaryIdentifier(shadow);
        if (secondaryIdentifier == null) {
            return null;
        }
        return secondaryIdentifier.getRealValue();
    }

    public static Collection<ResourceAttribute<?>> getAllIdentifiers(PrismObject<? extends ShadowType> shadow) {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        if (attributesContainer == null) {
            return null;
        }
        return attributesContainer.getAllIdentifiers();
    }

    public static Collection<ResourceAttribute<?>> getAllIdentifiers(ShadowType shadow) {
        return getAllIdentifiers(shadow.asPrismObject());
    }

    public static ResourceAttribute<String> getNamingAttribute(ShadowType shadow){
        return getNamingAttribute(shadow.asPrismObject());
    }

    public static ResourceAttribute<String> getNamingAttribute(PrismObject<? extends ShadowType> shadow) {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        if (attributesContainer == null) {
            return null;
        }
        return attributesContainer.getNamingAttribute();
    }

    public static Collection<ResourceAttribute<?>> getAttributes(ShadowType shadowType) {
        return getAttributes(shadowType.asPrismObject());
    }

    // TODO what if there's no attributes container?
    public static Collection<ResourceAttribute<?>> getAttributes(PrismObject<? extends ShadowType> shadow) {
        return getAttributesContainer(shadow).getAttributes();
    }

    public static <T> ResourceAttribute<T> getAttribute(PrismObject<? extends ShadowType> shadow, QName attrName) {
        return getAttributesContainer(shadow).findAttribute(attrName);
    }

    public static ResourceAttributeContainer getAttributesContainer(ShadowType shadowType) {
        return getAttributesContainer(shadowType.asPrismObject());
    }

    public static ResourceAttributeContainer getAttributesContainer(PrismObject<? extends ShadowType> shadow) {
        return getAttributesContainer(shadow, ShadowType.F_ATTRIBUTES);
    }

    public static ResourceAttributeContainer getAttributesContainer(PrismObject<? extends ShadowType> shadow, QName containerName) {
        return getAttributesContainer(shadow.getValue(), containerName);
    }

    public static boolean isAttributesContainerRaw(PrismObject<? extends ShadowType> shadow) {
        PrismContainer<ShadowAttributesType> container = shadow.findContainer(ShadowType.F_ATTRIBUTES);
        return container != null && !(container instanceof ResourceAttributeContainer);
    }

    public static ResourceAttributeContainer getAttributesContainer(PrismContainerValue<?> cval, QName containerName) {
        PrismContainer attributesContainer = cval.findContainer(containerName);
        if (attributesContainer == null) {
            return null;
        }
        if (attributesContainer instanceof ResourceAttributeContainer) {
            return (ResourceAttributeContainer)attributesContainer;
        } else {
            throw new SystemException("Expected that <"+containerName.getLocalPart()+"> will be ResourceAttributeContainer but it is "+attributesContainer.getClass());
        }
    }

    public static ResourceAttributeContainer getOrCreateAttributesContainer(
            PrismObject<? extends ShadowType> shadow, ResourceObjectDefinition definition) {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        if (attributesContainer != null) {
            return attributesContainer;
        }
        ResourceAttributeContainer emptyContainer =
                ResourceAttributeContainer.createEmptyContainer(ShadowType.F_ATTRIBUTES, definition);
        try {
            shadow.add(emptyContainer);
        } catch (SchemaException e) {
            throw SystemException.unexpected(e);
        }
        return emptyContainer;
    }

    public static ResourceObjectClassDefinition getObjectClassDefinition(ShadowType shadow) {
        // TODO: maybe we can do something more intelligent here
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        return attributesContainer.getDefinition()
                .getComplexTypeDefinition()
                .getObjectClassDefinition();
    }

    public static String getResourceOid(ShadowType shadowType) {
        return getResourceOid(shadowType.asPrismObject());
    }

    public static @NotNull String getResourceOidRequired(@NotNull ShadowType shadow) {
        return java.util.Objects.requireNonNull(
                getResourceOid(shadow),
                () -> "No resource OID in " + shadow);
    }

    public static String getResourceOid(PrismObject<ShadowType> shadow) {
        PrismReference resourceRef = shadow.findReference(ShadowType.F_RESOURCE_REF);
        if (resourceRef == null) {
            return null;
        }
        return resourceRef.getOid();
    }

    public static PolyString getResourceName(ShadowType shadowType) {
        return getResourceName(shadowType.asPrismObject());
    }

    public static PolyString getResourceName(PrismObject<ShadowType> shadow) {
        PrismReference resourceRef = shadow.findReference(ShadowType.F_RESOURCE_REF);
        if (resourceRef == null) {
            return null;
        }
        return resourceRef.getTargetName();
    }

    public static String getSingleStringAttributeValue(ShadowType shadow, QName attrName) {
        return getSingleStringAttributeValue(shadow.asPrismObject(), attrName);
    }

    public static String getSingleStringAttributeValue(PrismObject<ShadowType> shadow, QName attrName) {
        PrismContainer<?> attributesContainer = shadow.findContainer(ShadowType.F_ATTRIBUTES);
        if (attributesContainer == null) {
            return null;
        }
        PrismProperty<String> attribute = attributesContainer.findProperty(ItemName.fromQName(attrName));
        if (attribute == null) {
            return null;
        }
        return attribute.getRealValue();
    }

    public static String getMultiStringAttributeValueAsSingle(ShadowType shadow, QName attrName) {
        return getMultiStringAttributeValueAsSingle(shadow.asPrismObject(), attrName);
    }


    private static String getMultiStringAttributeValueAsSingle(PrismObject<ShadowType> shadow, QName attrName) {
        PrismContainer<?> attributesContainer = shadow.findContainer(ShadowType.F_ATTRIBUTES);
        if (attributesContainer == null) {
            return null;
        }
        PrismProperty<String> attribute = attributesContainer.findProperty(ItemName.fromQName(attrName));
        if (attribute == null) {
            return null;
        }
        Collection<String> realValues = attribute.getRealValues();
        if (realValues == null || realValues.isEmpty()) {
            return null;
        }
        if (realValues.size() > 1) {
            throw new IllegalStateException("More than one value in attribute "+attrName);
        }
        return realValues.iterator().next();
    }

    public static <T> List<T> getAttributeValues(ShadowType shadowType, QName attrName) {
        return getAttributeValues(shadowType.asPrismObject(), attrName);
    }

    public static <T> List<T> getAttributeValues(PrismObject<? extends ShadowType> shadow, QName attrName) {
        PrismContainer<?> attributesContainer = shadow.findContainer(ShadowType.F_ATTRIBUTES);
        if (attributesContainer == null || attributesContainer.isEmpty()) {
            return null;
        }
        PrismProperty<T> attr = attributesContainer.findProperty(ItemName.fromQName(attrName));
        if (attr == null) {
            return null;
        }
        List<T> values = new ArrayList<>();
        for (PrismPropertyValue<T> pval : attr.getValues()) {
            values.add(pval.getValue());
        }
        if (values.isEmpty()) {
            return null;
        }
        return values;
    }

    public static <T> T getAttributeValue(ShadowType shadowType, QName attrName) throws SchemaException {
        return (T) getAttributeValue(shadowType.asPrismObject(), attrName);
    }

    public static <T> T getAttributeValue(PrismObject<? extends ShadowType> shadow, QName attrName) throws SchemaException {
        Collection<T> values = getAttributeValues(shadow, attrName);
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new SchemaException("Attempt to get single value from multi-valued attribute "+attrName);
        }
        return values.iterator().next();
    }

    public static void setPassword(ShadowType shadowType, ProtectedStringType password) {
        CredentialsType credentialsType = shadowType.getCredentials();
        if (credentialsType == null) {
            credentialsType = new CredentialsType();
            shadowType.setCredentials(credentialsType);
        }
        PasswordType passwordType = credentialsType.getPassword();
        if (passwordType == null) {
            passwordType = new PasswordType();
            credentialsType.setPassword(passwordType);
        }
        passwordType.setValue(password);
    }

    public static void setPasswordIncomplete(ShadowType shadowType) throws SchemaException {
        CredentialsType credentialsType = shadowType.getCredentials();
        if (credentialsType == null) {
            credentialsType = new CredentialsType();
            shadowType.setCredentials(credentialsType);
        }
        PasswordType passwordType = credentialsType.getPassword();
        if (passwordType == null) {
            passwordType = new PasswordType();
            credentialsType.setPassword(passwordType);
        }
        PrismContainerValue<PasswordType> passwordContainer = passwordType.asPrismContainerValue();
        PrismProperty<ProtectedStringType> valueProperty = passwordContainer.findOrCreateProperty(PasswordType.F_VALUE);
        valueProperty.setIncomplete(true);
    }

    public static ActivationType getOrCreateActivation(ShadowType shadowType) {
        ActivationType activation = shadowType.getActivation();
        if (activation == null) {
            activation = new ActivationType();
            shadowType.setActivation(activation);
        }
        return activation;
    }

    /**
     * This is not supposed to be used in production code! It is just for the tests.
     */
    public static void applyResourceSchema(PrismObject<? extends ShadowType> shadow,
            ResourceSchema resourceSchema) throws SchemaException {
        ShadowType shadowType = shadow.asObjectable();
        QName objectClass = shadowType.getObjectClass();
        ResourceObjectDefinition objectClassDefinition = resourceSchema.findDefinitionForObjectClass(objectClass);
        applyObjectDefinition(shadow, objectClassDefinition);
    }

    private static void applyObjectDefinition(PrismObject<? extends ShadowType> shadow,
            ResourceObjectDefinition objectClassDefinition) throws SchemaException {
        PrismContainer<?> attributesContainer = shadow.findContainer(ShadowType.F_ATTRIBUTES);
        ResourceAttributeContainerDefinition racDef = ObjectFactory.createResourceAttributeContainerDefinition(
                ShadowType.F_ATTRIBUTES, objectClassDefinition);
        attributesContainer.applyDefinition((PrismContainerDefinition) racDef, true);
    }

    public static PrismObjectDefinition<ShadowType> applyObjectDefinition(PrismObjectDefinition<ShadowType> shadowDefinition,
            ResourceObjectDefinition objectClassDefinition) throws SchemaException {
        return shadowDefinition.cloneWithReplacedDefinition(ShadowType.F_ATTRIBUTES,
                objectClassDefinition.toResourceAttributeContainerDefinition());
    }

    public static String getIntent(PrismObject<ShadowType> shadow) {
        return shadow != null ? getIntent(shadow.asObjectable()) : null;
    }

    /**
     * Returns intent from the shadow. Backwards compatible with older accountType. May also adjust for default
     * intent if necessary.
     */
    @Contract("null -> null")
    public static String getIntent(ShadowType shadow) {
        if (shadow == null) {
            return null;
        }
        return shadow.getIntent();
    }

    public static ShadowKindType getKind(PrismObject<ShadowType> shadow) {
        return shadow != null ? getKind(shadow.asObjectable()) : null;
    }

    @Contract("!null -> !null; null -> null")
    public static ShadowKindType getKind(ShadowType shadow) {
        if (shadow == null) {
            return null;
        }
        return ObjectUtils.defaultIfNull(shadow.getKind(), ShadowKindType.ACCOUNT);
    }

    public static <T> Collection<T> getAttributeValues(ShadowType shadow, QName attributeQname, Class<T> type) {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        if (attributesContainer == null) {
            return null;
        }
        ResourceAttribute<T> attribute = attributesContainer.findAttribute(attributeQname);
        if (attribute == null) {
            return null;
        }
        return attribute.getRealValues(type);
    }

    public static ItemName getAttributeName(ItemPath attributePath, String message) throws SchemaException {
        if (attributePath == null || attributePath.isEmpty()) {
            return null;
        }
        Object firstPathSegment = attributePath.first();
        if (!ItemPath.isName(firstPathSegment)) {
            throw new SchemaException(message + ": first path segment is not a name segment");
        }
        ItemName firstName = ItemPath.toName(firstPathSegment);
        if (!QNameUtil.match(ShadowType.F_ATTRIBUTES, firstName)) {
            throw new SchemaException(message + ": first path segment is not "+ShadowType.F_ATTRIBUTES);
        }
        if (attributePath.size() < 1) {
            throw new SchemaException(message + ": path too short ("+attributePath.size()+" segments)");
        }
        if (attributePath.size() > 2) {
            throw new SchemaException(message + ": path too long ("+attributePath.size()+" segments)");
        }
        Object secondPathSegment = attributePath.rest().first();
        if (!ItemPath.isName(secondPathSegment)) {
            throw new SchemaException(message + ": second path segment is not a name segment");
        }
        return ItemPath.toName(secondPathSegment);
    }

    public static void checkConsistence(PrismObject<? extends ShadowType> shadow, String desc) {
        PrismReference resourceRef = shadow.findReference(ShadowType.F_RESOURCE_REF);
        if (resourceRef == null) {
            throw new IllegalStateException("No resourceRef in "+shadow+" in "+desc);
        }
        if (StringUtils.isBlank(resourceRef.getOid())) {
            throw new IllegalStateException("Null or empty OID in resourceRef in "+desc);
        }
        ShadowType shadowType = shadow.asObjectable();
        if (shadowType.getObjectClass() == null) {
            throw new IllegalStateException("Null objectClass in "+desc);
        }
        PrismContainer<ShadowAttributesType> attributesContainer = shadow.findContainer(ShadowType.F_ATTRIBUTES);
        if (attributesContainer != null) {
            if (!(attributesContainer instanceof ResourceAttributeContainer)) {
                throw new IllegalStateException("The attributes element expected to be ResourceAttributeContainer but it is "
                        +attributesContainer.getClass()+" instead in "+desc);
            }
            checkConsistency(attributesContainer.getDefinition(), " container definition in "+desc);
        }

        PrismContainerDefinition<ShadowAttributesType> attributesDefinition =
                shadow.getDefinition().findContainerDefinition((ItemPath) ShadowType.F_ATTRIBUTES);
        checkConsistency(attributesDefinition, " object definition in "+desc);
    }

    public static void checkConsistency(PrismContainerDefinition<ShadowAttributesType> attributesDefinition, String desc) {
        if (attributesDefinition == null) {
            throw new IllegalStateException("No definition for <attributes> in "+desc);
        }
        if (!(attributesDefinition instanceof ResourceAttributeContainerDefinition)) {
            throw new IllegalStateException("The attributes element definition expected to be ResourceAttributeContainerDefinition but it is "
                    +attributesDefinition.getClass()+" instead in "+desc);
        }
    }

    // TODO is this correct?
    public static boolean isAccount(ShadowType shadowType) {
        if (shadowType.getKind() != null) {
            return shadowType.getKind() == ShadowKindType.ACCOUNT;
        } else {
            return true;        // ???
        }
    }

    public static boolean isProtected(@Nullable ShadowType shadow) {
        return shadow != null && Boolean.TRUE.equals(shadow.isProtectedObject());
    }

    public static boolean isProtected(PrismObject<? extends ShadowType> shadow) {
        return isProtected(
                PrismObject.asObjectable(shadow));
    }

    public static boolean isDead(ShadowType shadow) {
        Boolean dead = shadow.isDead();
        return dead != null && dead;
    }

    public static boolean isDead(PrismObject<ShadowType> shadow) {
        return isDead(shadow.asObjectable());
    }

    public static boolean isNotDead(PrismObject<ShadowType> shadow) {
        return !isDead(shadow);
    }

    public static boolean isNotDead(ShadowType shadow) {
        return !isDead(shadow);
    }

    public static boolean isDead(ObjectReferenceType projectionRef) {
        return !SchemaService.get().relationRegistry().isMember(projectionRef.getRelation());
    }

    public static boolean isNotDead(ObjectReferenceType projectionRef) {
        return !isDead(projectionRef);
    }

    public static boolean isExists(ShadowType shadow) {
        Boolean exists = shadow.isExists();
        return exists == null || exists;
    }

    public static boolean isExists(PrismObject<ShadowType> shadow) {
        return isExists(shadow.asObjectable());
    }

    public static boolean matches(ShadowType shadowType, String resourceOid, ShadowKindType kind, String intent) {
        if (shadowType == null) {
            return false;
        }
        if (!shadowHasResourceOid(shadowType, resourceOid)) {
            return false;
        }
        if (!MiscUtil.equals(kind, shadowType.getKind())) {
            return false;
        }
        if (intent == null) {
            return true;
        }
        return MiscUtil.equals(intent, shadowType.getIntent());
    }

    public static boolean matches(PrismObject<ShadowType> shadow, ResourceShadowDiscriminator discr) {
        return matches(shadow.asObjectable(), discr);
    }

    public static boolean matches(ShadowType shadowType, ResourceShadowDiscriminator discr) {
        if (shadowType == null) {
            return false;
        }
        if (discr == null) {
            return false; // shouldn't occur
        }
        if (!shadowHasResourceOid(shadowType, discr.getResourceOid())) {
            return false;
        }
        if (!MiscUtil.equals(discr.getKind(), shadowType.getKind())) {
            return false;
        }
        return ResourceShadowDiscriminator.equalsIntent(shadowType.getIntent(), discr.getIntent());
    }

    /**
     * Returns true if the shadow has non-null resource OID that matches given value.
     *
     * Neither one of the OIDs (in shadow nor in discriminator) should be null.
     * So we intentionally return false even if they are both null (and therefore equal).
     *
     * In reality such situations sometimes occur (see MID-6673). Maybe we should find
     * and fix the primary cause and then strictly require non-nullity of these OIDs here.
     * But not now.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean shadowHasResourceOid(ShadowType shadow, String oid) {
        String shadowResourceOid = shadow.getResourceRef() != null ? shadow.getResourceRef().getOid() : null;
        return shadowResourceOid != null && shadowResourceOid.equals(oid);
    }

    /**
     * Interprets ResourceShadowDiscriminator as a pattern. E.g. null discriminator kind is
     * interpreted to match any shadow kind.
     */
    public static boolean matchesPattern(ShadowType shadowType, ShadowDiscriminatorType discr) {
        if (shadowType == null) {
            return false;
        }
        if (discr == null || discr.getResourceRef() == null) {
            return false; // shouldn't occur
        }
        if (!shadowHasResourceOid(shadowType, discr.getResourceRef().getOid())) {
            return false;
        }
        if (discr.getKind() != null && !MiscUtil.equals(discr.getKind(), shadowType.getKind())) {
            return false;
        }
        if (discr.getIntent() == null) {
            return true;
        }
        return ResourceShadowDiscriminator.equalsIntent(shadowType.getIntent(), discr.getIntent());
    }

    public static boolean isConflicting(ShadowType shadow1, ShadowType shadow2) {
        if (!shadow1.getResourceRef().getOid().equals(shadow2.getResourceRef().getOid())) {
            return false;
        }
        if (!MiscUtil.equals(getKind(shadow1), getKind(shadow2))) {
            return false;
        }
        return ResourceShadowDiscriminator.equalsIntent(shadow1.getIntent(), shadow2.getIntent());
    }

    public static Object getHumanReadableNameLazily(PrismObject<? extends ShadowType> shadow) {
        return DebugUtil.lazy(() -> getHumanReadableName(shadow));
    }

    public static String getHumanReadableName(PrismObject<? extends ShadowType> shadow) {
        if (shadow == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        ShadowType shadowType = shadow.asObjectable();
        ShadowKindType kind = shadowType.getKind();
        if (kind != null) {
            sb.append(kind).append(" ");
        }
        sb.append("shadow ");
        boolean first = true;
        for(ResourceAttribute iattr: getPrimaryIdentifiers(shadow)) {
            if (first) {
                sb.append("[");
                first  = false;
            } else {
                sb.append(",");
            }
            sb.append(iattr.getElementName().getLocalPart());
            sb.append("=");
            sb.append(iattr.getRealValue());
        }
        if (first) {
            sb.append("[");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String getHumanReadableName(ShadowType shadowType) {
        if (shadowType == null) {
            return "null";
        }
        return getHumanReadableName(shadowType.asPrismObject());
    }

    public static boolean isFullShadow(PrismObject<ShadowType> shadow) {
        ShadowType shadowType = shadow.asObjectable();
        if (shadowType.getCachingMetadata() == null) {
            return false;
        }
        return shadowType.getCachingMetadata().getRetrievalTimestamp() != null;
    }

    public static <T extends ShadowType> PolyString determineShadowName(ShadowType shadow)
            throws SchemaException {
        return determineShadowName(shadow.asPrismObject());
    }

    public static <T extends ShadowType> PolyString determineShadowName(PrismObject<T> shadow) throws SchemaException {
        String stringName = determineShadowStringName(shadow);
        return stringName != null ? PolyString.fromOrig(stringName) : null;
    }

    public static <T extends ShadowType> String determineShadowStringName(PrismObject<T> shadow) throws SchemaException {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        if (attributesContainer == null) {
            return null;
        }
        ResourceAttribute<String> namingAttribute = attributesContainer.getNamingAttribute();
        if (namingAttribute == null || namingAttribute.isEmpty()) {
            // No naming attribute defined. Try to fall back to identifiers.
            Collection<ResourceAttribute<?>> identifiers = attributesContainer.getPrimaryIdentifiers();
            // We can use only single identifiers (not composite)
            if (identifiers.size() == 1) {
                PrismProperty<?> identifier = identifiers.iterator().next();
                // Only single-valued identifiers
                Collection<PrismPropertyValue<?>> values = (Collection) identifier.getValues();
                if (values.size() == 1) {
                    PrismPropertyValue<?> value = values.iterator().next();
                    // and only strings
                    if (value.getValue() instanceof String) {
                        return (String) value.getValue();
                    }
                }
            } else {
                ResourceAttribute<String> nameAttribute = attributesContainer.findAttribute(SchemaConstants.ICFS_NAME);
                if (nameAttribute == null) {
                    throw new SchemaException("Could not determine shadow name.");
                }
                return nameAttribute.getValue(String.class).getValue();
            }
            // Identifier is not usable as name
            // TODO: better identification of a problem
            throw new SchemaException("No naming attribute defined (and identifier not usable)");
        }
        // TODO: Error handling
        List<PrismPropertyValue<String>> possibleValues = namingAttribute.getValues();

        if (possibleValues.size() > 1) {
            throw new SchemaException(
                    "Cannot determine name of shadow. Found more than one value for naming attribute (attr: "
                            + namingAttribute.getElementName() + ", values: {}" + possibleValues + ")");
        }

        PrismPropertyValue<String> value = possibleValues.iterator().next();

        if (value == null) {
            throw new SchemaException("Naming attribute has no value. Could not determine shadow name.");
        }

        return value.getValue();
    }

    public static @NotNull ResourceObjectIdentification getResourceObjectIdentification(
            PrismObject<ShadowType> shadow, ResourceObjectDefinition objectDefinition) {
        return new ResourceObjectIdentification(
                objectDefinition,
                ShadowUtil.getPrimaryIdentifiers(shadow),
                ShadowUtil.getSecondaryIdentifiers(shadow));
    }

    public static boolean matchesAttribute(ItemPath path, QName attributeName) {
        return path.startsWithName(ShadowType.F_ATTRIBUTES) &&
                path.rest().startsWithName(attributeName);
    }

    public static boolean hasPrimaryIdentifier(PrismObject<ShadowType> shadow,
            ResourceObjectDefinition objectDefinition) {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        return attributesContainer != null &&
                !attributesContainer
                        .extractAttributesByDefinitions(objectDefinition.getPrimaryIdentifiers())
                        .isEmpty();
    }

    public static boolean hasPrimaryIdentifier(Collection<? extends ResourceAttribute<?>> identifiers,
            ResourceObjectDefinition objectDefinition) {
        for (ResourceAttribute identifier: identifiers) {
            if (objectDefinition.isPrimaryIdentifier(identifier.getElementName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasSecondaryIdentifier(Collection<? extends ResourceAttribute<?>> identifiers,
            ResourceObjectClassDefinition objectClassDefinition) {
        for (ResourceAttribute identifier: identifiers) {
            if (objectClassDefinition.isSecondaryIdentifier(identifier.getElementName())) {
                return true;
            }
        }
        return false;
    }

    public static ResourceAttribute<?> fixAttributePath(ResourceAttribute<?> attribute) throws SchemaException {
        if (attribute == null) {
            return null;
        }
        if (attribute.getPath().startsWithName(ShadowType.F_ATTRIBUTES)) {
            return attribute;
        }
        ResourceAttribute<?> fixedAttribute = attribute.clone();
        ResourceAttributeContainer container = ObjectFactory.createResourceAttributeContainer(ShadowType.F_ATTRIBUTES, null);
        container.createNewValue().add(fixedAttribute);
        return fixedAttribute;
    }

    // TODO: may be useful to move to ResourceObjectClassDefinition later?
    public static void validateAttributeSchema(ShadowType shadow,
            ResourceObjectDefinition objectDefinition) throws SchemaException {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        for (ResourceAttribute<?> attribute: attributesContainer.getAttributes()) {
            validateAttribute(attribute, objectDefinition);
        }
    }

    // TODO: may be useful to move to ResourceAttributeDefinition later?
    private static <T> void validateAttribute(ResourceAttribute<T> attribute,
            ResourceObjectDefinition objectDefinition) throws SchemaException {
        QName attrName = attribute.getElementName();
        ResourceAttributeDefinition<?> attrDef = objectDefinition.findAttributeDefinition(attrName);
        if (attrDef == null) {
            throw new SchemaException("No definition for attribute "+attrName+" in object class "+objectDefinition);
        }
        List<PrismPropertyValue<T>> pvals = attribute.getValues();
        if (pvals.isEmpty()) {
            if (attrDef.isMandatory()) {
                throw new SchemaException("Mandatory attribute "+attrName+" has no value");
            } else {
                return;
            }
        }
        if (pvals.size() > 1 && attrDef.isSingleValue()) {
            throw new SchemaException("Single-value attribute "+attrName+" has "+pvals.size()+" values");
        }
        Class<?> expectedClass = attrDef.getTypeClass();
        for (PrismPropertyValue<T> pval: pvals) {
            T val = pval.getValue();
            if (val == null) {
                throw new SchemaException("Null value in attribute "+attrName);
            }
//            LOGGER.info("MMMMMMMMMMMM: {}:{}\n   {} <-> {}", attrName, attrDef, expectedClass, val.getClass());
            if (!XmlTypeConverter.isMatchingType(expectedClass, val.getClass())) {
                throw new SchemaException("Wrong value in attribute "+attrName+"; expected class "+attrDef.getTypeClass().getSimpleName()+", but was "+val.getClass());
            }
        }
    }

    public static ProtectedStringType getPasswordValue(ShadowType shadowType) {
        if (shadowType == null) {
            return null;
        }
        CredentialsType creds = shadowType.getCredentials();
        if (creds == null) {
            return null;
        }
        PasswordType passwd = creds.getPassword();
        if (passwd == null) {
            return null;
        }
        return passwd.getValue();
    }

    public static Object shortDumpShadowLazily(PrismObject<ShadowType> shadow) {
        return DebugUtil.lazy(() -> shortDumpShadow(shadow));
    }

    public static String shortDumpShadow(PrismObject<ShadowType> shadow) {
        if (shadow == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("shadow:");
        sb.append(shadow.getOid()).append("(");
        PolyString name = shadow.getName();
        if (name != null) {
            sb.append(name);
        } else {
            Collection<ResourceAttribute<?>> primaryIdentifiers = getPrimaryIdentifiers(shadow);
            if (primaryIdentifiers != null && !primaryIdentifiers.isEmpty()) {
                shortDumpShadowIdentifiers(sb, shadow, primaryIdentifiers);
            } else {
                Collection<ResourceAttribute<?>> secondaryIdentifiers = getSecondaryIdentifiers(shadow);
                if (secondaryIdentifiers != null && !secondaryIdentifiers.isEmpty()) {
                    shortDumpShadowIdentifiers(sb, shadow, secondaryIdentifiers);
                }
            }
        }
        ShadowType shadowType = shadow.asObjectable();
        Boolean dead = shadowType.isDead();
        if (dead != null && dead) {
            sb.append(";DEAD");
        }
        Boolean exists = shadowType.isExists();
        if (exists != null && !exists) {
            sb.append(";NOTEXISTS");
        }
        sb.append(")");
        return sb.toString();
    }

    private static void shortDumpShadowIdentifiers(StringBuilder sb, PrismObject<ShadowType> shadow, Collection<ResourceAttribute<?>> identifiers) {
        Iterator<ResourceAttribute<?>> iterator = identifiers.iterator();
        while (iterator.hasNext()) {
            ResourceAttribute<?> identifier = iterator.next();
            sb.append(identifier.getElementName().getLocalPart());
            sb.append("=");
            sb.append(identifier.getRealValue());
            if (iterator.hasNext()) {
                sb.append(";");
            }
        }
    }

    public static boolean isKnown(ShadowKindType kind) {
        return kind != null && kind != ShadowKindType.UNKNOWN;
    }

    public static boolean isNotKnown(ShadowKindType kind) {
        return !isKnown(kind);
    }

    public static boolean isKnown(String intent) {
        return intent != null && !SchemaConstants.INTENT_UNKNOWN.equals(intent);
    }

    public static boolean isNotKnown(String intent) {
        return !isKnown(intent);
    }

    public static void removeAllAttributesExceptPrimaryIdentifier(PrismObject<ShadowType> shadow,
            ResourceObjectDefinition objDef) {
        ResourceAttributeContainer attributesContainer = getAttributesContainer(shadow);
        if (attributesContainer != null) {
            List<ItemName> attributesToDelete = attributesContainer.getAttributes().stream()
                    .map(Item::getElementName)
                    .filter(attrName -> !objDef.isPrimaryIdentifier(attrName))
                    .collect(Collectors.toList());
            for (ItemName attrName : attributesToDelete) {
                attributesContainer.getValue().removeProperty(attrName);
            }
        }
    }

    public static List<ObjectReferenceType> selectLiveLinks(List<ObjectReferenceType> refs) {
        return refs.stream()
                .filter(ShadowUtil::isNotDead)
                .collect(Collectors.toList());
    }

    /**
     * Returns true if the shadow state indicates that it is 'gone', i.e. no longer on the resource.
     * This could be determined from the `dead` property or from the `shadowLifecycleState`. The latter is
     * more precise for shadows that have been fetched with the future point-in-time.
     *
     * PRECONDITION: shadow lifecycle state must be set up.
     */
    public static boolean isGone(@NotNull ShadowType shadow) {
        ShadowLifecycleStateType state = shadow.getShadowLifecycleState();
        stateCheck(state != null, "Unknown lifecycle state of %s", shadow);
        return state == ShadowLifecycleStateType.CORPSE || state == ShadowLifecycleStateType.TOMBSTONE;
    }

    public static ShadowCorrelationStateType getCorrelationStateRequired(@NotNull ShadowType shadow) {
        return MiscUtil.requireNonNull(
                shadow.getCorrelation(),
                () -> new IllegalStateException("No correlation state in shadow " + shadow));

    }
    public static <T extends AbstractCorrelatorStateType> T getCorrelatorStateRequired(@NotNull ShadowType shadow, Class<T> clazz)
            throws SchemaException {
        return MiscUtil.requireNonNull(
                MiscUtil.castSafely(getCorrelationStateRequired(shadow).getCorrelatorState(), clazz),
                () -> new IllegalStateException("No correlation state in shadow " + shadow));
    }

    public static void setCorrelatorState(@NotNull ShadowType shadow, @Nullable AbstractCorrelatorStateType state) {
        if (shadow.getCorrelation() == null) {
            if (state == null) {
                return;
            } else {
                shadow.setCorrelation(
                        new ShadowCorrelationStateType(PrismContext.get()));
            }
        }

        shadow.getCorrelation().setCorrelatorState(state);
    }
}
