package com.sequenceiq.cloudbreak.cloud.aws;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNoneEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.SdkBaseException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeRegionsRequest;
import com.sequenceiq.cloudbreak.cloud.CredentialConnector;
import com.sequenceiq.cloudbreak.cloud.aws.view.AwsCredentialView;
import com.sequenceiq.cloudbreak.cloud.aws.view.AwsCredentialViewProvider;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredentialStatus;
import com.sequenceiq.cloudbreak.cloud.model.CredentialStatus;
import com.sequenceiq.cloudbreak.cloud.response.AwsCredentialPrerequisites;
import com.sequenceiq.cloudbreak.cloud.response.CredentialPrerequisitesResponse;

@Service
public class AwsCredentialConnector implements CredentialConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsCredentialConnector.class);

    @Value("${cb.aws.account.id:}")
    private String accountId;

    @Inject
    private AwsSessionCredentialClient credentialClient;

    @Inject
    private AwsClient awsClient;

    @Inject
    private AwsCredentialVerifier awsCredentialVerifier;

    @Inject
    private AwsPlatformParameters awsPlatformParameters;

    @Inject
    private AwsCredentialViewProvider credentialViewProvider;

    @Override
    public CloudCredentialStatus verify(AuthenticatedContext authenticatedContext) {
        CloudCredential credential = authenticatedContext.getCloudCredential();
        LOGGER.debug("Create credential: {}", credential);
        AwsCredentialView awsCredential = credentialViewProvider.createAwsCredentialView(credential);
        String roleArn = awsCredential.getRoleArn();
        String accessKey = awsCredential.getAccessKey();
        String secretKey = awsCredential.getSecretKey();
        if (isNoneEmpty(roleArn, accessKey, secretKey)) {
            String message = "Please only provide the 'role arn' or the 'access' and 'secret key'";
            return new CloudCredentialStatus(credential, CredentialStatus.FAILED, new Exception(message), message);
        }
        if (isNotEmpty(roleArn)) {
            return verifyIamRoleIsAssumable(credential);
        }
        if (isEmpty(accessKey) || isEmpty(secretKey)) {
            String message = "Please provide both the 'access' and 'secret key'";
            return new CloudCredentialStatus(credential, CredentialStatus.FAILED, new Exception(message), message);
        } else {
            return verifyAccessKeySecretKeyIsAssumable(credential);
        }
    }

    @Override
    public CloudCredentialStatus create(AuthenticatedContext auth) {
        return new CloudCredentialStatus(auth.getCloudCredential(), CredentialStatus.CREATED);
    }

    @Override
    public CloudCredentialStatus delete(AuthenticatedContext auth) {
        return new CloudCredentialStatus(auth.getCloudCredential(), CredentialStatus.DELETED);
    }

    @Override
    public CredentialPrerequisitesResponse getPrerequisites(CloudContext cloudContext, String externalId, String deploymentAddress) {
        AwsCredentialPrerequisites awsPrerequisites = new AwsCredentialPrerequisites(externalId, awsPlatformParameters.getCredentialPoliciesJson());
        return new CredentialPrerequisitesResponse(cloudContext.getPlatform().value(), accountId, awsPrerequisites);
    }

    private CloudCredentialStatus verifyIamRoleIsAssumable(CloudCredential cloudCredential) {
        AwsCredentialView awsCredential = credentialViewProvider.createAwsCredentialView(cloudCredential);
        try {
            credentialClient.retrieveSessionCredentials(awsCredential);
        } catch (AmazonClientException ae) {
            if (ae.getMessage().contains("Unable to load AWS credentials")) {
                String errorMessage = String.format("Unable to load AWS credentials: please make sure that you configured your assumer %s and %s to deployer.",
                        awsCredential.isGovernmentCloudEnabled() ? "AWS_GOV_ACCESS_KEY_ID" : "AWS_ACCESS_KEY_ID",
                        awsCredential.isGovernmentCloudEnabled() ? "AWS_GOV_SECRET_ACCESS_KEY" : "AWS_SECRET_ACCESS_KEY");
                LOGGER.info(errorMessage, ae);
                return new CloudCredentialStatus(cloudCredential, CredentialStatus.FAILED, ae, errorMessage);
            }
        } catch (RuntimeException e) {
            String errorMessage = String.format("Could not assume role '%s': check if the role exists and if it's created with the correct external ID",
                    awsCredential.getRoleArn());
            LOGGER.warn(errorMessage, e);
            return new CloudCredentialStatus(cloudCredential, CredentialStatus.FAILED, e, errorMessage);
        }
        try {
            awsCredentialVerifier.validateAws(awsCredential);
        } catch (AwsPermissionMissingException e) {
            return new CloudCredentialStatus(cloudCredential, CredentialStatus.PERMISSIONS_MISSING, new Exception(e.getMessage()), e.getMessage());
        } catch (SdkBaseException e) {
            LOGGER.warn("AWS credential validation failed due to {}", e.getMessage(), e);
            return new CloudCredentialStatus(cloudCredential, CredentialStatus.FAILED, e, e.getMessage());
        }
        return new CloudCredentialStatus(cloudCredential, CredentialStatus.VERIFIED);
    }

    private CloudCredentialStatus verifyAccessKeySecretKeyIsAssumable(CloudCredential cloudCredential) {
        AwsCredentialView awsCredential = new AwsCredentialView(cloudCredential);
        try {
            AmazonEC2Client access = awsClient.createAccess(cloudCredential);
            DescribeRegionsRequest describeRegionsRequest = new DescribeRegionsRequest();
            access.describeRegions(describeRegionsRequest);
        } catch (AmazonClientException ae) {
            String errorMessage = "Unable to verify AWS credentials: "
                    + "please make sure the access key and secret key is correct. "
                    + ae.getMessage();
            LOGGER.debug(errorMessage, ae);
            return new CloudCredentialStatus(cloudCredential, CredentialStatus.FAILED, ae, errorMessage);
        } catch (RuntimeException e) {
            String errorMessage = String.format("Could not verify keys '%s': check if the keys exists and if it's created with the correct external ID. %s",
                    awsCredential.getAccessKey(), e.getMessage());
            LOGGER.warn(errorMessage, e);
            return new CloudCredentialStatus(cloudCredential, CredentialStatus.FAILED, e, errorMessage);
        }
        try {
            awsCredentialVerifier.validateAws(awsCredential);
        } catch (AwsPermissionMissingException e) {
            return new CloudCredentialStatus(cloudCredential, CredentialStatus.PERMISSIONS_MISSING, new Exception(e.getMessage()), e.getMessage());
        }
        return new CloudCredentialStatus(cloudCredential, CredentialStatus.VERIFIED);
    }
}
