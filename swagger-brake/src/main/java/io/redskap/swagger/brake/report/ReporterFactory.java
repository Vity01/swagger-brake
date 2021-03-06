package io.redskap.swagger.brake.report;

import java.util.Collection;
import java.util.stream.Collectors;

import io.redskap.swagger.brake.runner.Options;
import io.redskap.swagger.brake.runner.OutputFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReporterFactory {
    private final Collection<CheckableReporter> reporters;

    public Reporter create(Options options) {
        Collection<Reporter> reporters = options.getOutputFormats().stream().map(this::findReporters).flatMap(Collection::stream).collect(Collectors.toList());
        if (reporters.isEmpty()) {
            throw new IllegalStateException("No suitable reporters could be loaded");
        }
        return new CompositeReporter(reporters);
    }

    private Collection<Reporter> findReporters(OutputFormat outputFormat) {
        return reporters.stream().filter(r -> r.canReport(outputFormat)).collect(Collectors.toList());
    }
}
