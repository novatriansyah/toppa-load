package com.toppaload.notification.internal;

import com.toppaload.allocation.AllocationCreatedEvent;
import com.toppaload.notification.NotificationPublisher;
import com.toppaload.notification.NotificationType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class AllocationNotificationListener {

    private final NotificationPublisher notificationPublisher;

    AllocationNotificationListener(NotificationPublisher notificationPublisher) {
        this.notificationPublisher = notificationPublisher;
    }

    @EventListener
    public void onAllocationCreated(AllocationCreatedEvent event) {
        notificationPublisher.publishIfAbsent(
                event.userId(),
                NotificationType.ALLOCATION_ASSIGNED,
                "Penugasan Alokasi Proyek: " + event.projectName(),
                Map.of(
                        "allocationId", event.allocationId().toString(),
                        "projectName", event.projectName(),
                        "startDate", event.startDate().toString(),
                        "endDate", event.endDate().toString(),
                        "hoursPerDay", event.hoursPerDay().toString(),
                        "allocationType", event.allocationType().name()
                ),
                "ALLOC_ASSIGN:" + event.allocationId()
        );
    }
}

