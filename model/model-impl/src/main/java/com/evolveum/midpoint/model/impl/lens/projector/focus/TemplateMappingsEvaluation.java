/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.lens.projector.focus;

import com.evolveum.midpoint.model.common.mapping.MappingEvaluationEnvironment;
import com.evolveum.midpoint.model.common.util.ObjectTemplateIncludeProcessor;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.model.impl.lens.ItemValueWithOrigin;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.model.impl.lens.LensFocusContext;
import com.evolveum.midpoint.model.impl.lens.LensUtil;
import com.evolveum.midpoint.model.impl.lens.projector.focus.consolidation.DeltaSetTripleMapConsolidation;
import com.evolveum.midpoint.model.impl.lens.projector.mappings.*;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.delta.DeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.PathKeyedMap;
import com.evolveum.midpoint.prism.path.UniformItemPath;
import com.evolveum.midpoint.prism.util.ObjectDeltaObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.*;
import java.util.function.Function;

import static com.evolveum.midpoint.model.impl.lens.LensUtil.setMappingTarget;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectTemplateMappingEvaluationPhaseType.BEFORE_ASSIGNMENTS;

/**
 * Evaluation of object template mappings.
 *
 * Source: template and the whole context (focus, deltas, target in case of personas)
 * Target: delta set triple, item deltas, next recompute
 *
 * Primarily deals with handling object template data. The real computation is delegated to
 *
 * - {@link FocalMappingSetEvaluation} (mappings -> triples)
 * - {@link DeltaSetTripleMapConsolidation} (triples -> item deltas)
 */
public class TemplateMappingsEvaluation<F extends AssignmentHolderType, T extends AssignmentHolderType> {

    // The logger name is intentionally different because of the backward compatibility.
    private static final Trace LOGGER = TraceManager.getTrace(ObjectTemplateProcessor.class);

    /**
     * Useful Spring beans.
     */
    private final ModelBeans beans;

    /**
     * Overall lens context.
     */
    private final LensContext<F> context;

    /**
     * Focus context.
     */
    private final LensFocusContext<F> focusContext;

    /**
     * ODO for the current focus.
     *
     * In some special cases (e.g. persona addition) it may be artificially created,
     * therefore it is not derived from the focus context.
     */
    private final ObjectDeltaObject<F> focusOdo;

    /**
     * Template that is to be used.
     */
    private final ObjectTemplateType template;

    /**
     * Evaluation environment (context description, now, task).
     */
    private final MappingEvaluationEnvironment env;

    /**
     * Evaluation phase. For persona mappings it is BEFORE_ASSIGNMENTS.
     */
    private final ObjectTemplateMappingEvaluationPhaseType phase;

    /**
     * Current operation result.
     */
    private final OperationResult result;

    /**
     * Iteration to be used in computations. For persona mappings it is zero (why?).
     */
    private final int iteration;

    /**
     * Iteration token to be used in computations. For persona mappings it is null (why?).
     */
    private final String iterationToken;

    /**
     * Target object to which the items are to be applied.
     * For standard template processing this is the current object.
     * For persona template processing this is the new (persona) object.
     */
    private final TargetObjectSpecification<T> targetSpecification;

    /**
     * Definition of the target object.
     */
    private final PrismObjectDefinition<T> targetDefinition;

    /**
     * A priori delta for target object.
     */
    private final ObjectDelta<T> targetAPrioriDelta;

    /**
     * Whether item delta exists for a given target item.
     */
    private final Function<ItemPath, Boolean> itemDeltaExistsProvider;

    //region Intermediary data
    /**
     * Collected item definitions from the template and all included templates.
     */
    private final PathKeyedMap<ObjectTemplateItemDefinitionType> itemDefinitionsMap = new PathKeyedMap<>();

    /**
     * Collected mappings:
     * - mappings embedded in item definitions in the template
     * - standalone mappings in the template
     * - collected auto-assignment mappings
     */
    private final List<FocalMappingEvaluationRequest<?, ?>> mappings = new ArrayList<>();
    //endregion

    //region Results of the evaluation
    /**
     * Result of the computation: evaluation of the mappings.
     */
    private FocalMappingSetEvaluation<F, T> mappingSetEvaluation;

