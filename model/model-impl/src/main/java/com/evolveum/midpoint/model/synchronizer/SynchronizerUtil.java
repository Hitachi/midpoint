/**
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
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.model.synchronizer;

import com.evolveum.midpoint.model.SyncContext;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * @author semancik
 *
 */
public class SynchronizerUtil {
	
	private static final Trace LOGGER = TraceManager.getTrace(SynchronizerUtil.class);
	
	public static void traceContext(String phase, SyncContext context, boolean showTriples) {
    	if (LOGGER.isDebugEnabled()) {
    		StringBuilder sb = new StringBuilder("After ");
    		sb.append(phase);
    		sb.append(":");
    		for (ObjectDelta objectDelta: context.getAllChanges()) {
    			sb.append("\n");
    			sb.append(objectDelta.toString());
    		}
    		LOGGER.debug(sb.toString());
    	}
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Synchronization context:\n"+
            		"---[ CONTEXT after {} ]--------------------------------\n"+
            		"{}\n",
            		phase, context.dump(showTriples));
        }
    }

}
