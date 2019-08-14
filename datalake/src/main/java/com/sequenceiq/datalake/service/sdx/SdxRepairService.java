package com.sequenceiq.datalake.service.sdx;

import static com.sequenceiq.cloudbreak.exception.NotFoundException.notFound;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.dyngr.Polling;
import com.dyngr.core.AttemptResult;
import com.dyngr.core.AttemptResults;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.ClusterRepairV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.StackV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.cluster.ClusterV4Response;
import com.sequenceiq.cloudbreak.auth.altus.Crn;
import com.sequenceiq.cloudbreak.auth.altus.CrnParseException;
import com.sequenceiq.cloudbreak.client.CloudbreakServiceUserCrnClient;
import com.sequenceiq.cloudbreak.common.exception.ClientErrorExceptionHandler;
import com.sequenceiq.cloudbreak.common.json.JsonUtil;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.datalake.controller.exception.BadRequestException;
import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.datalake.entity.SdxClusterStatus;
import com.sequenceiq.datalake.flow.SdxReactorFlowManager;
import com.sequenceiq.datalake.repository.SdxClusterRepository;
import com.sequenceiq.sdx.api.model.SdxClusterRequest;
import com.sequenceiq.sdx.api.model.SdxRepairRequest;

@Service
public class SdxRepairService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SdxRepairService.class);

    @Inject
    private SdxClusterRepository sdxClusterRepository;

    @Inject
    private SdxReactorFlowManager sdxReactorFlowManager;

    @Inject
    private CloudbreakServiceUserCrnClient cloudbreakClient;

    @Inject
    private SdxService sdxService;

    public void triggerRepairByCrn(String userCrn, String clusterCrn, SdxRepairRequest clusterRepairRequest) {
        SdxCluster cluster = sdxService.getByCrn(userCrn, clusterCrn);
        MDCBuilder.buildMdcContext(cluster);
        sdxReactorFlowManager.triggerSdxRepairFlow(cluster.getId(), clusterRepairRequest);
    }

    public void triggerRepairByName(String userCrn, String clusterName, SdxRepairRequest clusterRepairRequest) {
        SdxCluster cluster = sdxService.getSdxByNameInAccount(userCrn, clusterName);
        MDCBuilder.buildMdcContext(cluster);
        sdxReactorFlowManager.triggerSdxRepairFlow(cluster.getId(), clusterRepairRequest);
    }

    public void startSdxRepair(Long id, SdxRepairRequest repairRequest) {
        sdxClusterRepository.findById(id).ifPresentOrElse(sdxCluster -> {
            startRepairInCb(sdxCluster, repairRequest);
        }, () -> {
            throw notFound("SDX cluster", id).get();
        });
    }

    protected void startRepairInCb(SdxCluster sdxCluster, SdxRepairRequest repairRequest) {
        try {
            LOGGER.info("Triggering repair flow for cluster {} with hostgroups {}", sdxCluster.getClusterName(), repairRequest.getHostGroupName());
            cloudbreakClient.withCrn(sdxCluster.getInitiatorUserCrn())
                    .stackV4Endpoint()
                    .repairCluster(0L, sdxCluster.getClusterName(), createRepairRequest(repairRequest));
            sdxCluster.setStatus(SdxClusterStatus.REPAIR_IN_PROGRESS);
            sdxClusterRepository.save(sdxCluster);
        } catch (NotFoundException e) {
            LOGGER.info("Can not find stack on cloudbreak side {}", sdxCluster.getClusterName());
        } catch (ClientErrorException e) {
            String errorMessage = ClientErrorExceptionHandler.getErrorMessage(e);
            LOGGER.info("Can not delete stack {} from cloudbreak: {}", sdxCluster.getStackId(), errorMessage, e);
            throw new RuntimeException("Can not delete stack, client error happened on Cloudbreak side: " + errorMessage);
        }
    }

    private ClusterRepairV4Request createRepairRequest(SdxRepairRequest sdxRepairRequest) {
        ClusterRepairV4Request repairRequest = new ClusterRepairV4Request();
        repairRequest.setHostGroups(List.of(sdxRepairRequest.getHostGroupName()));
        return repairRequest;
    }

    public void waitCloudbreakClusterRepair(Long id, PollingConfig pollingConfig) {
        sdxClusterRepository.findById(id).ifPresentOrElse(sdxCluster -> {
            Polling.waitPeriodly(pollingConfig.getSleepTime(), pollingConfig.getSleepTimeUnit())
                    .stopIfException(pollingConfig.getStopPollingIfExceptionOccured())
                    .stopAfterDelay(pollingConfig.getDuration(), pollingConfig.getDurationTimeUnit())
                    .run(() -> checkClusterStatusDuringRepair(sdxCluster));
            sdxCluster.setStatus(SdxClusterStatus.RUNNING);
            sdxClusterRepository.save(sdxCluster);
        }, () -> {
            throw notFound("SDX cluster", id).get();
        });
    }

    protected AttemptResult<StackV4Response> checkClusterStatusDuringRepair(SdxCluster sdxCluster) throws JsonProcessingException {
        LOGGER.info("Repair polling cloudbreak for stack status: '{}' in '{}' env", sdxCluster.getClusterName(), sdxCluster.getEnvName());
        try {
            StackV4Response stackV4Response = cloudbreakClient.withCrn(sdxCluster.getInitiatorUserCrn())
                    .stackV4Endpoint()
                    .get(0L, sdxCluster.getClusterName(), Collections.emptySet());
            LOGGER.info("Response from cloudbreak: {}", JsonUtil.writeValueAsString(stackV4Response));
            ClusterV4Response cluster = stackV4Response.getCluster();
            if (stackAndClusterAvailable(stackV4Response, cluster)) {
                return AttemptResults.finishWith(stackV4Response);
            } else {
                if (Status.UPDATE_FAILED.equals(stackV4Response.getStatus())) {
                    LOGGER.info("Stack repair failed for Stack {} with status {}", stackV4Response.getName(), stackV4Response.getStatus());
                    return sdxRepairFailed(sdxCluster, stackV4Response.getStatusReason());
                } else if (Status.UPDATE_FAILED.equals(stackV4Response.getCluster().getStatus())) {
                    LOGGER.info("Cluster repair failed for Cluster {} status {}", stackV4Response.getCluster().getName(),
                            stackV4Response.getCluster().getStatus());
                    return sdxRepairFailed(sdxCluster, stackV4Response.getCluster().getStatusReason());
                } else {
                    return AttemptResults.justContinue();
                }
            }
        } catch (NotFoundException e) {
            LOGGER.debug("Stack not found on CB side " + sdxCluster.getClusterName(), e);
            return AttemptResults.breakFor("Stack not found on CB side " + sdxCluster.getClusterName());
        }
    }

    private AttemptResult<StackV4Response> sdxRepairFailed(SdxCluster sdxCluster, String statusReason) {
        LOGGER.info("SDX repair failed, statusReason: " + statusReason);
        return AttemptResults.breakFor("SDX repair failed '" + sdxCluster.getClusterName() + "', " + statusReason);
    }

    private boolean stackAndClusterAvailable(StackV4Response stackV4Response, ClusterV4Response cluster) {
        return stackV4Response.getStatus().isAvailable()
                && cluster != null
                && cluster.getStatus() != null
                && cluster.getStatus().isAvailable();
    }

    private boolean isCloudStorageConfigured(SdxClusterRequest clusterRequest) {
        return clusterRequest.getCloudStorage() != null
                && StringUtils.isNotEmpty(clusterRequest.getCloudStorage().getBaseLocation());
    }

    private String getAccountIdFromCrn(String userCrn) {
        try {
            Crn crn = Crn.safeFromString(userCrn);
            return crn.getAccountId();
        } catch (NullPointerException | CrnParseException e) {
            throw new BadRequestException("Can not parse CRN to find account ID: " + userCrn);
        }
    }
}