    /**
     * Consolidation of output triple map to item deltas.
     */
    private DeltaSetTripleMapConsolidation<T> consolidation;
    //endregion

    private TemplateMappingsEvaluation(ModelBeans beans, LensContext<F> context,
            ObjectDeltaObject<F> focusOdo,
            ObjectTemplateMappingEvaluationPhaseType phase,
            ObjectTemplateType template,
            int iteration, String iterationToken,
            TargetObjectSpecification<T> targetSpecification, ObjectDelta<T> targetAPrioriDelta,
            Function<ItemPath, Boolean> itemDeltaExistsProvider,
            PrismObjectDefinition<T> targetDefinition,
            String parentContextDesc, XMLGregorianCalendar now, Task task, OperationResult result) {
        this.beans = beans;
        this.context = context;
        this.focusContext = context.getFocusContext();
        this.focusOdo = focusOdo;
        this.template = template;
        this.phase = phase;
        this.iteration = iteration;
        this.iterationToken = iterationToken;
        this.targetSpecification = targetSpecification;
        this.targetAPrioriDelta = targetAPrioriDelta;
        this.itemDeltaExistsProvider = itemDeltaExistsProvider;
        this.targetDefinition = targetDefinition;
        this.env = new MappingEvaluationEnvironment(getContextDescription(parentContextDesc), now, task);
        this.result = result;
    }

    static <AH extends AssignmentHolderType> TemplateMappingsEvaluation<AH, AH> createForStandardTemplate(
            ModelBeans beans, LensContext<AH> context, ObjectTemplateMappingEvaluationPhaseType phase,
            XMLGregorianCalendar now, Task task, OperationResult result) throws SchemaException {
        LensFocusContext<AH> focusContext = context.getFocusContextRequired();
        TargetObjectSpecification<AH> targetSpecification = new FixedTargetSpecification<>(focusContext.getObjectNew(), true);
        return new TemplateMappingsEvaluation<>(beans, context,
                focusContext.getObjectDeltaObjectRelative(),
                phase,
                context.getFocusTemplate(),
                focusContext.getIteration(), focusContext.getIterationToken(),
                targetSpecification, focusContext.getCurrentDelta(),
                context::primaryFocusItemDeltaExists,
                focusContext.getObjectDefinition(),
                "focus " + focusContext.getObjectAny(), now, task, result);
    }

    public static <F extends AssignmentHolderType, T extends AssignmentHolderType>
    TemplateMappingsEvaluation<F, T> createForPersonaTemplate(
            ModelBeans beans, LensContext<F> context,
            ObjectDeltaObject<F> focusOdoAbsolute,
            ObjectTemplateType template,
            @NotNull PrismObject<T> targetObject, ObjectDelta<T> targetAPrioriDelta,
            String contextDescription,
            XMLGregorianCalendar now, Task task, OperationResult result) {
        TargetObjectSpecification<T> targetSpecification = new FixedTargetSpecification<>(targetObject, false);
        return new TemplateMappingsEvaluation<>(beans, context,
                focusOdoAbsolute,
                BEFORE_ASSIGNMENTS,
                template,
                0, null,
                targetSpecification, targetAPrioriDelta,
                itemPath -> targetAPrioriDelta != null && targetAPrioriDelta.findItemDelta(itemPath) != null,
                targetObject.getDefinition(),
                contextDescription, now, task, result);
    }

    public void computeItemDeltas() throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, PolicyViolationException,
            SecurityViolationException, ConfigurationException, CommunicationException {

        LOGGER.trace("Applying object template {} to {} (target {}), iteration {} ({}), phase {}",
                template, focusContext.getObjectNew(), targetSpecification.getTargetObject(), iteration, iterationToken, phase);

        collectDefinitionsAndMappings();
        evaluateMappings();
        consolidateToItemDeltas();
    }

