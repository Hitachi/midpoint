/*
 * Copyright (C) 2019-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.test;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.tools.testng.MidpointTestContext;

/**
 * Value object carrying test context information like task, result and method name.
 * <p>
 * Static methods are used for creating the context and working with it via {@link ThreadLocal}.
 * <b>It is important to to call {@link #destroy()} at the end (in some after-method).</b>
 */
public final class MidpointTestContextWithTask implements MidpointTestContext {

    private static final ThreadLocal<MidpointTestContextWithTask> TEST_CONTEXT_THREAD_LOCAL =
            new ThreadLocal<>();

    /**
     * Actual test class - not abstract (where method may be implemented) but executed test class.
     */
    private final Class<?> testClass;

    /**
     * Test method name.
     */
    private final String methodName;

    /**
     * Task used to execute the test.
     */
    private final Task task;

    /**
     * Top-level operation result for test execution.
     */
    private final OperationResult result;

    private final String originalThreadName;

    private MidpointTestContextWithTask(
            Class<?> testClass, String methodName, Task task, OperationResult result) {

        this.testClass = testClass;
        this.methodName = methodName;
        this.task = task;
        this.result = result;
        this.originalThreadName = Thread.currentThread().getName();
    }

    @Override
    public Class<?> getTestClass() {
        return testClass;
    }

    @Override
    public String getTestMethodName() {
        return methodName;
    }

    public Task getTask() {
        return task;
    }

    public OperationResult getResult() {
        return result;
    }

    public static MidpointTestContextWithTask create(
            Class<?> testClass, String methodName, Task task, OperationResult result) {

        MidpointTestContextWithTask ctx =
                new MidpointTestContextWithTask(testClass, methodName, task, result);
        Thread.currentThread().setName(ctx.getTestName());
        TEST_CONTEXT_THREAD_LOCAL.set(ctx);
        return ctx;
    }

    public static MidpointTestContextWithTask get() {
        return TEST_CONTEXT_THREAD_LOCAL.get();
    }

    public static void destroy() {
        Thread.currentThread().setName(get().originalThreadName);
        TEST_CONTEXT_THREAD_LOCAL.remove();
    }
}
