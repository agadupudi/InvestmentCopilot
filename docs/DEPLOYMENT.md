# Deployment (AWS)

Step-by-step guide to deploy Investment Co-Pilot to your AWS account so it's reachable on the public internet.

**Target architecture:**
- **Backend** → ECS Fargate behind an Application Load Balancer
- **Database** → RDS PostgreSQL 16
- **Cache** → ElastiCache Redis 7
- **Frontend** → AWS Amplify Hosting (auto-builds from GitHub)
- **Infra-as-code** → AWS CDK (TypeScript) in `infra/cdk/`

**Estimated cost:** ~$80/mo at idle (see [cost section](#cost-breakdown)).

---

## Prerequisites

| Tool | Why | Install |
|---|---|---|
| **AWS account** | Where you're deploying | https://signup.aws.amazon.com |
| **AWS CLI v2** | Auth + ECR push | `brew install awscli` |
| **AWS credentials** | Authenticate the CLI | `aws configure` (Access Key + Secret Key) — needs `AdministratorAccess` or equivalent on first run for `cdk bootstrap` |
| **Node 20+** | Run CDK | `brew install node` |
| **Docker** | Build the backend image | OrbStack (`brew install --cask orbstack`) |
| **GitHub repo** | Source for Amplify auto-deploys | Push this repo to GitHub if you haven't already |

Quick sanity check:
```bash
aws sts get-caller-identity     # must succeed
node --version                  # v20+
docker --version
```

Pick a region (this guide uses `us-east-1`):
```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
```

---

## Step 1 — Bootstrap CDK (one time per account/region)

```bash
cd infra/cdk
npm install
npx cdk bootstrap aws://${AWS_ACCOUNT_ID}/${AWS_REGION}
```

This creates the CDK staging bucket, KMS key, and IAM roles that `cdk deploy` needs. Idempotent — safe to re-run.

---

## Step 2 — Validate the CDK app

```bash
npx cdk synth
```

Outputs the synthesized CloudFormation. If this fails, fix the TypeScript before deploying.

---

## Step 3 — Deploy the backend infrastructure

```bash
npx cdk deploy --all --require-approval never
```

Takes ~15–20 minutes (RDS and ALB are the slow ones). When it finishes, capture the outputs printed at the bottom:

```
InvestmentCopilot-Data.DbEndpoint     = ic-…rds.amazonaws.com
InvestmentCopilot-Data.DbSecretArn    = arn:aws:secretsmanager:…
InvestmentCopilot-Data.RedisEndpoint  = ic-…cache.amazonaws.com
InvestmentCopilot-App.EcrRepositoryUri = <ACCOUNT>.dkr.ecr.us-east-1.amazonaws.com/investment-copilot-backend
InvestmentCopilot-App.AlbDnsName       = InvestmentCopilot-App-Alb-…elb.amazonaws.com
InvestmentCopilot-App.ClusterName      = InvestmentCopilot-App-Cluster…
InvestmentCopilot-App.ServiceName      = InvestmentCopilot-App-Service…
```

At this point the Fargate service is pulling an image tag (`latest`) that doesn't exist yet — tasks will keep failing. That's expected. Push the image in Step 4.

---

## Step 4 — Build and push the backend Docker image

From the **repo root**:

```bash
# 1. Log Docker into your ECR registry
aws ecr get-login-password --region ${AWS_REGION} | \
  docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

# 2. Build (multi-stage; takes a few minutes on first build)
docker build -f backend/Dockerfile -t investment-copilot-backend:latest .

# 3. Tag + push
ECR_URI=${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/investment-copilot-backend
docker tag investment-copilot-backend:latest ${ECR_URI}:latest
docker push ${ECR_URI}:latest
```

> **Apple Silicon note:** ECS Fargate runs `linux/amd64` by default. If you're on M-series Mac, build with `--platform=linux/amd64`:
> ```bash
> docker buildx build --platform=linux/amd64 -f backend/Dockerfile -t investment-copilot-backend:latest --load .
> ```

Force the ECS service to pick up the new image:

```bash
aws ecs update-service \
  --cluster <ClusterName from CDK output> \
  --service <ServiceName from CDK output> \
  --force-new-deployment \
  --region ${AWS_REGION}
```

Watch the rollout:

```bash
aws ecs describe-services \
  --cluster <ClusterName> \
  --services <ServiceName> \
  --region ${AWS_REGION} \
  --query 'services[0].{runningCount:runningCount,desiredCount:desiredCount,events:events[0:3]}'
```

You want `runningCount == desiredCount == 1`. If the task keeps cycling, check CloudWatch Logs:

```bash
aws logs tail /aws/ecs/<LogGroup> --since 5m --follow --region ${AWS_REGION}
```

(Find the exact log group name in the AppStack CloudWatch outputs or in the AWS console.)

---

## Step 5 — Verify the backend is reachable

```bash
ALB=<AlbDnsName from CDK output>
curl http://${ALB}/health
# → {"status":"ok","db":true,"redis":true}
```

Flyway will have already run on first boot (auto-applied), so the `holdings` table exists.

Quick smoke test:
```bash
curl -X POST http://${ALB}/holdings \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","quantity":"10","cost_basis":"150.50"}'

curl http://${ALB}/holdings
```

---

## Step 6 — Deploy the frontend (Amplify Hosting)

1. Open the [AWS Amplify Console](https://console.aws.amazon.com/amplify/).
2. **Create new app** → **Host web app** → **GitHub** → authorize and pick this repo + branch (`main`).
3. Amplify detects the `amplify.yml` at the repo root — leave the default build settings.
4. **Environment variables** — add:

   | Key | Value |
   |---|---|
   | `NEXT_PUBLIC_API_BASE_URL` | `http://<AlbDnsName>` |
   | `AMPLIFY_MONOREPO_APP_ROOT` | `frontend` |
   | `_LIVE_UPDATES` | `[{"name":"Next.js version","pkg":"next-version","type":"internal","version":"latest"}]` |

5. **Save and deploy**. First build takes ~5 minutes.
6. Once it goes green, open the Amplify-provided URL (`https://<branch>.<app-id>.amplifyapp.com`).

Every subsequent `git push` to `main` triggers an Amplify rebuild.

> **Mixed content warning:** if Amplify gives you an HTTPS URL but the ALB is HTTP, the browser will block API calls. Either add an ACM cert + HTTPS listener to the ALB (Step 7) or use Amplify's preview environments over HTTP for now.

---

## Step 7 — (Optional) Custom domain + HTTPS

1. **Route 53 hosted zone** for your domain (e.g. `mycopilot.app`).
2. **ACM cert** in `us-east-1` for `mycopilot.app` and `api.mycopilot.app` (DNS-validated).
3. **ALB HTTPS listener** — manually add a `:443` listener using the cert; point at the existing target group. Optionally redirect `:80` → `:443`.
4. **Route 53 alias records:**
   - `api.mycopilot.app` → ALB alias
   - `mycopilot.app` → Amplify alias (set up via Amplify console "Domain management")
5. Update Amplify env var `NEXT_PUBLIC_API_BASE_URL` → `https://api.mycopilot.app` and trigger a rebuild.

---

## Updating the deployment

| Change | What to do |
|---|---|
| Backend code | `docker build`, `docker push`, `aws ecs update-service --force-new-deployment` |
| Frontend code | `git push origin main` — Amplify auto-builds |
| Infrastructure | edit `infra/cdk/lib/*`, `npx cdk diff`, `npx cdk deploy --all` |
| Database schema | drop a new `V<n>__<msg>.sql` in `backend/src/main/resources/db/migration/`, rebuild + redeploy the backend — Flyway applies it on next boot |
| RDS password rotation | the Secrets Manager secret is wired with auto-rotation off by default; enable in the console or add a CDK construct for `SecretRotation` |

---

## Tear down

To avoid charges:

```bash
# 1. Delete the Amplify app via the console (Actions → Delete app).
# 2. Empty + delete the ECR images:
aws ecr batch-delete-image \
  --repository-name investment-copilot-backend \
  --image-ids "$(aws ecr list-images --repository-name investment-copilot-backend --query 'imageIds[*]' --output json)"

# 3. Destroy the CDK stacks (~10 min):
cd infra/cdk
npx cdk destroy --all
```

RDS takes a final snapshot before deletion (`RemovalPolicy.SNAPSHOT`). Delete that snapshot manually if you don't want to keep it.

---

## Cost breakdown

us-east-1, smallest sustainable footprint, on-demand pricing as of 2026:

| Resource | $/month |
|---|---|
| Fargate `0.25 vCPU / 0.5 GB`, 24/7 | ~$10 |
| RDS `db.t4g.micro` Postgres + 20 GB gp3 | ~$13 |
| ElastiCache `cache.t4g.micro` Redis | ~$12 |
| Application Load Balancer | ~$16 |
| NAT Gateway (1 AZ) | ~$32 |
| Data transfer + CloudWatch logs | ~$5 |
| **Total** | **~$88/mo** |

Amplify hosting for a low-traffic side project stays in the free tier.

**Cost-saving options:**
- Drop the NAT gateway and add VPC interface endpoints for ECR/CloudWatch/Secrets Manager (~$22 savings) — but Yahoo Finance is a public endpoint, so the task still needs outbound, so NAT stays.
- Schedule the Fargate service to scale to 0 overnight (`ApplicationAutoScaling` + cron) if usage is bursty.
- Replace ALB + Fargate with a single **App Runner** service (no ALB cost, pay-per-use). Trade-off: less control over networking and harder VPC integration with RDS.

---

## Future hardening (Phase 5 follow-ups)

- **GitHub Actions CI/CD** — automate `docker build → push → ECS update` on merge to main.
- **CloudWatch alarms** — alert on ECS task crash loop, ALB 5xx surge, RDS CPU/storage.
- **AWS X-Ray** — distributed tracing for the backend (add `spring-cloud-aws-xray-starter`).
- **WAF** in front of the ALB and Amplify for basic abuse protection.
- **Secrets rotation** via Secrets Manager Lambda for the RDS password.
- **Multi-AZ RDS** + ECS desired count = 2 — for any real production use.
- **Separate AWS account** for prod vs dev (CDK supports multi-env out of the box).
