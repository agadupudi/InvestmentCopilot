# Investment Co-Pilot — AWS CDK

TypeScript CDK app that provisions the AWS infrastructure for the backend.
The frontend deploys via AWS Amplify Hosting separately (see `../../docs/DEPLOYMENT.md`).

## Stacks

| Stack | Purpose |
|---|---|
| `InvestmentCopilot-Network` | VPC across 2 AZs: public + private-egress + private-isolated subnets, 1 NAT gateway |
| `InvestmentCopilot-Data` | RDS Postgres 16 (`db.t4g.micro`, isolated subnet, password in Secrets Manager) + ElastiCache Redis (`cache.t4g.micro`, isolated subnet) |
| `InvestmentCopilot-App` | ECR repo + ECS Fargate service (0.25 vCPU / 0.5 GB) behind an ALB on port 80, target group health-checking `/health` |

## Prerequisites

- AWS account + AWS CLI configured (`aws configure`)
- Node 20+
- Docker (for building the backend image)

## One-time bootstrap

CDK needs a few helper resources in your account/region. Run once per account:

```bash
cd infra/cdk
npm install
npx cdk bootstrap aws://<ACCOUNT_ID>/us-east-1
```

## Deploy

```bash
npx cdk synth        # validate (no changes to AWS)
npx cdk deploy --all
```

After deploy, copy the outputs printed at the end:
- `EcrRepositoryUri` — push your backend image here
- `AlbDnsName` — public DNS for the API; use as `NEXT_PUBLIC_API_BASE_URL` in Amplify
- `DbEndpoint`, `DbSecretArn`, `RedisEndpoint` — Spring Boot reads these via env vars (already wired in the task definition)

See `../../docs/DEPLOYMENT.md` for full step-by-step including image push + Amplify setup.

## Tear down

```bash
npx cdk destroy --all
```

> Note: RDS has `RemovalPolicy.SNAPSHOT`, so a final snapshot is taken before deletion. Delete the snapshot manually in the RDS console if you don't want to keep it.

## Costs (us-east-1, minimum scale)

| Resource | Approx /mo |
|---|---|
| Fargate (0.25 vCPU, 0.5 GB, 24/7) | ~$10 |
| RDS db.t4g.micro | ~$13 |
| ElastiCache cache.t4g.micro | ~$12 |
| ALB | ~$16 |
| NAT Gateway | ~$32 |
| **Total** | **~$80 / mo** |

Cost-saving tweak: drop the NAT gateway and use VPC interface endpoints for ECR/CloudWatch/Secrets Manager if you don't need outbound internet from the tasks. Yahoo Finance is a public endpoint though, so for this app the NAT is required.
