package com.sequenceiq.cloudbreak.template.views;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.sequenceiq.cloudbreak.template.ClusterDefinitionProcessingException;
import com.sequenceiq.cloudbreak.template.processor.ClusterDefinitionTextProcessor;

public class ClusterDefinitionView {

    private String clusterDefinitionText;

    private String version;

    private String type;

    private Set<String> components;

    private ClusterDefinitionTextProcessor processor;

    public ClusterDefinitionView(String clusterDefinitionText, String version, String type, ClusterDefinitionTextProcessor processor) {
        this.clusterDefinitionText = clusterDefinitionText;
        this.type = type;
        this.version = version;
        this.processor = processor;
        components = prepareComponents(clusterDefinitionText);
    }

    private Set<String> prepareComponents(String clusterDefinitionText) {
        Set<String> result = new HashSet<>();
        try {
            Map<String, Set<String>> componentsByHostGroup = processor.getComponentsByHostGroup();
            componentsByHostGroup.values().forEach(result::addAll);
        } catch (ClusterDefinitionProcessingException exception) {
            result = new HashSet<>();
        }
        return result;
    }

    public String getVersion() {
        return version;
    }

    public void setClusterDefinitionText(String clusterDefinitionText) {
        this.clusterDefinitionText = clusterDefinitionText;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public boolean isHdf() {
        return "HDF".equalsIgnoreCase(type);
    }

    public String getClusterDefinitionText() {
        return clusterDefinitionText;
    }

    public Set<String> getComponents() {
        return components;
    }

    public void setComponents(Set<String> components) {
        this.components = components;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClusterDefinitionView)) {
            return false;
        }
        ClusterDefinitionView that = (ClusterDefinitionView) o;
        return Objects.equals(clusterDefinitionText, that.clusterDefinitionText)
                && Objects.equals(version, that.version)
                && Objects.equals(type, that.type)
                && Objects.equals(components, that.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterDefinitionText, version, type, components);
    }

}
