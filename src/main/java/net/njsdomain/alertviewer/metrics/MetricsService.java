package net.njsdomain.alertviewer.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class MetricsService {

    @Autowired
    private MeterRegistry meterRegistry;

    Counter newFatalCounter;
    Counter newErrorCounter;
    Counter newWarnCounter;

    Counter repeatFatalRegexCounter;
    Counter repeatErrorRegexCounter;
    Counter repeatWarnRegexCounter;

    Counter repeatFatalDirectCounter;
    Counter repeatErrorDirectCounter;
    Counter repeatWarnDirectCounter;

    Counter watchedFatalCounter;
    Counter watchedErrorCounter;
    Counter watchedWarnCounter;

    //Gauge fatalGauge;
    //Gauge errorsGauge;
    //Gauge warnGauge;

    @PostConstruct
    private void init() {
        newFatalCounter = Counter.builder("log.entry.new").tag("type", "fatal").register(meterRegistry);
        newErrorCounter = Counter.builder("log.entry.new").tag("type", "error").register(meterRegistry);
        newWarnCounter = Counter.builder("log.entry.new").tag("type", "warn").register(meterRegistry);

        repeatFatalRegexCounter = Counter.builder("log.entry.repeat").tag("type", "fatal").tag("regex", "true").register(meterRegistry);
        repeatErrorRegexCounter = Counter.builder("log.entry.repeat").tag("type", "error").tag("regex", "true").register(meterRegistry);
        repeatWarnRegexCounter = Counter.builder("log.entry.repeat").tag("type", "warn").tag("regex", "true").register(meterRegistry);

        repeatFatalDirectCounter = Counter.builder("log.entry.repeat").tag("type", "fatal").tag("regex", "false").register(meterRegistry);
        repeatErrorDirectCounter = Counter.builder("log.entry.repeat").tag("type", "error").tag("regex", "false").register(meterRegistry);
        repeatWarnDirectCounter = Counter.builder("log.entry.repeat").tag("type", "warn").tag("regex", "false").register(meterRegistry);

        watchedFatalCounter = Counter.builder("log.entry.watch").tag("type", "fatal").register(meterRegistry);
        watchedErrorCounter = Counter.builder("log.entry.watch").tag("type", "error").register(meterRegistry);
        watchedWarnCounter = Counter.builder("log.entry.watch").tag("type", "warn").register(meterRegistry);
    }

    public void incNewFatal() {
        newFatalCounter.increment();
    }
    public void incNewError() {
        newErrorCounter.increment();
    }
    public void incNewWarn() {
        newWarnCounter.increment();
    }

    public void incRepeatFatalRegex() {
        repeatFatalRegexCounter.increment();
    }
    public void incRepeatErrorRegex() {
        repeatErrorRegexCounter.increment();
    }
    public void incRepeatWarnRegex() {
        repeatWarnRegexCounter.increment();
    }

    public void incRepeatFatalDirect() {
        repeatFatalDirectCounter.increment();
    }
    public void incRepeatErrorDirect() {
        repeatErrorDirectCounter.increment();
    }
    public void incRepeatWarnDirect() {
        repeatWarnDirectCounter.increment();
    }

    public void incWatchedFatal() {
        watchedFatalCounter.increment();
    }
    public void incWatchedError() {
        watchedErrorCounter.increment();
    }
    public void incWatchedWarn() {
        watchedWarnCounter.increment();
    }

}
