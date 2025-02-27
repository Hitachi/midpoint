/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.user;

import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

import org.apache.wicket.model.IModel;

/**
 * Created by honchar.
 */
@PageDescriptor(
        urls = {
                @Url(mountUrl = "/admin/userHistoryNew")
        },
        action = {
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_USERS_ALL_URL,
                label = "PageAdminUsers.auth.usersAll.label",
                description = "PageAdminUsers.auth.usersAll.description"),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_USER_HISTORY_URL,
                label = "PageUser.auth.userHistory.label",
                description = "PageUser.auth.userHistory.description")})
public class PageUserHistory extends PageUser {
    private static final long serialVersionUID = 1L;

    private static final String DOT_CLASS = PageUserHistory.class.getName() + ".";
    private static final Trace LOGGER = TraceManager.getTrace(PageUserHistory.class);
    private String date = "";

    public PageUserHistory(final PrismObject<UserType> user, String date) {
        super(user);
        this.date = date;
    }

    @Override
    protected UserDetailsModel createObjectDetailsModels(PrismObject<UserType> object) {
        return new UserDetailsModel(createPrismObjectModel(object), this) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean isReadonly() {
                return true;
            }
        };
    }

    @Override
    protected IModel<String> createPageTitleModel() {
        return new LoadableModel<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected String load() {
                String name = null;
                if (getModelObjectType() != null) {
                    name = WebComponentUtil.getName(getModelObjectType());
                }
                return createStringResource("PageUserHistory.title", name, date).getObject();
            }
        };
    }

}
