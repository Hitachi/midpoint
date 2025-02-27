/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.rest.impl;

import static org.springframework.http.ResponseEntity.status;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import javax.xml.namespace.QName;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.evolveum.midpoint.model.api.*;
import com.evolveum.midpoint.model.impl.ModelCrudService;
import com.evolveum.midpoint.model.impl.scripting.PipelineData;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.ItemPathCollectionsUtil;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.DefinitionProcessingOption;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.SecurityUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ExecuteScriptOutputType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ExecuteScriptType;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;

@RestController
@RequestMapping({ "/ws/rest", "/rest/model", "/api/model" })
public class ModelRestController extends AbstractRestController {

    public static final String GET_OBJECT_PATH = "/{type}/{id}";

    private static final String CURRENT = "current";
    private static final long WAIT_FOR_TASK_STOP = 2000L;

    @Autowired private ModelCrudService model;
    @Autowired private ModelDiagnosticService modelDiagnosticService;
    @Autowired private ModelInteractionService modelInteraction;
    @Autowired private ModelService modelService;
    @Autowired private ScriptingService scriptingService;
    @Autowired private TaskService taskService;

    @PostMapping("/{type}/{oid}/generate")
    public ResponseEntity<?> generateValue(
            @PathVariable("type") String type,
            @PathVariable("oid") String oid,
            @RequestBody PolicyItemsDefinitionType policyItemsDefinition) {

        Task task = initRequest();
        OperationResult result = createSubresult(task, "generateValue");

        Class<? extends ObjectType> clazz = ObjectTypes.getClassFromRestType(type);
        ResponseEntity<?> response;
        try {
            PrismObject<? extends ObjectType> object = model.getObject(clazz, oid, null, task, result);
            response = generateValue(object, policyItemsDefinition, task, result);
        } catch (Exception ex) {
            result.computeStatus();
            response = handleException(result, ex);
        }

        finishRequest(task, result);
        return response;
    }

    @PostMapping("/rpc/generate")
    public ResponseEntity<?> generateValueRpc(
            @RequestBody PolicyItemsDefinitionType policyItemsDefinition) {
        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("generateValueRpc");

        ResponseEntity<?> response = generateValue(null, policyItemsDefinition, task, result);
        finishRequest(task, result);

        return response;
    }

    private <O extends ObjectType> ResponseEntity<?> generateValue(
            PrismObject<O> object, PolicyItemsDefinitionType policyItemsDefinition,
            Task task, OperationResult parentResult) {

        ResponseEntity<?> response;
        if (policyItemsDefinition == null) {
            response = createBadPolicyItemsDefinitionResponse("Policy items definition must not be null", parentResult);
        } else {
            try {
                modelInteraction.generateValue(object, policyItemsDefinition, task, parentResult);
                parentResult.computeStatusIfUnknown();
                if (parentResult.isSuccess()) {
                    response = createResponse(HttpStatus.OK, policyItemsDefinition, parentResult, true);
                } else {
                    response = createResponse(HttpStatus.BAD_REQUEST, parentResult, parentResult);
                }

            } catch (Exception ex) {
                parentResult.recordFatalError("Failed to generate value, " + ex.getMessage(), ex);
                response = handleException(parentResult, ex);
            }
        }
        return response;
    }

    @PostMapping("/{type}/{oid}/validate")
    public ResponseEntity<?> validateValue(
            @PathVariable("type") String type,
            @PathVariable("oid") String oid,
            @RequestBody PolicyItemsDefinitionType policyItemsDefinition) {

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("validateValue");

        Class<? extends ObjectType> clazz = ObjectTypes.getClassFromRestType(type);
        ResponseEntity<?> response;
        try {
            PrismObject<? extends ObjectType> object = model.getObject(clazz, oid, null, task, result);
            response = validateValue(object, policyItemsDefinition, task, result);
        } catch (Exception ex) {
            result.computeStatus();
            response = handleException(result, ex);
        }

        finishRequest(task, result);
        return response;
    }

