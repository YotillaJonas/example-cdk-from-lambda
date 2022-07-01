package com.myorg;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class CreateTenant implements RequestHandler<Map<String, String>, String> {

    public static final String CDK_OUT_DIR = "/tmp/cdk.out";
    private static final String CDK_COMMAND = "java -cp . ";

    protected static final String TENANT_ID_PARAMETER_KEY = "tenantId";
    protected static final String REGION_PARAMETER_KEY = "region";
    protected static final String VERSION_LABEL_PARAMETER_KEY = "versionLabel";
    private static final Random RANDOM = new Random();

    @Override
    public String handleRequest(Map<String, String> input, Context context) {
        final String region = input.get(REGION_PARAMETER_KEY);
        final String versionLabel = input.get(VERSION_LABEL_PARAMETER_KEY);
        final int tenantId = RANDOM.nextInt();

        createNewTenant(RunCdkLambdaApp.class, region, versionLabel, tenantId);

        return String.valueOf(tenantId);
    }

    public static void createNewTenant(Class<?> cdkClass, final String region, final String versionLabel, final int tenantId) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "cdk",
                "deploy",
                "--verbose",
                "--output", CDK_OUT_DIR,
                "--app", CDK_COMMAND + cdkClass.getName(),
                "--context", TENANT_ID_PARAMETER_KEY + "=" + tenantId,
                "--context", REGION_PARAMETER_KEY + "=" + region,
                "--context", VERSION_LABEL_PARAMETER_KEY + "=" + versionLabel,
                "--require-approval", "\"never\""
        );
        executeProcess(processBuilder);
    }

    private static void executeProcess(final ProcessBuilder processBuilder) {
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException("Cannot start cdk process!", e);
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Cdk process interrupted!", e);
        }

        int exitValue = process.exitValue();

        if (exitValue != 0) {
            final String errorMessages = new BufferedReader(new InputStreamReader(process.getErrorStream())).lines()
                    .collect(Collectors.joining("\n"));
            throw new RuntimeException("Exception while executing CDK!\n" + errorMessages);
        }
    }
}
