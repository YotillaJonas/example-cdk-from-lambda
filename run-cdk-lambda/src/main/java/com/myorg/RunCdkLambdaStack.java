package com.myorg;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

import java.util.Collections;

public class RunCdkLambdaStack extends Stack {

    public RunCdkLambdaStack(final Construct scope, final String id, final StackProps props, final int tenantId, final String versionLabel) {
        super(scope, id, props);

//        final Bucket bucket = new Bucket(this, "created-by-cdk-from-lambda", BucketProps.builder()
//                .removalPolicy(RemovalPolicy.DESTROY)
//                .build());
//
//        Tags.of(bucket).add(CdkWrapper.TENANT_ID_PARAMETER_KEY, String.valueOf(tenantId));


        final AuroraPostgresClusterEngineProps auroraPostgresClusterEngineProps = AuroraPostgresClusterEngineProps.builder()
                .version(AuroraPostgresEngineVersion.VER_13_6)
                .build();

        final InstanceProps instanceProps = InstanceProps.builder()
                .instanceType(new CustomInstanceType(CustomInstanceType.ServerlessInstanceType.SERVERLESS.name()))
                .autoMinorVersionUpgrade(false)
                .publiclyAccessible(false)
                .build();

        final DatabaseClusterProps databaseClusterProps = DatabaseClusterProps.builder()
                .engine(DatabaseClusterEngine.auroraPostgres(auroraPostgresClusterEngineProps))
                .instances(1)
                .instanceProps(instanceProps)
                .port(5432)
                .cloudwatchLogsExports(Collections.singletonList("auroraV2Postgres"))
                .build();

        new DatabaseCluster(this, "AuroraV2Cluster", databaseClusterProps);
    }


    public static class CustomInstanceType extends InstanceType {
        public enum ServerlessInstanceType {
            SERVERLESS("serverless");

            ServerlessInstanceType(final String name) {
                ServerlessInstanceType.valueOf(name);
            }
        }

        public CustomInstanceType(@NotNull final String instanceTypeIdentifier) {
            super(instanceTypeIdentifier);
        }
    }
}
