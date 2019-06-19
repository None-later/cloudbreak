package com.sequenceiq.redbeams.api.endpoint.v4.databaseserver.requests;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sequenceiq.redbeams.api.endpoint.v4.stacks.AwsDBStackV4Parameters;
import com.sequenceiq.redbeams.api.endpoint.v4.stacks.DatabaseServerV4Request;
import com.sequenceiq.redbeams.api.endpoint.v4.stacks.NetworkV4Request;
import com.sequenceiq.cloudbreak.common.mappable.Mappable;
import com.sequenceiq.cloudbreak.common.mappable.ProviderParametersBase;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@ApiModel
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllocateDatabaseServerV4Request extends ProviderParametersBase {

    // FIXME Define ModelDescriptions.DBStackModelDescription

    @Size(max = 40, min = 5, message = "The length of the name has to be in range of 5 to 40")
    @Pattern(regexp = "(^[a-z][-a-z0-9]*[a-z0-9]$)",
            message = "The name can only contain lowercase alphanumeric characters and hyphens and must start with an alphanumeric character")
    @NotNull
    @ApiModelProperty(value = /* DBStackModelDescription.STACK_NAME */ "name of the stack", required = true)
    private String name;

    @NotNull
    @ApiModelProperty(value = /* StackModelDescription.ENVIRONMENT_CRN */ "ID of the environment", required = true)
    private String environmentId;

    @Valid
    @ApiModelProperty(/* DBStackModelDescriptions.NETWORK) */ "Network for database stack")
    private NetworkV4Request network;

    @Valid
    @ApiModelProperty(/* DBStackModelDescriptions.DATABASE_SERVER) */ "Database server for database stack")
    private DatabaseServerV4Request databaseServer;

    @ApiModelProperty(/* DBStackModelDescription.AWS_PARAMETERS */ "AWS-specific parameters for database stack")
    private AwsDBStackV4Parameters aws;

    public String getName() {
        return name;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public NetworkV4Request getNetwork() {
        return network;
    }

    public DatabaseServerV4Request getDatabaseServer() {
        return databaseServer;
    }

    public Mappable createAws() {
        if (aws == null) {
            aws = new AwsDBStackV4Parameters();
        }
        return aws;
    }

    public void setAws(AwsDBStackV4Parameters aws) {
        this.aws = aws;
    }

    public Mappable createGcp() {
        return null;
    }

    public Mappable createAzure() {
        return null;
    }

    public Mappable createOpenstack() {
        return null;
    }

    public Mappable createYarn() {
        return null;
    }

    public Mappable createMock() {
        return null;
    }

}