    private void evaluateMappings() throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException,
            PolicyViolationException, SecurityViolationException, ConfigurationException, CommunicationException {

        mappingSetEvaluation = new FocalMappingSetEvaluationBuilder<F, T>()
                .context(context)
                .evaluationRequests(mappings)
                .phase(phase)
                .focusOdo(focusOdo)
                .targetSpecification(targetSpecification)
                .iteration(iteration)
                .iterationToken(iterationToken)
                .beans(beans)
                .env(env)
                .result(result)
                .build();
        mappingSetEvaluation.evaluateMappingsToTriples();
    }

    private void consolidateToItemDeltas() throws ExpressionEvaluationException, SchemaException,
            ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException {
        PathKeyedMap<DeltaSetTriple<ItemValueWithOrigin<?, ?>>> outputTripleMap = mappingSetEvaluation.getOutputTripleMap();
        LOGGER.trace("outputTripleMap before item delta computation:\n{}", DebugUtil.debugDumpMapMultiLineLazily(outputTripleMap));

        // TODO for chained mappings: what exactly should be the target object?
        //  What is used here is the original focus odo, which is maybe correct.
        PrismObject<T> targetObject = targetSpecification.getTargetObject();

        consolidation = new DeltaSetTripleMapConsolidation<>(outputTripleMap, targetObject, targetAPrioriDelta, itemDeltaExistsProvider,
                null, null, targetDefinition, env, beans, context, result);
        consolidation.computeItemDeltas();
    }

    private void collectDefinitionsAndMappings() throws SchemaException, ObjectNotFoundException, CommunicationException,
            ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        collectItemDefinitionsFromTemplate();
        collectMappingsFromTemplate();
        beans.autoAssignMappingCollector.collectAutoassignMappings(context, mappings, result);
    }

    private void collectItemDefinitionsFromTemplate() throws SchemaException,
            ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException,
            ExpressionEvaluationException {
        if (template != null) {
            new ObjectTemplateIncludeProcessor(beans.modelObjectResolver)
                    .processThisAndIncludedTemplates(template, env.contextDescription, env.task, result,
                            this::collectLocalItemDefinitions);
        }
    }

    private void collectLocalItemDefinitions(ObjectTemplateType objectTemplate) {
        for (ObjectTemplateItemDefinitionType def : objectTemplate.getItem()) {
            if (def.getRef() == null) {
                throw new IllegalStateException("Item definition with null ref in " + env.contextDescription);
            }
            UniformItemPath itemPath = beans.prismContext.toUniformPath(def.getRef());
            LensUtil.rejectNonTolerantSettingIfPresent(def, itemPath, env.contextDescription);

            // TODO check for incompatible overrides
            itemDefinitionsMap.put(itemPath, def);
        }
    }

    private void collectMappingsFromTemplate()
            throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, SecurityViolationException, ConfigurationException, CommunicationException {
        if (template != null) {
            new ObjectTemplateIncludeProcessor(beans.modelObjectResolver)
                    .processThisAndIncludedTemplates(template, env.contextDescription, env.task, result,
                            this::collectLocalMappings);
        }
    }

    private void collectLocalMappings(ObjectTemplateType objectTemplate) {
        for (ObjectTemplateMappingType mapping: objectTemplate.getMapping()) {
            mappings.add(new TemplateMappingEvaluationRequest(mapping, objectTemplate));
        }
        for (ObjectTemplateItemDefinitionType templateItemDefType: objectTemplate.getItem()) {
            for (ObjectTemplateMappingType mapping: templateItemDefType.getMapping()) {
                mapping = setMappingTarget(mapping, templateItemDefType.getRef());
                mappings.add(new TemplateMappingEvaluationRequest(mapping, objectTemplate));
            }
        }
    }

    private String getContextDescription(String parentContextDescription) {
        if (template != null) {
            return "object template " + template + " for " + parentContextDescription;
        } else {
            return "no object template for " + parentContextDescription; // Is this really needed?
        }
    }

    PathKeyedMap<ObjectTemplateItemDefinitionType> getItemDefinitionsMap() {
        return itemDefinitionsMap;
    }

    public Collection<ItemDelta<?, ?>> getItemDeltas() {
        return consolidation.getItemDeltas();
    }

    public LensFocusContext<F> getFocusContext() {
        return focusContext;
    }

    NextRecompute getNextRecompute() {
        return mappingSetEvaluation.getNextRecompute();
    }
}
