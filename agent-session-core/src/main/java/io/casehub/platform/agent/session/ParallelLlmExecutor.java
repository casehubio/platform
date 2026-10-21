package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.gate.AimdAdmissionStrategy;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ParallelLlmExecutor {

    private static final Logger LOG = Logger.getLogger(ParallelLlmExecutor.class);
    private static final int DEFAULT_CONCURRENCY = 6;
    private static final long ADMISSION_TIMEOUT_SECONDS = 120;

    public record ExecutionResult(int tasksSubmitted, int tasksCompleted, long wallClockMs) {}

    private ParallelLlmExecutor() {}

    public static ExecutionResult execute(SessionPipeline pipeline, String admissionContext,
                                          int ceiling, String systemPrompt, String phaseName,
                                          List<Consumer<AgentSession>> tasks) {
        return execute(pipeline, admissionContext, ceiling, systemPrompt, phaseName, DEFAULT_CONCURRENCY, tasks);
    }

    public static ExecutionResult execute(SessionPipeline pipeline, String admissionContext,
                                          int ceiling, String systemPrompt, String phaseName,
                                          int concurrency, List<Consumer<AgentSession>> tasks) {
        if (tasks.isEmpty()) return new ExecutionResult(0, 0, 0);
        int poolSize = Math.min(concurrency, tasks.size());

        LOG.infof("[%s] starting %d tasks with %d sessions (pipeline)", phaseName, tasks.size(), poolSize);
        long startTime = System.currentTimeMillis();

        var sessionIds = new ArrayList<String>();
        var sessionPool = new ArrayBlockingQueue<AgentSession>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            String id = phaseName + ":" + i;
            AgentSession session = pipeline.openSession(id, AgentSessionInit.of(systemPrompt));
            if (session != null) {
                sessionIds.add(id);
                sessionPool.add(session);
            }
        }
        if (sessionIds.isEmpty()) {
            LOG.warnf("[%s] could not open any sessions — skipping", phaseName);
            return new ExecutionResult(tasks.size(), 0, System.currentTimeMillis() - startTime);
        }

        AimdAdmissionStrategy admission = pipeline.companionAdmission(admissionContext, ceiling);
        var completed = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();
            for (Consumer<AgentSession> task : tasks) {
                futures.add(executor.submit(() -> {
                    try {
                        if (!admission.tryAcquirePriority(Duration.ofSeconds(ADMISSION_TIMEOUT_SECONDS))) {
                            LOG.warnf("[%s] timeout acquiring admission permit", phaseName);
                            return null;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }

                    AgentSession session;
                    try {
                        session = sessionPool.take();
                    } catch (InterruptedException e) {
                        admission.release();
                        Thread.currentThread().interrupt();
                        return null;
                    }

                    try {
                        task.accept(session);
                        admission.release();
                        completed.incrementAndGet();
                    } catch (Exception e) {
                        if (SessionPipeline.isCapacityError(e)) {
                            admission.rollback();
                        } else {
                            admission.release();
                        }
                        LOG.warnf("[%s] task failed (non-fatal): %s", phaseName, e.getMessage());
                    } finally {
                        try {
                            sessionPool.put(session);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return null;
                }));
            }
            for (var f : futures) {
                try { f.get(); } catch (Exception e) { /* already logged */ }
            }
        }

        for (String id : sessionIds) {
            pipeline.releaseSession(id);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LOG.infof("[%s] completed %d/%d tasks in %dms", phaseName, completed.get(), tasks.size(), elapsed);
        return new ExecutionResult(tasks.size(), completed.get(), elapsed);
    }
}
