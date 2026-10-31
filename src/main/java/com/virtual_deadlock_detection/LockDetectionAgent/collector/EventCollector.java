
package com.virtual_deadlock_detection.LockDetectionAgent.collector;

import com.virtual_deadlock_detection.LockDetectionAgent.dto.EventDTO;
import com.virtual_deadlock_detection.LockDetectionAgent.runtime.InternalThreads;
import com.virtual_deadlock_detection.LockDetectionAgent.sender.EventSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventCollector {

    private static final Logger log =
            LoggerFactory.getLogger(EventCollector.class);

    private final EventSender sender = new EventSender();

    public void collect(EventDTO eventDTO) {

        if (eventDTO == null) {
            return;
        }

        // Ignore internal agent threads.
        if (InternalThreads.isInternal(eventDTO.getThreadName())
                || InternalThreads.isInternal(eventDTO.getOwnerThreadName())
                || InternalThreads.isInternal(eventDTO.getCarrierThreadName())) {
            return;
        }

        log.debug(
                "Event Collected - Type: {}, Thread: {}",
                eventDTO.getEventType(),
                eventDTO.getThreadName()
        );

        sender.send(eventDTO);
    }

    /**
     * Sends the final monitoring shutdown notification.
     */
    public void stopMonitoring() {

        log.info("Sending monitoring shutdown notification to DIXY");

        sender.sendMonitorStop();
    }
}
