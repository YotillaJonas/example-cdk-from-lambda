package com.myorg;


import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.CfnResource;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.customresources.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.LayerVersion;
import software.amazon.awscdk.services.rds.InstanceProps;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static software.amazon.awscdk.services.lambda.Runtime.JAVA_11;


public class ExampleCdkFromLambdaStack extends Stack {
    protected static final String VPC_ID = "VPC";

    public ExampleCdkFromLambdaStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public ExampleCdkFromLambdaStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

//        createRunCdkLambda();
        createAuroraV2();
    }

    private void createRunCdkLambda() {
        String tmpBinDir = getTmpBinDir();

        PolicyStatement cloudformationPolicy = new PolicyStatement(PolicyStatementProps.builder()
                .resources(Collections.singletonList(
                        "*"
                ))
                .actions(Arrays.asList(
                        "cloudformation:DescribeStacks",
                        "cloudformation:CreateChangeSet",
                        "cloudformation:DescribeChangeSet",
                        "cloudformation:GetTemplate",
                        "cloudformation:GetTemplateSummary",
                        "cloudformation:DescribeStackEvents",
                        "cloudformation:ExecuteChangeSet",
                        "cloudformation:DeleteChangeSet",
                        "cloudformation:DeleteStack"
                ))
                .build());

        // we can restrict this policy to certain buckets only
        PolicyStatement s3Policy = new PolicyStatement(PolicyStatementProps.builder()
                .resources(Collections.singletonList(
                        "*"
                ))
                .actions(Collections.singletonList(
                        "s3:*"
                ))
                .build());

        LayerVersion nodeLayer = LayerVersion.Builder.create(this, "node-layer")
                .description("Layer containing node binary")
                .code(
                        Code.fromAsset(tmpBinDir + "/node.zip")
                )
                .build();

        LayerVersion cdkLayer = LayerVersion.Builder.create(this, "aws-cdk-layer")
                .description("Layer containing AWS CDK")
                .code(
                        Code.fromAsset(tmpBinDir + "/aws-cdk.zip")
                )
                .build();

        new Function(this, "CreateTenant", FunctionProps.builder()
                .runtime(JAVA_11)
                .handler("com.myorg.CreateTenant")
                .code(Code.fromAsset(tmpBinDir + "/run-cdk-lambda.jar"))
                .layers(Arrays.asList(
                        nodeLayer,
                        cdkLayer
                ))
                .timeout(Duration.seconds(100))
                .memorySize(1024)
                .initialPolicy(Arrays.asList(
                        cloudformationPolicy,
                        s3Policy
                ))
                .functionName("create-tenant")
                .build());
    }

    private void createAuroraV2() {
        final AuroraPostgresClusterEngineProps auroraPostgresClusterEngineProps = AuroraPostgresClusterEngineProps.builder()
                .version(AuroraPostgresEngineVersion.VER_13_6)
                .build();

        final InstanceProps instanceProps = InstanceProps.builder()
                .instanceType(new InstanceType("serverless"))
                .autoMinorVersionUpgrade(false)
                .publiclyAccessible(false)
                .vpc(createVpc())
                .build();

        final DatabaseClusterProps databaseClusterProps = DatabaseClusterProps.builder()
                .engine(DatabaseClusterEngine.auroraPostgres(auroraPostgresClusterEngineProps))
                .instances(1)
                .instanceProps(instanceProps)
                .port(5432)
                .cloudwatchLogsExports(Collections.singletonList("postgresql"))
                .build();

        final DatabaseCluster auroraV2Cluster = new DatabaseCluster(this, "AuroraV2Cluster", databaseClusterProps);

        final int dbClusterInstanceCount = 1;

        final Map<String, Object> serverlessV2ScalingConfiguration = new HashMap<>();
        serverlessV2ScalingConfiguration.put("MinCapacity", 0.5);
        serverlessV2ScalingConfiguration.put("MaxCapacity", 16);

        final Map<String, Object> params = new HashMap<>();
        params.put("DBClusterIdentifier", auroraV2Cluster.getClusterIdentifier());
        params.put("ServerlessV2ScalingConfiguration", serverlessV2ScalingConfiguration);


        final AwsCustomResourceProps scalingResourceProps = AwsCustomResourceProps.builder()
                .onCreate(AwsSdkCall.builder()
                        .service("RDS")
                        .action("modifyDBCluster")
                        .parameters(params)
                        .physicalResourceId(PhysicalResourceId.of(auroraV2Cluster.getClusterIdentifier()))
                        .build())
                .onUpdate(AwsSdkCall.builder()
                        .service("RDS")
                        .action("modifyDBCluster")
                        .parameters(params)
                        .physicalResourceId(PhysicalResourceId.of(auroraV2Cluster.getClusterIdentifier()))
                        .build())
                .policy(AwsCustomResourcePolicy.fromSdkCalls(SdkCallsPolicyOptions.builder()
                        .resources(AwsCustomResourcePolicy.ANY_RESOURCE)
                        .build()))
                .build();

        final AwsCustomResource dbScalingConfigure = new AwsCustomResource(this, "AuroraScalingConfigure", scalingResourceProps);
        final CfnDBCluster cfnDbCluster = (CfnDBCluster) auroraV2Cluster.getNode().getDefaultChild();

        final CfnResource dbScalingConfigureTarget = (CfnResource) dbScalingConfigure.getNode().findChild("Resource").getNode().getDefaultChild();

        cfnDbCluster.addPropertyOverride("EngineMode", "provisioned");
        dbScalingConfigureTarget.getNode().addDependency(cfnDbCluster);

        for (int i = 1; i <= dbClusterInstanceCount; i++) {
            ((CfnDBInstance) (auroraV2Cluster.getNode().findChild("Instance" + i))).addDependsOn(dbScalingConfigureTarget);
        }
    }


    private Vpc createVpc() {
        final List<SubnetConfiguration> subnetConfigurationList = createSubnetConfigurationList();

        final String vpcName = String.format("yotilla-vpc-tenant-%d", 4711);

        final VpcProps vpcProps = VpcProps.builder()
                .maxAzs(3)
                .natGateways(1)
                .subnetConfiguration(subnetConfigurationList)
                .vpcName(vpcName)
                .build();

        return new Vpc(this, VPC_ID, vpcProps);
    }

    @NotNull
    private List<SubnetConfiguration> createSubnetConfigurationList() {
        final SubnetConfiguration subnetPrivateNAT = SubnetConfiguration.builder()
                .subnetType(SubnetType.PRIVATE_WITH_NAT)
                .name("PrivateNat")
                .cidrMask(18)
                .build();

        final SubnetConfiguration subnetPrivateIsolated = SubnetConfiguration.builder()
                .subnetType(SubnetType.PRIVATE_ISOLATED)
                .name("PrivateIsolated")
                .cidrMask(24)
                .build();

        final SubnetConfiguration subnetPublic = SubnetConfiguration.builder()
                .subnetType(SubnetType.PUBLIC)
                .name("Public")
                .cidrMask(24)
                .build();

        List<SubnetConfiguration> subnetConfigurations = new java.util.ArrayList<>();
        subnetConfigurations.add(subnetPrivateNAT);
        subnetConfigurations.add(subnetPrivateIsolated);
        subnetConfigurations.add(subnetPublic);

        return subnetConfigurations;
    }

    private String getTmpBinDir() {
        Path tmpPath = Paths.get("tmp");
        return tmpPath.toAbsolutePath().toString();
    }
}
