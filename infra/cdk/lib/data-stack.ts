import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

interface DataStackProps extends cdk.StackProps {
  vpc: ec2.IVpc;
}

export class DataStack extends cdk.Stack {
  public readonly dbInstance: rds.DatabaseInstance;
  public readonly dbSecret: secretsmanager.ISecret;
  public readonly redisEndpoint: string;
  public readonly redisPort: number;
  public readonly redisSecurityGroup: ec2.SecurityGroup;
  public readonly dbSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: DataStackProps) {
    super(scope, id, props);

    // ---------- RDS Postgres ----------
    this.dbSecurityGroup = new ec2.SecurityGroup(this, 'DbSg', {
      vpc: props.vpc,
      description: 'RDS Postgres SG — ingress only from app SG',
      allowAllOutbound: false,
    });

    this.dbInstance = new rds.DatabaseInstance(this, 'Postgres', {
      vpc: props.vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_16_3,
      }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.MICRO),
      allocatedStorage: 20,
      maxAllocatedStorage: 100,
      databaseName: 'copilot',
      credentials: rds.Credentials.fromGeneratedSecret('copilot', {
        secretName: 'investment-copilot/rds',
      }),
      securityGroups: [this.dbSecurityGroup],
      multiAz: false,
      backupRetention: cdk.Duration.days(7),
      deletionProtection: false,
      removalPolicy: cdk.RemovalPolicy.SNAPSHOT,
      storageEncrypted: true,
    });

    this.dbSecret = this.dbInstance.secret!;

    // ---------- ElastiCache Redis ----------
    this.redisSecurityGroup = new ec2.SecurityGroup(this, 'RedisSg', {
      vpc: props.vpc,
      description: 'ElastiCache Redis SG — ingress only from app SG',
      allowAllOutbound: false,
    });

    const redisSubnetGroup = new elasticache.CfnSubnetGroup(this, 'RedisSubnetGroup', {
      description: 'Subnet group for Redis',
      subnetIds: props.vpc.selectSubnets({
        subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
      }).subnetIds,
    });

    const redis = new elasticache.CfnCacheCluster(this, 'Redis', {
      engine: 'redis',
      cacheNodeType: 'cache.t4g.micro',
      numCacheNodes: 1,
      vpcSecurityGroupIds: [this.redisSecurityGroup.securityGroupId],
      cacheSubnetGroupName: redisSubnetGroup.ref,
      engineVersion: '7.1',
    });
    redis.addDependency(redisSubnetGroup);

    this.redisEndpoint = redis.attrRedisEndpointAddress;
    this.redisPort = cdk.Token.asNumber(redis.attrRedisEndpointPort);

    new cdk.CfnOutput(this, 'DbEndpoint', { value: this.dbInstance.dbInstanceEndpointAddress });
    new cdk.CfnOutput(this, 'DbSecretArn', { value: this.dbSecret.secretArn });
    new cdk.CfnOutput(this, 'RedisEndpoint', { value: this.redisEndpoint });
  }
}
