package com.sequenceiq.cloudbreak.controller.mapper;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import com.sequenceiq.cloudbreak.service.StackUnderOperationService;
import com.sequenceiq.cloudbreak.service.events.CloudbreakEventService;

abstract class SendNotificationExceptionMapper<E extends Throwable> extends BaseExceptionMapper<E> {

    @Inject
    private StackUnderOperationService stackUnderOperationService;

    @Inject
    private CloudbreakEventService eventService;

    @Override
    public Response toResponse(E exception) {
        Long stackId = stackUnderOperationService.get();
        if (stackId != null) {
            eventService.fireCloudbreakEvent(stackId, "BAD_REQUEST", exception.getMessage());
        }
        return super.toResponse(exception);
    }
}
