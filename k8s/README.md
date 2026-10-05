# Kubernetes manifests

One file per service (Deployment + Service), applied by the GitHub Actions
pipeline in `.github/workflows/`. Each manifest mirrors its entry in
`app/docker-compose.yml`. The image is written as `${IMAGE}`; the pipeline
substitutes the image it just built and pushed
(`ghcr.io/<owner>/<repo>/<service>:<commit sha>`).

## Pipeline

A push or merge to `master` that touches `services/<service>/**` (or that
service's manifest/workflow) runs `.github/workflows/<service>.yml`:

1. **test**: the service's own test suite (Maven, Go, Yarn or pytest)
2. **build & push image**: `services/<service>/Dockerfile` to GHCR, tagged with the commit sha and `latest`
3. **deploy to kubernetes**: `kubectl apply` of `k8s/<service>.yaml`, then `kubectl rollout status`

Only the services that changed are built and deployed. A change to
`.github/workflows/deploy.yml` redeploys all of them. Every workflow can also be
started by hand from the Actions tab (`workflow_dispatch`).

## One-time setup

### GitHub

| Where | Name | Value |
|---|---|---|
| Secret | `KUBE_CONFIG` | `base64 < kubeconfig` of a user/service account that can apply Deployments and Services in the namespace |
| Variable (optional) | `K8S_NAMESPACE` | target namespace, defaults to `ecommerce` |

The deploy job runs in the `production` environment, so the secret can live
there and the environment can require a reviewer before anything is deployed.

### Cluster

The manifests expect the namespace to already contain the infrastructure,
reachable under the same names as in compose: `postgres`, `keycloak`,
`rabbitmq`, `redis`, `minio` and `mailpit`. The database must be migrated
(`app/init/migrate.sh`) and the Keycloak realm provisioned
(`app/init/keycloak-init.sh`) before the services can start.

```bash
kubectl create namespace ecommerce

# Pull access to GHCR (only needed while the packages are private).
# The token needs the read:packages scope.
kubectl -n ecommerce create secret docker-registry ghcr \
  --docker-server=ghcr.io --docker-username=<github user> --docker-password=<token>

# Credentials the services read. Same values as the compose .env.
kubectl -n ecommerce create secret generic ecommerce-secrets \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD='...' \
  --from-literal=POSTGRES_PASSWORD_URLENCODED='...' \
  --from-literal=RABBITMQ_USER=admin \
  --from-literal=RABBITMQ_PASSWORD='...' \
  --from-literal=RABBITMQ_PASSWORD_URLENCODED='...' \
  --from-literal=REDIS_USER=admin \
  --from-literal=REDIS_PASSWORD='...' \
  --from-literal=MINIO_ROOT_USER=admin \
  --from-literal=MINIO_ROOT_PASSWORD='...' \
  --from-literal=AUTH_SERVICE_CLIENT_SECRET='...' \
  --from-literal=NOTIFICATION_SERVICE_CLIENT_SECRET='...'
# SMTP_USER and SMTP_PASS are optional keys; add them when the SMTP server needs auth.
```

### Deploying by hand

```bash
IMAGE=ghcr.io/<owner>/<repo>/order-service:<sha> \
  envsubst '$IMAGE' < k8s/order-service.yaml | kubectl apply -n ecommerce -f -
```
