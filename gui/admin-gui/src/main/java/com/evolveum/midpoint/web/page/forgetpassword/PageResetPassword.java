/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.forgetpassword;

import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.web.page.admin.home.dto.MyPasswordsDto;
import com.evolveum.midpoint.web.page.admin.home.dto.PasswordAccountDto;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.pages.RedirectPage;

import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.web.page.self.PageAbstractSelfCredentials;
import com.evolveum.midpoint.web.page.self.PageSelf;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

import java.util.Collections;
import java.util.List;

@PageDescriptor(
        urls = {
                @Url(mountUrl = "/resetPassword", matchUrlForSecurity = "/resetPassword")
        },
        action = {
                @AuthorizationAction(actionUri = PageSelf.AUTH_SELF_ALL_URI,
                    label = PageSelf.AUTH_SELF_ALL_LABEL,
                    description = PageSelf.AUTH_SELF_ALL_DESCRIPTION),
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_SELF_CREDENTIALS_URL,
                    label = "PageSelfCredentials.auth.credentials.label",
                    description = "PageSelfCredentials.auth.credentials.description")})
public class PageResetPassword extends PageAbstractSelfCredentials{

    private static final long serialVersionUID = 1L;

    public PageResetPassword() {
        // TODO Auto-generated constructor stub
    }

    @Override
    protected boolean isSideMenuVisible() {
        return false;
    }


    @Override
    protected boolean isCheckOldPassword() {
        return false;
    }

    @Override
    protected void finishChangePassword(final OperationResult result, AjaxRequestTarget target, boolean showFeedback) {


        if (result.getStatus() == OperationResultStatus.SUCCESS) {
            result.setMessage(getString("PageResetPassword.reset.successful"));
            setResponsePage(new RedirectPage("/"));

            MyPasswordsDto passwords = getPasswordDto();
            PrismObject<? extends FocusType> focus = passwords.getFocus();
            if (focus == null) {
                AuthUtil.clearMidpointAuthentication();
                return;
            }

            FocusType focusType = focus.asObjectable();

            if (focusType.getCredentials() != null && focusType.getCredentials().getNonce() != null) {

                try {
                    ObjectDelta<UserType> deleteNonceDelta = getPrismContext().deltaFactory().object()
                            .createModificationDeleteContainer(UserType.class, focusType.getOid(), SchemaConstants.PATH_NONCE,
                                    focusType.getCredentials().getNonce().clone());
                    WebModelServiceUtils.save(deleteNonceDelta, result, this);
                } catch (SchemaException e) {
                    //nothing to do, just let the nonce here. it will be invalid
                }
            }

            showResult(result);
            target.add(getFeedbackPanel());
            AuthUtil.clearMidpointAuthentication();
        } else if (showFeedback) {
            showResult(result);
        }
        target.add(getFeedbackPanel());
    }

    @Override
    protected void createBreadcrumb() {
        // we don't want breadcrumbs here
    }

    @Override
    protected boolean shouldLoadAccounts() {
        return false;
    }

    @Override
    protected List<PasswordAccountDto> getSelectedAccountsList() {
        List<PasswordAccountDto> accounts = getPasswordDto().getAccounts();
        if (accounts.isEmpty()) {
            return Collections.emptyList();
        }

       for (PasswordAccountDto account : accounts) {
           if (account.isMidpoint()) {
               return Collections.singletonList(account);
           }
       }

       return accounts;
    }

    @Override
    public Task createSimpleTask(String operation) {
        return createAnonymousTask(operation);
    }
}
