package com.sequenceiq.datalake.flow;

import static com.sequenceiq.datalake.flow.create.SdxCreateEvent.ENV_WAIT_EVENT;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.SDX_DELETE_EVENT;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.common.event.Acceptable;
import com.sequenceiq.cloudbreak.exception.CloudbreakApiException;
import com.sequenceiq.cloudbreak.exception.FlowsAlreadyRunningException;
import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.datalake.service.sdx.SdxService;
import com.sequenceiq.flow.core.Flow2Handler;
import com.sequenceiq.flow.reactor.ErrorHandlerAwareReactorEventFactory;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Service
public class SdxReactorFlowManager {

    private static final long WAIT_FOR_ACCEPT = 10L;

    @Inject
    private EventBus reactor;

    @Inject
    private ErrorHandlerAwareReactorEventFactory eventFactory;

    @Inject
    private SdxService sdxService;

    @Inject
    private ThreadBasedUserCrnProvider threadBasedUserCrnProvider;

    public void triggerSdxCreation(Long sdxId) {
        String selector = ENV_WAIT_EVENT.event();
        String userId = threadBasedUserCrnProvider.getUserCrn();
        notify(selector, new SdxEvent(selector, sdxId, userId));
    }

    public void triggerSdxDeletion(Long sdxId) {
        String selector = SDX_DELETE_EVENT.event();
        String userId = threadBasedUserCrnProvider.getUserCrn();
        notify(selector, new SdxEvent(selector, sdxId, userId));
    }

    public void cancelRunningFlows(Long sdxId) {
        String userId = threadBasedUserCrnProvider.getUserCrn();
        SdxEvent cancelEvent = new SdxEvent(Flow2Handler.FLOW_CANCEL, sdxId, userId);
        reactor.notify(Flow2Handler.FLOW_CANCEL, eventFactory.createEventWithErrHandler(cancelEvent));
    }

    private void notify(String selector, Acceptable acceptable) {
        Event<Acceptable> event = eventFactory.createEventWithErrHandler(acceptable);

        SdxCluster sdxCluster = sdxService.getById(event.getData().getResourceId());

        reactor.notify(selector, event);
        try {
            Boolean accepted = true;
            if (event.getData().accepted() != null) {
                accepted = event.getData().accepted().await(WAIT_FOR_ACCEPT, TimeUnit.SECONDS);
            }
            if (accepted == null || !accepted) {
                throw new FlowsAlreadyRunningException(String.format("Sdx cluster %s has flows under operation, request not allowed.", sdxCluster.getId()));
            }
        } catch (InterruptedException e) {
            throw new CloudbreakApiException(e.getMessage());
        }

    }
}