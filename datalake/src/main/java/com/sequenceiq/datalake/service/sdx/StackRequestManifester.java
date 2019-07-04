package com.sequenceiq.datalake.service.sdx;

import static com.sequenceiq.cloudbreak.util.NullUtil.getIfNotNull;

import java.io.IOException;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.parameter.network.AwsNetworkV4Parameters;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.parameter.network.AzureNetworkV4Parameters;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.parameter.stack.YarnStackV4Parameters;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.StackV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.authentication.StackAuthenticationV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.ClusterV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.environment.placement.PlacementSettingsV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.network.NetworkV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.tags.TagsV4Request;
import com.sequenceiq.cloudbreak.cloud.model.CloudSubnet;
import com.sequenceiq.cloudbreak.common.json.JsonUtil;
import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;
import com.sequenceiq.cloudbreak.util.PasswordUtil;
import com.sequenceiq.datalake.controller.exception.BadRequestException;
import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.environment.api.v1.environment.model.response.DetailedEnvironmentResponse;
import com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentNetworkResponse;

@Service
public class StackRequestManifester {

    private static final Logger LOGGER = LoggerFactory.getLogger(StackRequestManifester.class);

    @Value("${sdx.cluster.definition}")
    private String clusterDefinition;

    public void configureStackForSdxCluster(SdxCluster sdxCluster, DetailedEnvironmentResponse environment) {
        StackV4Request generatedStackV4Request = setupStackRequestForCloudbreak(sdxCluster, environment);
        addStackV4RequestAsString(sdxCluster, generatedStackV4Request);
    }

    public void addStackV4RequestAsString(SdxCluster sdxCluster, StackV4Request internalRequest) {
        try {
            LOGGER.info("Forming request from Internal Request");
            sdxCluster.setStackRequestToCloudbreak(JsonUtil.writeValueAsString(internalRequest));
        } catch (JsonProcessingException e) {
            LOGGER.info("Can not parse stack request");
            throw new BadRequestException("Can not parse stack request", e);
        }
    }

    public StackV4Request setupStackRequestForCloudbreak(SdxCluster sdxCluster, DetailedEnvironmentResponse environment) {
        try {
            StackV4Request stackRequest = JsonUtil.readValue(sdxCluster.getStackRequest(), StackV4Request.class);
            stackRequest.setName(sdxCluster.getClusterName());
            TagsV4Request tags = new TagsV4Request();
            try {
                tags.setUserDefined(sdxCluster.getTags().get(HashMap.class));
            } catch (IOException e) {
                throw new BadRequestException("can not convert from json to tags");
            }
            stackRequest.setTags(tags);
            stackRequest.setEnvironmentCrn(sdxCluster.getEnvCrn());

            if (!CloudPlatform.YARN.name().equals(environment.getCloudPlatform())
                    && environment.getNetwork() != null
                    && environment.getNetwork().getSubnetMetas() != null
                    && !environment.getNetwork().getSubnetMetas().isEmpty()) {
                setupPlacement(environment, stackRequest);
                setupNetwork(environment, stackRequest);
            } else {
                setupYarnDetails(environment, stackRequest);
            }
            setupAuthentication(environment, stackRequest);
            setupClusterRequest(stackRequest);
            return stackRequest;
        } catch (IOException e) {
            LOGGER.error("Can not parse json to stack request");
            throw new IllegalStateException("Can not parse json to stack request", e);
        }
    }

    private void setupYarnDetails(DetailedEnvironmentResponse environment, StackV4Request stackRequest) {
        if (environment.getNetwork() == null
                || environment.getNetwork().getYarn() == null
                || environment.getNetwork().getYarn().getQueue() == null) {
            throw new BadRequestException("There is no queue defined in your environment, please create a new yarn environment with queue");
        } else {
            YarnStackV4Parameters yarnStackV4Parameters = new YarnStackV4Parameters();
            yarnStackV4Parameters.setYarnQueue(environment.getNetwork().getYarn().getQueue());
            stackRequest.setYarn(yarnStackV4Parameters);
        }
    }

    private void setupPlacement(DetailedEnvironmentResponse environment, StackV4Request stackRequest) {
        String subnetId = environment.getNetwork().getSubnetMetas().keySet().iterator().next();
        CloudSubnet cloudSubnet = environment.getNetwork().getSubnetMetas().get(subnetId);

        PlacementSettingsV4Request placementSettingsV4Request = new PlacementSettingsV4Request();
        placementSettingsV4Request.setAvailabilityZone(cloudSubnet.getAvailabilityZone());
        placementSettingsV4Request.setRegion(environment.getRegions().getNames().iterator().next());
        stackRequest.setPlacement(placementSettingsV4Request);
    }

    private void setupNetwork(DetailedEnvironmentResponse environmentResponse, StackV4Request stackRequest) {
        stackRequest.setNetwork(convertNetwork(environmentResponse.getNetwork()));
    }

    public NetworkV4Request convertNetwork(EnvironmentNetworkResponse network) {
        NetworkV4Request response = new NetworkV4Request();
        response.setAws(getIfNotNull(network.getAws(), aws -> convertToAwsNetwork(network)));
        response.setAzure(getIfNotNull(network.getAzure(), azure -> convertToAzureNetwork(network)));
        return response;
    }

    private AzureNetworkV4Parameters convertToAzureNetwork(EnvironmentNetworkResponse source) {
        AzureNetworkV4Parameters response = new AzureNetworkV4Parameters();
        response.setNetworkId(source.getAzure().getNetworkId());
        response.setNoFirewallRules(source.getAzure().getNoFirewallRules());
        response.setNoPublicIp(source.getAzure().getNoPublicIp());
        response.setResourceGroupName(source.getAzure().getResourceGroupName());
        response.setSubnetId(source.getSubnetIds().stream().findFirst().orElseThrow(()
                -> new com.sequenceiq.cloudbreak.exception.BadRequestException("No subnet id for this environment")));
        return response;
    }

    private AwsNetworkV4Parameters convertToAwsNetwork(EnvironmentNetworkResponse source) {
        AwsNetworkV4Parameters response = new AwsNetworkV4Parameters();
        response.setSubnetId(source.getSubnetIds().stream().findFirst().orElseThrow(()
                -> new com.sequenceiq.cloudbreak.exception.BadRequestException("No subnet id for this environment")));
        response.setVpcId(source.getAws().getVpcId());
        return response;
    }

    private void setupAuthentication(DetailedEnvironmentResponse environment, StackV4Request stackRequest) {
        if (stackRequest.getAuthentication() == null) {
            StackAuthenticationV4Request stackAuthenticationV4Request = new StackAuthenticationV4Request();
            stackAuthenticationV4Request.setPublicKey(environment.getAuthentication().getPublicKey());
            stackAuthenticationV4Request.setPublicKeyId(environment.getAuthentication().getPublicKeyId());
            stackRequest.setAuthentication(stackAuthenticationV4Request);
        }
    }

    private void setupClusterRequest(StackV4Request stackRequest) {
        ClusterV4Request cluster = stackRequest.getCluster();
        if (cluster != null && cluster.getBlueprintName() == null) {
            cluster.setBlueprintName(clusterDefinition);
        }
        if (cluster != null && cluster.getUserName() == null) {
            cluster.setUserName("admin");
        }
        if (cluster != null && cluster.getPassword() == null) {
            cluster.setPassword(PasswordUtil.generatePassword());
        }
    }
}
