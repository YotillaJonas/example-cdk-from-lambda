package com.myorg;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.Collections;
import java.util.Map;

import static com.myorg.CreateTenant.*;

public class RunCdkLambdaApp {
    public static void main(final String[] args) {
        App app = new App();

        final int tenantId = getTenantId(app);
        final String versionLabel = getVersionLabel(app);
        final String stackId = String.format("YotillaStackTenant%d", tenantId);

        final StackProps stackProps = getStackProps(app, tenantId, stackId);

        new RunCdkLambdaStack(app, stackId, stackProps, tenantId, versionLabel);

        app.synth();
    }

    @NotNull
    private static StackProps getStackProps(final App app, final int tenantId, final String stackId) {
        final Map<String, String> tags = Collections.singletonMap("yotilla:stack:tenant:id", String.valueOf(tenantId));
        final String region = getRegion(app);

        return StackProps.builder()
                .stackName(stackId)
                .tags(tags)
                .env(Environment.builder()
                        .region(region)
                        .build())
                .build();
    }

    private static int getTenantId(final App app) {
        final String parameterValue = ContextParameter.of(app).get(TENANT_ID_PARAMETER_KEY)
                .orElseThrow(() -> new RuntimeException("No tenant ID provided in context."));
        return Integer.parseInt(parameterValue);
    }

    private static String getRegion(final App app) {
        return ContextParameter.of(app).get(REGION_PARAMETER_KEY)
                .orElseThrow(() -> new RuntimeException("No region provided in context."));
    }

    private static String getVersionLabel(final App app) {
        return ContextParameter.of(app).get(VERSION_LABEL_PARAMETER_KEY)
                .orElseThrow(() -> new RuntimeException("No version label provided in context."));
    }
}
