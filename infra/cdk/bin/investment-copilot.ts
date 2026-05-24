#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { NetworkStack } from '../lib/network-stack';
import { DataStack } from '../lib/data-stack';
import { AppStack } from '../lib/app-stack';

const app = new cdk.App();

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION ?? 'us-east-1',
};

const prefix = 'InvestmentCopilot';

const network = new NetworkStack(app, `${prefix}-Network`, { env });

const data = new DataStack(app, `${prefix}-Data`, {
  env,
  vpc: network.vpc,
});

new AppStack(app, `${prefix}-App`, {
  env,
  vpc: network.vpc,
  dbInstance: data.dbInstance,
  dbSecret: data.dbSecret,
  redisEndpoint: data.redisEndpoint,
  redisPort: data.redisPort,
  redisSecurityGroup: data.redisSecurityGroup,
});

app.synth();