    @PostMapping("/rpc/validate")
    public ResponseEntity<?> validateValue(
            @RequestBody PolicyItemsDefinitionType policyItemsDefinition) {
        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("validateValue");

        ResponseEntity<?> response = validateValue(null, policyItemsDefinition, task, result);
        finishRequest(task, result);
        return response;
    }

    private <O extends ObjectType> ResponseEntity<?> validateValue(
            PrismObject<O> object, PolicyItemsDefinitionType policyItemsDefinition,
            Task task, OperationResult result) {
        ResponseEntity<?> response;
        if (policyItemsDefinition == null) {
            response = createBadPolicyItemsDefinitionResponse("Policy items definition must not be null", result);
            finishRequest(task, result);
            return response;
        }

        if (CollectionUtils.isEmpty(policyItemsDefinition.getPolicyItemDefinition())) {
            response = createBadPolicyItemsDefinitionResponse("No definitions for items", result);
            finishRequest(task, result);
            return response;
        }

        try {
            modelInteraction.validateValue(object, policyItemsDefinition, task, result);

            result.computeStatusIfUnknown();
            if (result.isAcceptable()) {
                response = createResponse(HttpStatus.OK, policyItemsDefinition, result, true);
            } else {
                response = ResponseEntity.status(HttpStatus.CONFLICT).body(result);
            }

        } catch (Exception ex) {
            result.computeStatus();
            response = handleException(result, ex);
        }

        return response;
    }

    private ResponseEntity<?> createBadPolicyItemsDefinitionResponse(
            String message, OperationResult parentResult) {
        logger.error(message);
        parentResult.recordFatalError(message);
        return status(HttpStatus.BAD_REQUEST).body(parentResult);
    }

