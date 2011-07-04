/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.model.sync.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import javax.xml.bind.JAXBElement;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.w3c.dom.Element;

import com.evolveum.midpoint.api.logging.Trace;
import com.evolveum.midpoint.common.jaxb.JAXBUtil;
import com.evolveum.midpoint.common.result.OperationResult;
import com.evolveum.midpoint.common.test.XmlAsserts;
import com.evolveum.midpoint.logging.TraceManager;
import com.evolveum.midpoint.model.sync.SynchronizationException;
import com.evolveum.midpoint.model.test.util.ModelServiceUtil;
import com.evolveum.midpoint.schema.ObjectTypes;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectChangeAdditionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyModificationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowChangeDescriptionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SynchronizationSituationType;
import com.evolveum.midpoint.xml.schema.SchemaConstants;

/**
 * 
 * @author lazyman
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:application-context-model.xml",
		"classpath:application-context-model-unit-test.xml" })
public class LinkAccountActionTest extends BaseActionTest {

	private static final File TEST_FOLDER = new File("./src/test/resources/sync/action/linkAccount");
	private static final Trace LOGGER = TraceManager.getTrace(LinkAccountActionTest.class);

	@Before
	public void before() {
		Mockito.reset(provisioning, repository);
		before(new LinkAccountAction());
	}

	@Test(expected = SynchronizationException.class)
	@SuppressWarnings("unchecked")
	public void nonExistingUser() throws Exception {
		ResourceObjectShadowChangeDescriptionType change = ((JAXBElement<ResourceObjectShadowChangeDescriptionType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "../addUser/existing-user-change.xml"))).getValue();
		OperationResult result = new OperationResult("Link Account Action Test");

		String userOid = "1";
		when(
				repository.getObject(eq(userOid), any(PropertyReferenceListType.class),
						any(OperationResult.class))).thenThrow(new ObjectNotFoundException("user not found"));

		try {
			ObjectChangeAdditionType addition = (ObjectChangeAdditionType) change.getObjectChange();
			action.executeChanges(userOid, change, SynchronizationSituationType.CONFIRMED,
					(ResourceObjectShadowType) addition.getObject(), result);
		} finally {
			LOGGER.debug(result.debugDump());
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void nonAccountShadow() throws Exception {
		ResourceObjectShadowChangeDescriptionType change = ((JAXBElement<ResourceObjectShadowChangeDescriptionType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "group-change.xml"))).getValue();
		OperationResult result = new OperationResult("Link Account Action Test");

		String userOid = ModelServiceUtil.mockUser(repository, new File(TEST_FOLDER, "user.xml"), null);

		try {
			ObjectChangeAdditionType addition = (ObjectChangeAdditionType) change.getObjectChange();
			action.executeChanges(userOid, change, SynchronizationSituationType.CONFIRMED,
					(ResourceObjectShadowType) addition.getObject(), result);
		} finally {
			LOGGER.debug(result.debugDump());
		}

		verify(repository, times(0)).modifyObject(any(ObjectModificationType.class),
				any(OperationResult.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void correctLinkAccount() throws Exception {
		ResourceObjectShadowChangeDescriptionType change = ((JAXBElement<ResourceObjectShadowChangeDescriptionType>) JAXBUtil
				.unmarshal(new File(TEST_FOLDER, "../addUser/existing-user-change.xml"))).getValue();
		OperationResult result = new OperationResult("Link Account Action Test");

		final String shadowOid = change.getShadow().getOid();

		final String userOid = ModelServiceUtil.mockUser(repository, new File(TEST_FOLDER, "user.xml"), null);
		doNothing().doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				ObjectModificationType change = (ObjectModificationType) invocation.getArguments()[0];
				assertNotNull(change);
				assertEquals(userOid, change.getOid());
				assertEquals(1, change.getPropertyModification().size());

				PropertyModificationType modification = change.getPropertyModification().get(0);
				assertNotNull(modification.getValue());
				assertEquals(1, modification.getValue().getAny().size());
				assertEquals(modification.getModificationType(), PropertyModificationTypeType.add);

				Element element = modification.getValue().getAny().get(0);

				ObjectReferenceType accountRef = new ObjectReferenceType();
				accountRef.setOid(shadowOid);
				accountRef.setType(ObjectTypes.ACCOUNT.getQName());

				XmlAsserts.assertPatch(JAXBUtil.marshalWrap(accountRef, SchemaConstants.I_ACCOUNT_REF),
						DOMUtil.printDom(element).toString());

				return null;
			}
		}).when(repository).modifyObject(any(ObjectModificationType.class), any(OperationResult.class));

		try {
			ObjectChangeAdditionType addition = (ObjectChangeAdditionType) change.getObjectChange();
			action.executeChanges(userOid, change, SynchronizationSituationType.CONFIRMED,
					(ResourceObjectShadowType) addition.getObject(), result);
		} finally {
			LOGGER.debug(result.debugDump());
		}

		verify(repository, times(1)).modifyObject(any(ObjectModificationType.class),
				any(OperationResult.class));
	}
}
