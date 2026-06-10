# Distributed Lovable — AI App Builder on Java Microservices

A [Lovable.dev](https://lovable.dev)-style AI application builder, built as a distributed system of **Spring Boot microservices**. Users chat with an LLM, and the platform generates code, manages project files in object storage, and deploys live previews to Kubernetes.

## Architecture

```
                        ┌─────────────┐
        User ──────────▶│ API Gateway │  Spring Cloud Gateway (8080)
                        └──────┬──────┘
                               │ JWT-secured routes (/api/v1/**)
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
┌───────────────┐     ┌───────────────┐     ┌────────────────────┐
│ Account       │◀────│ Workspace     │◀────│ Intelligence       │
│ Service       │Feign│ Service       │Feign│ Service            │
│ (9050)        │     │ (9020)        │     │ (9030)             │
│ Auth, JWT,    │     │ Projects,     │     │ AI chat (Spring AI │
│ Stripe billing│     │ Files (MinIO),│     │ + OpenAI/OpenRouter│
│ Plans         │     │ K8s deploys,  │     │ ), code generation,│
│               │     │ Kafka events  │     │ context gathering  │
└───────┬───────┘     └───────┬───────┘     └─────────┬──────────┘
        │                     │                       │
        ▼                     ▼                       ▼
   PostgreSQL          PostgreSQL + MinIO        PostgreSQL
                       + Redis + Kafka

  Supporting services: Config Server (8888, Spring Cloud Config),
  Service Discovery (8761, Eureka), common-lib (shared JWT filter,
  Feign interceptor, DTOs)
```

### Key design points

- **Centralized configuration** — Spring Cloud Config Server serves all service configs from `config-repo/`; secrets are injected via environment variables, never committed.
- **Decentralized security** — every service validates JWTs itself via a shared `JwtAuthFilter` (common-lib); a `FeignClientInterceptor` propagates the caller's token across service-to-service calls.
- **Service discovery & load balancing** — Netflix Eureka + Spring Cloud LoadBalancer (`lb://` URIs in the gateway).
- **Event-driven file updates** — Kafka topics decouple file-storage events between services.
- **Object storage** — project source files live in MinIO (S3-compatible); new projects are bootstrapped from a starter template (`react-vite-tailwind-daisyui-starter`).
- **Plan-based limits** — Free/Pro plans (seeded automatically) cap projects and daily AI tokens; Stripe handles subscription billing via webhooks.

## Tech stack

Java 21 · Spring Boot 4 · Spring Cloud (Gateway, Config, Eureka, OpenFeign) · Spring AI · Spring Security + JJWT · PostgreSQL · Redis · Apache Kafka · MinIO · Stripe · MapStruct · Lombok · Docker · Kubernetes (Fabric8) · Jib

## Running locally

Prerequisites: JDK 21, Maven, Docker Desktop.

```bash
# 1. Start infrastructure (Postgres, Redis, Kafka, MinIO + bucket/template seeding)
docker compose up -d

# 2. Build the shared library first
cd common-lib && mvn clean install -DskipTests "-Djib.skip=true" && cd ..

# 3. Build every service
for s in config-service discovery-service api-gateway account-service workspace-service intelligence-service; do
  (cd $s && mvn clean package -DskipTests "-Djib.skip=true")
done

# 4. Start services IN THIS ORDER (each from its own folder)
java -jar config-service/target/config-service-0.0.1-SNAPSHOT.jar        # 8888
java -jar discovery-service/target/discovery-service-0.0.1-SNAPSHOT.jar  # 8761
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar              # 8080 (use --server.port=8081 if 8080 is busy)
java -jar account-service/target/account-service-0.0.1-SNAPSHOT.jar      # 9050
java -jar workspace-service/target/workspace-service-0.0.1-SNAPSHOT.jar  # 9020
java -jar intelligence-service/target/intelligence-service-0.0.1-SNAPSHOT.jar # 9030
```

> **Note:** run the config-service from inside its module folder (or set `CONFIG_REPO_PATH`) so it finds `../config-repo`.

### Optional environment variables

| Variable | Purpose |
|---|---|
| `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` | Real Stripe test keys for billing flows |
| `AI_API_KEY` | OpenRouter/OpenAI key for AI chat & code generation |
| `JWT_SECRET` | Override the dev JWT signing key |

### Smoke test

```bash
# Sign up (field is `username`, holding the email)
curl -X POST localhost:8080/api/v1/account/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"you@example.com","name":"You","password":"Secret@123"}'

# Create a project with the returned token
curl -X POST localhost:8080/api/v1/workspace/projects \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"my-first-app"}'
```

## Deployment

Kubernetes manifests live in `k8s/` (namespaces, network policies, ingress, stateful sets for Postgres/Redis/Kafka/MinIO, per-service deployments). Copy `k8s/.env.example` to `k8s/.env` and fill in real secrets.

Container images are built with **Jib** (no Dockerfiles needed): `mvn package jib:build`.

Reference CI/CD workflows (GitHub Actions → GKE with Workload Identity Federation) are kept in `docs/reference/gke-workflows/`. An AWS deployment pipeline is in progress.
