package com.sequenceiq.environment.network.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.MapUtils;

import com.sequenceiq.cloudbreak.cloud.model.CloudSubnet;
import com.sequenceiq.environment.api.v1.environment.model.base.PrivateSubnetCreation;
import com.sequenceiq.environment.network.dao.domain.RegistrationType;

public class NetworkDto {

    private Long id;

    private String name;

    private final String networkId;

    private final String resourceCrn;

    private final AwsParams aws;

    private final AzureParams azure;

    private final YarnParams yarn;

    private final MockParams mock;

    private final CumulusYarnParams cumulus;

    private final GcpParams gcp;

    private final OpenstackParams openstack;

    private final String networkCidr;

    private final Map<String, CloudSubnet> subnetMetas;

    private final PrivateSubnetCreation privateSubnetCreation;

    private final RegistrationType registrationType;

    public NetworkDto(Builder builder) {
        this.id = builder.id;
        this.resourceCrn = builder.resourceCrn;
        this.name = builder.name;
        this.aws = builder.aws;
        this.azure = builder.azure;
        this.yarn = builder.yarn;
        this.mock = builder.mock;
        this.cumulus = builder.cumulus;
        this.gcp = builder.gcp;
        this.openstack = builder.openstack;
        this.subnetMetas = MapUtils.isEmpty(builder.subnetMetas) ? new HashMap<>() : builder.subnetMetas;
        this.networkCidr = builder.networkCidr;
        this.networkId = builder.networkId;
        this.privateSubnetCreation = builder.privateSubnetCreation;
        this.registrationType = builder.registrationType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNetworkName() {
        return name;
    }

    public String getResourceCrn() {
        return resourceCrn;
    }

    public AwsParams getAws() {
        return aws;
    }

    public AzureParams getAzure() {
        return azure;
    }

    public YarnParams getYarn() {
        return yarn;
    }

    public MockParams getMock() {
        return mock;
    }

    public CumulusYarnParams getCumulus() {
        return cumulus;
    }

    public GcpParams getGcp() {
        return gcp;
    }

    public OpenstackParams getOpenstack() {
        return openstack;
    }

    public Set<String> getSubnetIds() {
        return subnetMetas.keySet();
    }

    public String getNetworkCidr() {
        return networkCidr;
    }

    public Map<String, CloudSubnet> getSubnetMetas() {
        return subnetMetas;
    }

    public String getName() {
        return name;
    }

    public String getNetworkId() {
        return networkId;
    }

    public PrivateSubnetCreation getPrivateSubnetCreation() {
        return privateSubnetCreation;
    }

    public RegistrationType getRegistrationType() {
        return registrationType;
    }

    public static final class Builder {
        private Long id;

        private String name;

        private String networkId;

        private String resourceCrn;

        private AwsParams aws;

        private AzureParams azure;

        private YarnParams yarn;

        private MockParams mock;

        private CumulusYarnParams cumulus;

        private GcpParams gcp;

        private OpenstackParams openstack;

        private Map<String, CloudSubnet> subnetMetas;

        private String networkCidr;

        private PrivateSubnetCreation privateSubnetCreation;

        private RegistrationType registrationType;

        private Builder() {
        }

        public static Builder aNetworkDto() {
            return new Builder();
        }

        public Builder withId(Long id) {
            this.id = id;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withAws(AwsParams aws) {
            this.aws = aws;
            return this;
        }

        public Builder withAzure(AzureParams azure) {
            this.azure = azure;
            return this;
        }

        public Builder withOpenstack(OpenstackParams openstack) {
            this.openstack = openstack;
            return this;
        }

        public Builder withCumulus(CumulusYarnParams cumulus) {
            this.cumulus = cumulus;
            return this;
        }

        public Builder withGcp(GcpParams gcp) {
            this.gcp = gcp;
            return this;
        }

        public Builder withYarn(YarnParams yarn) {
            this.yarn = yarn;
            return this;
        }

        public Builder withMock(MockParams mock) {
            this.mock = mock;
            return this;
        }

        public Builder withSubnetMetas(Map<String, CloudSubnet> subnetMetas) {
            this.subnetMetas = subnetMetas;
            return this;
        }

        public Builder withResourceCrn(String resourceCrn) {
            this.resourceCrn = resourceCrn;
            return this;
        }

        public Builder withNetworkCidr(String networkCidr) {
            this.networkCidr = networkCidr;
            return this;
        }

        public Builder withNetworkId(String networkId) {
            this.networkId = networkId;
            return this;
        }

        public Builder withPrivateSubnetCreation(PrivateSubnetCreation privateSubnetCreation) {
            this.privateSubnetCreation = privateSubnetCreation;
            return this;
        }

        public Builder withRegistrationType(RegistrationType registrationType) {
            this.registrationType = registrationType;
            return this;
        }

        public NetworkDto build() {
            return new NetworkDto(this);
        }
    }
}
