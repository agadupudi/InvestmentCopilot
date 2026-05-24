import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

interface AppStackProps extends cdk.StackProps {
  vpc: ec2.IVpc;
  dbInstance: rds.DatabaseInstance;
  dbSecret: secretsmanager.ISecret;
  redisEndpoint: string;
  redisPort: number;
  redisSecurityGroup: ec2.ISecurityGroup;
}

export class AppStack extends cdk.Stack {
  public readonly ecrRepository: ecr.Repository;
  public readonly loadBalancer: elbv2.ApplicationLoadBalancer;

  constructor(scope: Construct, id: string, props: AppStackProps) {
    super(scope, id, props);

    // ---------- ECR ----------
    this.ecrRepository = new ecr.Repository(this, 'BackendRepo', {
      repositoryName: 'investment-copilot-backend',
      imageScanOnPush: true,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      emptyOnDelete: true,
      lifecycleRules: [{ maxImageCount: 10 }],
    });

    // ---------- ECS cluster ----------
    const cluster = new ecs.Cluster(this, 'Cluster', {
      vpc: props.vpc,
      containerInsights: true,
    });

    const logGroup = new logs.LogGroup(this, 'BackendLogs', {
      retention: logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // ---------- Service SG ----------
    const serviceSg = new ec2.SecurityGroup(this, 'ServiceSg', {
      vpc: props.vpc,
      description: 'Fargate service SG',
      allowAllOutbound: true,
    });

    // Allow Fargate → RDS Postgres
    props.dbInstance.connections.allowDefaultPortFrom(serviceSg, 'App service → Postgres');

    // Allow Fargate → ElastiCache Redis
    props.redisSecurityGroup.addIngressRule(
      ec2.Peer.securityGroupId(serviceSg.securityGroupId),
      ec2.Port.tcp(6379),
      'App service → Redis'
    );

    // ---------- Task definition ----------
    const taskDef = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      cpu: 256,
      memoryLimitMiB: 512,
    });

    props.dbSecret.grantRead(taskDef.taskRole);

    taskDef.addContainer('backend', {
      image: ecs.ContainerImage.fromEcrRepository(this.ecrRepository, 'latest'),
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: 'backend',
        logGroup,
      }),
      portMappings: [{ containerPort: 8000 }],
      environment: {
        PORT: '8000',
        REDIS_URL: `redis://${props.redisEndpoint}:${props.redisPort}/0`,
        SPRING_DATASOURCE_URL: `jdbc:postgresql://${props.dbInstance.dbInstanceEndpointAddress}:${props.dbInstance.dbInstanceEndpointPort}/copilot`,
      },
      secrets: {
        SPRING_DATASOURCE_USERNAME: ecs.Secret.fromSecretsManager(props.dbSecret, 'username'),
        SPRING_DATASOURCE_PASSWORD: ecs.Secret.fromSecretsManager(props.dbSecret, 'password'),
      },
      healthCheck: {
        command: ['CMD-SHELL', 'wget -qO- http://localhost:8000/health || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
        startPeriod: cdk.Duration.seconds(60),
      },
    });

    // ---------- ALB + listener ----------
    this.loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      vpc: props.vpc,
      internetFacing: true,
    });

    const listener = this.loadBalancer.addListener('HttpListener', { port: 80, open: true });

    // ---------- Fargate service ----------
    const service = new ecs.FargateService(this, 'Service', {
      cluster,
      taskDefinition: taskDef,
      desiredCount: 1,
      assignPublicIp: false,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [serviceSg],
      circuitBreaker: { rollback: true },
      healthCheckGracePeriod: cdk.Duration.seconds(90),
    });

    listener.addTargets('Backend', {
      port: 8000,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targets: [service],
      healthCheck: {
        path: '/health',
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
      },
      deregistrationDelay: cdk.Duration.seconds(15),
    });

    // ---------- Outputs ----------
    new cdk.CfnOutput(this, 'AlbDnsName', { value: this.loadBalancer.loadBalancerDnsName });
    new cdk.CfnOutput(this, 'EcrRepositoryUri', { value: this.ecrRepository.repositoryUri });
    new cdk.CfnOutput(this, 'ClusterName', { value: cluster.clusterName });
    new cdk.CfnOutput(this, 'ServiceName', { value: service.serviceName });
  }
}
