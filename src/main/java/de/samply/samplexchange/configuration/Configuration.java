package de.samply.samplexchange.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class Configuration {
    @Value("${app.version}")
    private String appVersion;

    @Value("${sourceformat:}")
    private String sourceFormat;

    @Value("${targetformat:}")
    private String targetFormat;

    @Value("${fileexportpath}")
    private String fileExportPath;

    private FhirServerProperties source = new FhirServerProperties();
    private FhirServerProperties target = new FhirServerProperties();

    public String getSourceServer() {
        return source.getUrl();
    }

    public String getTargetServer() {
        return target.getUrl();
    }
}