    @GetMapping("/users/{id}/policy")
    public ResponseEntity<?> getValuePolicyForUser(
            @PathVariable("id") String oid) {
        logger.debug("getValuePolicyForUser start");

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("getValuePolicyForUser");

        ResponseEntity<?> response;
        try {
            Collection<SelectorOptions<GetOperationOptions>> options =
                    SelectorOptions.createCollection(GetOperationOptions.createRaw());
            PrismObject<UserType> user = model.getObject(UserType.class, oid, options, task, result);

            CredentialsPolicyType policy = modelInteraction.getCredentialsPolicy(user, task, result);

            response = createResponse(HttpStatus.OK, policy, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);

        logger.debug("getValuePolicyForUser finish");
        return response;
    }

    @GetMapping(GET_OBJECT_PATH)
    public ResponseEntity<?> getObject(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestParam(value = "options", required = false) List<String> options,
            @RequestParam(value = "include", required = false) List<String> include,
            @RequestParam(value = "exclude", required = false) List<String> exclude,
            @RequestParam(value = "resolveNames", required = false) List<String> resolveNames) {
        logger.debug("model rest service for get operation start");

        Task task = initRequest();
        OperationResult result = createSubresult(task, "getObject");

        Class<? extends ObjectType> clazz = ObjectTypes.getClassFromRestType(type);
        Collection<SelectorOptions<GetOperationOptions>> getOptions =
                GetOperationOptions.fromRestOptions(options, include, exclude,
                        resolveNames, DefinitionProcessingOption.ONLY_IF_EXISTS, prismContext);

        ResponseEntity<?> response;
        try {
            PrismObject<? extends ObjectType> object;
            if (NodeType.class.equals(clazz) && CURRENT.equals(id)) {
                object = getCurrentNodeObject(getOptions, task, result);
            } else {
                object = model.getObject(clazz, id, getOptions, task, result);
            }
            removeExcludes(object, exclude); // temporary measure until fixed in repo

            response = createResponse(HttpStatus.OK, object, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    private PrismObject<? extends ObjectType> getCurrentNodeObject(Collection<SelectorOptions<GetOperationOptions>> getOptions,
            Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException,
            ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        String nodeId = taskManager.getNodeId();
        ObjectQuery query = prismContext.queryFor(NodeType.class)
                .item(NodeType.F_NODE_IDENTIFIER).eq(nodeId)
                .build();
        List<PrismObject<NodeType>> objects = model.searchObjects(NodeType.class, query, getOptions, task, result);
        if (objects.isEmpty()) {
            throw new ObjectNotFoundException("Current node (id " + nodeId + ") couldn't be found.");
        } else if (objects.size() > 1) {
            throw new IllegalStateException("More than one 'current' node (id " + nodeId + ") found.");
        } else {
            return objects.get(0);
        }
    }

    @GetMapping("/self")
    public ResponseEntity<?> getSelf() {
        logger.debug("model rest service for get operation start");
        Task task = initRequest();
        OperationResult result = createSubresult(task, "self");
        ResponseEntity<?> response;

        try {
            String loggedInUserOid = SecurityUtil.getPrincipalOidIfAuthenticated();
            PrismObject<UserType> user = model.getObject(UserType.class, loggedInUserOid, null, task, result);
            response = createResponse(HttpStatus.OK, user, result, true);
            result.recordSuccessIfUnknown();
        } catch (SecurityViolationException | ObjectNotFoundException | SchemaException |
                CommunicationException | ConfigurationException | ExpressionEvaluationException e) {
            LoggingUtils.logUnexpectedException(logger, e);
            response = status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }

        finishRequest(task, result);
        return response;
    }

    @PostMapping("/{type}")
    public <T extends ObjectType> ResponseEntity<?> addObject(
            @PathVariable("type") String type,
            @RequestParam(value = "options", required = false) List<String> options,
            @RequestBody @NotNull PrismObject<T> object) {
        logger.debug("model rest service for add operation start");

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("addObject");

        Class<?> clazz = ObjectTypes.getClassFromRestType(type);
        if (object.getCompileTimeClass() == null || !object.getCompileTimeClass().equals(clazz)) {
            String simpleName = object.getCompileTimeClass() != null ? object.getCompileTimeClass().getSimpleName() : null;
            result.recordFatalError("Request to add object of type " + simpleName + " to the collection of " + type);
            finishRequest(task, result);
            return createErrorResponseBuilder(HttpStatus.BAD_REQUEST, result);
        }

        ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(options, prismContext);

        String oid;
        ResponseEntity<?> response;
        try {
            oid = model.addObject(object, modelExecuteOptions, task, result);
            logger.debug("returned oid: {}", oid);

            if (oid != null) {
                response = createResponseWithLocation(
                        clazz.isAssignableFrom(TaskType.class) ? HttpStatus.ACCEPTED : HttpStatus.CREATED,
                        uriGetObject(type, oid),
                        result);
            } else {
                // OID might be null e.g. if the object creation is a subject of workflow approval
                response = createResponse(HttpStatus.ACCEPTED, result);
            }
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @NotNull
    public URI uriGetObject(@PathVariable("type") String type, String oid) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(controllerBasePath() + GET_OBJECT_PATH)
                .build(type, oid);
    }

    @GetMapping("/{type}")
    public <T extends ObjectType> ResponseEntity<?> searchObjectsByType(
            @PathVariable("type") String type,
            @RequestParam(value = "options", required = false) List<String> options,
            @RequestParam(value = "include", required = false) List<String> include,
            @RequestParam(value = "exclude", required = false) List<String> exclude,
            @RequestParam(value = "resolveNames", required = false) List<String> resolveNames) {
        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("searchObjectsByType");

        //noinspection unchecked
        Class<T> clazz = (Class<T>) ObjectTypes.getClassFromRestType(type);
        ResponseEntity<?> response;
        try {

            Collection<SelectorOptions<GetOperationOptions>> searchOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, DefinitionProcessingOption.ONLY_IF_EXISTS, prismContext);

            List<PrismObject<T>> objects = modelService.searchObjects(clazz, null, searchOptions, task, result);
            ObjectListType listType = new ObjectListType();
            for (PrismObject<T> object : objects) {
                listType.getObject().add(object.asObjectable());
            }

            response = createResponse(HttpStatus.OK, listType, result, true);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @PutMapping("/{type}/{id}")
    public <T extends ObjectType> ResponseEntity<?> addObject(
            @PathVariable("type") String type,
            // TODO is it OK that this is not used or at least asserted?
            @SuppressWarnings("unused") @PathVariable("id") String id,
            @RequestParam(value = "options", required = false) List<String> options,
            @RequestBody @NotNull PrismObject<T> object) {
        logger.debug("model rest service for add operation start");

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("addObject");

        Class<?> clazz = ObjectTypes.getClassFromRestType(type);
        if (!object.getCompileTimeClass().equals(clazz)) {
            finishRequest(task, result);
            result.recordFatalError("Request to add object of type "
                    + object.getCompileTimeClass().getSimpleName()
                    + " to the collection of " + type);
            return createErrorResponseBuilder(HttpStatus.BAD_REQUEST, result);
        }

        ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(options, prismContext);
        if (modelExecuteOptions == null) {
            modelExecuteOptions = ModelExecuteOptions.create(prismContext);
        }
        // TODO: Do we want to overwrite in any case? Because of PUT?
        //  This was original logic... and then there's that ignored ID.
        modelExecuteOptions.overwrite();

        String oid;
        ResponseEntity<?> response;
        try {
            oid = model.addObject(object, modelExecuteOptions, task, result);
            logger.debug("returned oid : {}", oid);

            response = createResponseWithLocation(
                    clazz.isAssignableFrom(TaskType.class) ? HttpStatus.ACCEPTED : HttpStatus.CREATED,
                    uriGetObject(type, oid),
                    result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }
        result.computeStatus();

        finishRequest(task, result);
        return response;
    }

    @DeleteMapping("/{type}/{id}")
    public ResponseEntity<?> deleteObject(
            @PathVariable("type") String type,
            @PathVariable("id") String id,
            @RequestParam(value = "options", required = false) List<String> options) {

        logger.debug("model rest service for delete operation start");

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("deleteObject");

        Class<? extends ObjectType> clazz = ObjectTypes.getClassFromRestType(type);
        ResponseEntity<?> response;
        try {
            if (clazz.isAssignableFrom(TaskType.class)) {
                taskService.suspendAndDeleteTask(id, WAIT_FOR_TASK_STOP, true, task, result);
                result.computeStatus();
                finishRequest(task, result);
                if (result.isSuccess()) {
                    return ResponseEntity.noContent().build();
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(result.getMessage());
            }

            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(options, prismContext);

            model.deleteObject(clazz, id, modelExecuteOptions, task, result);
            response = createResponse(HttpStatus.NO_CONTENT, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @PostMapping("/{type}/{oid}")
    public ResponseEntity<?> modifyObjectPost(
            @PathVariable("type") String type,
            @PathVariable("oid") String oid,
            @RequestParam(value = "options", required = false) List<String> options,
            @RequestBody ObjectModificationType modificationType) {
        return modifyObjectPatch(type, oid, options, modificationType);
    }

    @PatchMapping("/{type}/{oid}")
    public ResponseEntity<?> modifyObjectPatch(
            @PathVariable("type") String type,
            @PathVariable("oid") String oid,
            @RequestParam(value = "options", required = false) List<String> options,
            @RequestBody ObjectModificationType modificationType) {

        logger.debug("model rest service for modify operation start");

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("modifyObjectPatch");

        Class<? extends ObjectType> clazz = ObjectTypes.getClassFromRestType(type);
        ResponseEntity<?> response;
        try {
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(options, prismContext);
            Collection<? extends ItemDelta<?, ?>> modifications = DeltaConvertor.toModifications(modificationType, clazz, prismContext);
            model.modifyObject(clazz, oid, modifications, modelExecuteOptions, task, result);
            response = createResponse(HttpStatus.NO_CONTENT, result);
        } catch (Exception ex) {
            result.recordFatalError("Could not modify object. " + ex.getMessage(), ex);
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @PostMapping("/notifyChange")
    public ResponseEntity<?> notifyChange(
            @RequestBody ResourceObjectShadowChangeDescriptionType changeDescription) {
        logger.debug("model rest service for notify change operation start");
        Validate.notNull(changeDescription, "Chnage description must not be null");

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("notifyChange");

        ResponseEntity<?> response;
        try {
            modelService.notifyChange(changeDescription, task, result);
            response = createResponse(HttpStatus.OK, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @GetMapping("/shadows/{oid}/owner")
    public ResponseEntity<?> findShadowOwner(
            @PathVariable("oid") String shadowOid) {

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("findShadowOwner");

        ResponseEntity<?> response;
        try {
            PrismObject<? extends FocusType> focus = modelService.searchShadowOwner(shadowOid, null, task, result);
            response = createResponse(HttpStatus.OK, focus, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @PostMapping("/shadows/{oid}/import")
    public ResponseEntity<?> importShadow(
            @PathVariable("oid") String shadowOid) {
        logger.debug("model rest service for import shadow from resource operation start");

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("importShadow");

        ResponseEntity<?> response;
        try {
            modelService.importFromResource(shadowOid, task, result);

            response = createResponse(HttpStatus.OK, result, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @PostMapping("/{type}/search")
    public ResponseEntity<?> searchObjects(
            @PathVariable("type") String type,
            @RequestParam(value = "options", required = false) List<String> options,
            @RequestParam(value = "include", required = false) List<String> include,
            @RequestParam(value = "exclude", required = false) List<String> exclude,
            @RequestParam(value = "resolveNames", required = false) List<String> resolveNames,
            @RequestBody QueryType queryType) {

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("searchObjects");

        Class<? extends ObjectType> clazz = ObjectTypes.getClassFromRestType(type);
        ResponseEntity<?> response;
        try {
            ObjectQuery query = prismContext.getQueryConverter().createObjectQuery(clazz, queryType);
            Collection<SelectorOptions<GetOperationOptions>> searchOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, DefinitionProcessingOption.ONLY_IF_EXISTS, prismContext);
            List<? extends PrismObject<? extends ObjectType>> objects =
                    model.searchObjects(clazz, query, searchOptions, task, result);

            ObjectListType listType = new ObjectListType();
            for (PrismObject<? extends ObjectType> o : objects) {
                removeExcludes(o, exclude);        // temporary measure until fixed in repo
                listType.getObject().add(o.asObjectable());
            }

            response = createResponse(HttpStatus.OK, listType, result, true);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    private void removeExcludes(PrismObject<? extends ObjectType> object, List<String> exclude)
            throws SchemaException {
        object.getValue().removePaths(
                ItemPathCollectionsUtil.pathListFromStrings(exclude, prismContext));
    }

    @PostMapping("/resources/{resourceOid}/import/{objectClass}")
    public ResponseEntity<?> importFromResource(
            @PathVariable("resourceOid") String resourceOid,
            @PathVariable("objectClass") String objectClass) {
        logger.debug("model rest service for import from resource operation start");

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("importFromResource");

        QName objClass = new QName(MidPointConstants.NS_RI, objectClass);
        ResponseEntity<?> response;
        try {
            modelService.importFromResource(resourceOid, objClass, task, result);
            response = createResponseWithLocation(
                    HttpStatus.SEE_OTHER,
                    uriGetObject(ObjectTypes.TASK.getRestType(), task.getOid()),
                    result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @PostMapping("/resources/{resourceOid}/test")
    public ResponseEntity<?> testResource(
            @PathVariable("resourceOid") String resourceOid) {
        logger.debug("model rest service for test resource operation start");

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("testResource");

        ResponseEntity<?> response;
        OperationResult testResult = null;
        try {
            testResult = modelService.testResource(resourceOid, task);
            response = createResponse(HttpStatus.OK, testResult, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        if (testResult != null) {
            result.getSubresults().add(testResult);
        }

        finishRequest(task, result);
        return response;
    }

    @PostMapping("/tasks/{oid}/suspend")
    public ResponseEntity<?> suspendTask(
            @PathVariable("oid") String taskOid) {

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("suspendTask");

        ResponseEntity<?> response;
        try {
            taskService.suspendTask(taskOid, WAIT_FOR_TASK_STOP, task, result);
            result.computeStatus();
            response = createResponse(HttpStatus.NO_CONTENT, task, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        finishRequest(task, result);
        return response;
    }

    @PostMapping("/tasks/{oid}/resume")
    public ResponseEntity<?> resumeTask(
            @PathVariable("oid") String taskOid) {

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("resumeTask");

        ResponseEntity<?> response;
        try {
            taskService.resumeTask(taskOid, task, result);
            result.computeStatus();
            response = createResponse(HttpStatus.ACCEPTED, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        finishRequest(task, result);
        return response;
    }

    @PostMapping("tasks/{oid}/run")
    public ResponseEntity<?> scheduleTaskNow(
            @PathVariable("oid") String taskOid) {
        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("scheduleTaskNow");

        ResponseEntity<?> response;
        try {
            taskService.scheduleTaskNow(taskOid, task, result);
            result.computeStatus();
            response = createResponse(HttpStatus.NO_CONTENT, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        finishRequest(task, result);
        return response;
    }

    @PostMapping("/rpc/executeScript")
    public ResponseEntity<?> executeScript(
            @RequestParam(value = "asynchronous", required = false) Boolean asynchronous,
            @RequestBody ExecuteScriptType command) {
        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("executeScript");

        ResponseEntity<?> response;
        try {
            if (Boolean.TRUE.equals(asynchronous)) {
                scriptingService.evaluateExpressionInBackground(command, task, result);
                response = createResponseWithLocation(
                        HttpStatus.CREATED,
                        uriGetObject(ObjectTypes.TASK.getRestType(), task.getOid()),
                        result);
            } else {
                ScriptExecutionResult executionResult = scriptingService.evaluateExpression(
                        command, VariablesMap.emptyMap(), false, task, result);
                ExecuteScriptResponseType responseData = new ExecuteScriptResponseType()
                        .result(result.createOperationResultType())
                        .output(new ExecuteScriptOutputType()
                                .consoleOutput(executionResult.getConsoleOutput())
                                .dataOutput(PipelineData.prepareXmlData(executionResult.getDataOutput(), command.getOptions())));
                response = createResponse(HttpStatus.OK, responseData, result);
            }
        } catch (Exception ex) {
            LoggingUtils.logUnexpectedException(logger, "Couldn't execute script.", ex);
            response = handleExceptionNoLog(result, ex);
        }
        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @PostMapping("/rpc/compare")
//    @Consumes({ "application/xml" }) TODO do we need to limit it to XML?
    public <T extends ObjectType> ResponseEntity<?> compare(
            @RequestParam(value = "readOptions", required = false) List<String> restReadOptions,
            @RequestParam(value = "compareOptions", required = false) List<String> restCompareOptions,
            @RequestParam(value = "ignoreItems", required = false) List<String> restIgnoreItems,
            @RequestBody PrismObject<T> clientObject) {

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("compare");

        ResponseEntity<?> response;
        try {
            List<ItemPath> ignoreItemPaths = ItemPathCollectionsUtil.pathListFromStrings(restIgnoreItems, prismContext);
            final GetOperationOptions getOpOptions = GetOperationOptions.fromRestOptions(restReadOptions, DefinitionProcessingOption.ONLY_IF_EXISTS);
            Collection<SelectorOptions<GetOperationOptions>> readOptions =
                    getOpOptions != null ? SelectorOptions.createCollection(getOpOptions) : null;
            ModelCompareOptions compareOptions = ModelCompareOptions.fromRestOptions(restCompareOptions);
            CompareResultType compareResult = modelService.compareObject(clientObject, readOptions, compareOptions, ignoreItemPaths, task, result);

            response = createResponse(HttpStatus.OK, compareResult, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @GetMapping(value = "/log/size",
            produces = { MediaType.TEXT_PLAIN_VALUE, MimeTypeUtils.ALL_VALUE })
    public ResponseEntity<?> getLogFileSize() {

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("getLogFileSize");

        ResponseEntity<?> response;
        try {
            long size = modelDiagnosticService.getLogFileSize(task, result);
            response = createResponse(HttpStatus.OK, String.valueOf(size), result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @GetMapping(value = "/log",
            produces = { MediaType.TEXT_PLAIN_VALUE, MimeTypeUtils.ALL_VALUE })
    public ResponseEntity<?> getLog(
            @RequestParam(value = "fromPosition", required = false) Long fromPosition,
            @RequestParam(value = "maxSize", required = false) Long maxSize) {

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("getLog");

        ResponseEntity<?> response;
        try {
            LogFileContentType content = modelDiagnosticService.getLogFileContent(fromPosition, maxSize, task, result);

            ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
            builder.header("ReturnedDataPosition", String.valueOf(content.getAt()));
            builder.header("ReturnedDataComplete", String.valueOf(content.isComplete()));
            builder.header("CurrentLogFileSize", String.valueOf(content.getLogFileSize()));
            response = builder.body(content.getContent());
        } catch (Exception ex) {
            LoggingUtils.logUnexpectedException(logger, "Cannot get log file content: fromPosition={}, maxSize={}", ex, fromPosition, maxSize);
            response = handleExceptionNoLog(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @PostMapping("/users/{oid}/credential")
    public ResponseEntity<?> executeCredentialReset(
            @PathVariable("oid") String oid,
            @RequestBody ExecuteCredentialResetRequestType executeCredentialResetRequest) {
        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("executeCredentialReset");

        ResponseEntity<?> response;
        try {
            PrismObject<UserType> user = modelService.getObject(UserType.class, oid, null, task, result);

            ExecuteCredentialResetResponseType executeCredentialResetResponse = modelInteraction.executeCredentialsReset(user, executeCredentialResetRequest, task, result);
            response = createResponse(HttpStatus.OK, executeCredentialResetResponse, result);
        } catch (Exception ex) {
            response = handleException(result, ex);
        }

        result.computeStatus();
        finishRequest(task, result);
        return response;

    }

    @GetMapping(value = "/threads",
            produces = { MediaType.TEXT_PLAIN_VALUE, MimeTypeUtils.ALL_VALUE })
    public ResponseEntity<?> getThreadsDump() {
        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("getThreadsDump");

        ResponseEntity<?> response;
        try {
            String dump = taskService.getThreadsDump(task, result);
            response = ResponseEntity.ok(dump);
        } catch (Exception ex) {
            LoggingUtils.logUnexpectedException(logger, "Cannot get threads dump", ex);
            response = handleExceptionNoLog(result, ex);
        }
        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @GetMapping(value = "/tasks/threads",
            produces = { MediaType.TEXT_PLAIN_VALUE, MimeTypeUtils.ALL_VALUE })
    public ResponseEntity<?> getRunningTasksThreadsDump() {

        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("getRunningTasksThreadsDump");

        ResponseEntity<?> response;
        try {
            String dump = taskService.getRunningTasksThreadsDump(task, result);
            response = ResponseEntity.ok(dump);
        } catch (Exception ex) {
            LoggingUtils.logUnexpectedException(logger, "Cannot get running tasks threads dump", ex);
            response = handleExceptionNoLog(result, ex);
        }
        result.computeStatus();
        finishRequest(task, result);
        return response;
    }

    @GetMapping(value = "/tasks/{oid}/threads",
            produces = { MediaType.TEXT_PLAIN_VALUE, MimeTypeUtils.ALL_VALUE })
    public ResponseEntity<?> getTaskThreadsDump(
            @PathVariable("oid") String oid) {
        Task task = initRequest();
        OperationResult result = task.getResult().createSubresult("getTaskThreadsDump");

        ResponseEntity<?> response;
        try {
            String dump = taskService.getTaskThreadsDump(oid, task, result);
            response = ResponseEntity.ok(dump);
        } catch (Exception ex) {
            LoggingUtils.logUnexpectedException(logger, "Cannot get task threads dump for task " + oid, ex);
            response = handleExceptionNoLog(result, ex);
        }
        result.computeStatus();
        finishRequest(task, result);
        return response;
    }
}
