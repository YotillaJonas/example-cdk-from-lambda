package com.myorg;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CdkWrapper implements RequestHandler<Map<String, String>, String> {

    public static final String CDK_OUT_DIR = "/tmp/cdk.out";
    private static final String CDK_COMMAND = "java -cp . ";

    private static final String DEPLOY_OPERATION = "deploy";
    private static final String DESTROY_OPERATION = "destroy";

    protected static final String DEFAULT_TENANT_ID = "0";
    protected static final String TENANT_ID_PARAMETER_KEY = "tenantId";

    @Override
    public String handleRequest(Map<String, String> input, Context context) {
        final String operation = input.get("operation");
        final String tenantId = Optional.ofNullable(input.get(TENANT_ID_PARAMETER_KEY)).orElse(DEFAULT_TENANT_ID);

        if (DEPLOY_OPERATION.equalsIgnoreCase(operation)) {
            deployApp(RunCdkLambdaApp.class, tenantId);
        } else if (DESTROY_OPERATION.equalsIgnoreCase(operation)) {
            destroyApp(RunCdkLambdaApp.class, tenantId);
        }

        return "";
    }

    public static void deployApp(Class<?> cdkClass, final String tenantId) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "cdk",
                DEPLOY_OPERATION,
                "--verbose",
                "--output", CDK_OUT_DIR,
                "--app", CDK_COMMAND + cdkClass.getName(),
                "--context", TENANT_ID_PARAMETER_KEY + "=" + tenantId,
                "--require-approval", "\"never\""
        );
        executeProcess(processBuilder);
    }

    public static void destroyApp(Class<?> cdkClass, final String tenantId) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "cdk",
                DESTROY_OPERATION,
                "--verbose",
                "--output", CDK_OUT_DIR,
                "--app", CDK_COMMAND + cdkClass.getName(),
                "--context", TENANT_ID_PARAMETER_KEY + "=" + tenantId,
                "--force"
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
