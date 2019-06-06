package com.sequenceiq.environment.client;

public class EnvironmentApiClientParams {
    private boolean restDebug;

    private boolean certificateValidation;

    private boolean ignorePreValidation;

    private String environmentServerUrl;

    public EnvironmentApiClientParams(boolean restDebug, boolean certificateValidation, boolean ignorePreValidation, String environmentServerUrl) {
        this.restDebug = restDebug;
        this.certificateValidation = certificateValidation;
        this.ignorePreValidation = ignorePreValidation;
        this.environmentServerUrl = environmentServerUrl;
    }

    public String getServiceUrl() {
        return environmentServerUrl;
    }

    public boolean isCertificateValidation() {
        return certificateValidation;
    }

    public boolean isIgnorePreValidation() {
        return ignorePreValidation;
    }

    public boolean isRestDebug() {
        return restDebug;
    }
}