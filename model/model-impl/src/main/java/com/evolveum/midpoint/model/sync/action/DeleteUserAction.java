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
 * Portions Copyrighted 2010 Forgerock
 */

package com.evolveum.midpoint.model.sync.action;

import org.apache.commons.lang.StringUtils;

import com.evolveum.midpoint.api.logging.LoggingUtils;
import com.evolveum.midpoint.api.logging.Trace;
import com.evolveum.midpoint.common.result.OperationResult;
import com.evolveum.midpoint.logging.TraceManager;
import com.evolveum.midpoint.model.sync.SynchronizationException;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowChangeDescriptionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SynchronizationSituationType;

/**
 * 
 * @author lazyman
 * 
 */
public class DeleteUserAction extends BaseAction {

	private static Trace LOGGER = TraceManager.getTrace(DeleteUserAction.class);

	@Override
	public String executeChanges(String userOid, ResourceObjectShadowChangeDescriptionType change,
			SynchronizationSituationType situation, ResourceObjectShadowType shadowAfterChange,
			OperationResult result) throws SynchronizationException {
		super.executeChanges(userOid, change, situation, shadowAfterChange, result);

		OperationResult subResult = new OperationResult("Delete User Action");
		result.addSubresult(subResult);

		if (StringUtils.isEmpty(userOid)) {
			String message = "Can't delete user, because user oid is null.";
			subResult.recordFatalError(message);
			throw new SynchronizationException(message);
		}

		try {
			getModel().deleteObject(userOid, result);
			subResult.recordSuccess();
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't delete user {}", ex, userOid);
			subResult.recordFatalError("Couldn't delete user '" + userOid + "'.", ex);
			throw new SynchronizationException("Couldn't delete user '" + userOid + "', reason: "
					+ ex.getMessage(), ex);
		}

		return null;
	}
}
