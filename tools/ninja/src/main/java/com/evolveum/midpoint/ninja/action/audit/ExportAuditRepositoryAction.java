/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.action.audit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import com.evolveum.midpoint.ninja.action.AbstractRepositorySearchAction;
import com.evolveum.midpoint.ninja.action.ExportRepositoryAction;
import com.evolveum.midpoint.ninja.action.RepositoryAction;
import com.evolveum.midpoint.ninja.action.worker.ProgressReporterWorker;
import com.evolveum.midpoint.ninja.impl.LogTarget;
import com.evolveum.midpoint.ninja.opts.ExportOptions;
import com.evolveum.midpoint.ninja.util.NinjaUtils;
import com.evolveum.midpoint.ninja.util.OperationStatus;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.QueryFactory;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventRecordType;

/**
 * Similar to normal repository {@link ExportRepositoryAction}, but not extended from
 * {@link AbstractRepositorySearchAction} because we need containers here and objects are quite
 * deeply embedded in the existing classes.
 */
public class ExportAuditRepositoryAction extends RepositoryAction<ExportOptions> {

    private static final int QUEUE_CAPACITY_PER_THREAD = 100;
    private static final long CONSUMERS_WAIT_FOR_START = 2000L;

    public static final String OPERATION_SHORT_NAME = "exportAudit";

    protected Runnable createConsumer(
            BlockingQueue<AuditEventRecordType> queue, OperationStatus operation) {
        return new ExportAuditConsumerWorker(context, options, queue, operation);
    }

    protected String getOperationName() {
        return this.getClass().getName() + "." + OPERATION_SHORT_NAME;
    }

    @Override
    public void execute() throws Exception {
        OperationResult result = new OperationResult(getOperationName());
        OperationStatus operation = new OperationStatus(context, result);

        // "+ 2" will be used for consumer and progress reporter
        ExecutorService executor = Executors.newFixedThreadPool(options.getMultiThread() + 2);

        BlockingQueue<AuditEventRecordType> queue =
                new LinkedBlockingQueue<>(QUEUE_CAPACITY_PER_THREAD * options.getMultiThread());

        List<AuditExportProducerWorker> producers = createProducers(queue, operation);

        log.info("Starting " + OPERATION_SHORT_NAME);
        operation.start();

        // execute as many producers as there are threads for them
        for (int i = 0; i < producers.size() && i < options.getMultiThread(); i++) {
            executor.execute(producers.get(i));
        }

        Thread.sleep(CONSUMERS_WAIT_FOR_START);

        executor.execute(new ProgressReporterWorker<>(context, options, queue, operation));

        Runnable consumer = createConsumer(queue, operation);
        executor.execute(consumer);

        // execute rest of the producers
        for (int i = options.getMultiThread(); i < producers.size(); i++) {
            executor.execute(producers.get(i));
        }

        executor.shutdown();
        boolean awaitResult = executor.awaitTermination(NinjaUtils.WAIT_FOR_EXECUTOR_FINISH, TimeUnit.DAYS);
        if (!awaitResult) {
            log.error("Executor did not finish before timeout");
        }

        handleResultOnFinish(operation, "Finished " + OPERATION_SHORT_NAME);
    }

    @Override
    public LogTarget getInfoLogTarget() {
        if (options.getOutput() != null) {
            return LogTarget.SYSTEM_OUT;
        }

        return LogTarget.SYSTEM_ERR;
    }

    private List<AuditExportProducerWorker> createProducers(
            BlockingQueue<AuditEventRecordType> queue, OperationStatus operation)
            throws SchemaException, IOException {

        QueryFactory queryFactory = context.getPrismContext().queryFactory();
        List<AuditExportProducerWorker> producers = new ArrayList<>();

        if (options.getOid() != null) {
            log.info("OID is ignored for audit export");
        }

        ObjectFilter filter = NinjaUtils.createObjectFilter(options.getFilter(), context, AuditEventRecordType.class);
        ObjectQuery query = queryFactory.createQuery(filter);

        producers.add(new AuditExportProducerWorker(context, options, queue, operation, producers, query));

        return producers;
    }
}
