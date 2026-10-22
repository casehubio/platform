package io.casehub.platform.compiler;

import io.casehub.yaml.core.orchestration.DurationParser;
import org.openworkflow.sdk.api.types.DurationInline;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.api.types.TaskItem;
import org.openworkflow.sdk.api.types.TimeoutAfter;
import org.openworkflow.sdk.api.types.WaitTask;

import java.time.Duration;
import java.util.Map;

public class DelayCompiler implements StepCompiler {

    @Override
    public TaskItem compile(String stepKey, Object stepValue, Map<String, Object> fullStep, CompilationContext ctx) {
        String name = ctx.generateName("wait");
        Duration duration = DurationParser.parse(String.valueOf(stepValue));
        long millis = duration.toMillis();
        var durationInline = new DurationInline();
        if (millis >= 1000 && millis % 1000 == 0) {
            durationInline.withSeconds((int) (millis / 1000));
        } else {
            durationInline.withMilliseconds((int) millis);
        }
        var timeoutAfter = new TimeoutAfter().withDurationInline(durationInline);
        return new TaskItem(name, new Task().withWaitTask(new WaitTask().withWait(timeoutAfter)));
    }
}
