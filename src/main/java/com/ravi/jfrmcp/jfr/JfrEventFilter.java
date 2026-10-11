package com.ravi.jfrmcp.jfr;

import jdk.jfr.consumer.RecordedEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Filter criteria for selecting JFR recorded events during streaming.
 */
public class JfrEventFilter implements Predicate<RecordedEvent> {

    private final Set<String> eventNames;
    private final Instant startTime;
    private final Instant endTime;

    public JfrEventFilter(Collection<String> eventNames, Instant startTime, Instant endTime) {
        this.eventNames = eventNames != null ? new HashSet<>(eventNames) : null;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static JfrEventFilter ofTypes(String... types) {
        return new JfrEventFilter(Set.of(types), null, null);
    }

    public static JfrEventFilter ofTypes(Collection<String> types) {
        return new JfrEventFilter(types, null, null);
    }

    public static JfrEventFilter all() {
        return new JfrEventFilter(null, null, null);
    }

    @Override
    public boolean test(RecordedEvent event) {
        if (event == null) {
            return false;
        }

        if (eventNames != null && !eventNames.isEmpty()) {
            if (!eventNames.contains(event.getEventType().getName())) {
                return false;
            }
        }

        if (startTime != null && event.getStartTime().isBefore(startTime)) {
            return false;
        }

        if (endTime != null && event.getEndTime().isAfter(endTime)) {
            return false;
        }

        return true;
    }

    public Set<String> getEventNames() {
        return eventNames != null ? Collections.unmodifiableSet(eventNames) : Collections.emptySet();
    }
}
