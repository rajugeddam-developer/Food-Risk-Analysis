# Food Risk Analysis — Production Rollback Plan

## 1. Overview & Rollback Principles

When a production release experiences an unrecoverable failure (such as critical security defect, severe regression, or database failure), the deployment must roll back to the previous known-stable release in a controlled, non-destructive manner.

```text
Production Deployment
       ↓
 Automated Smoke Test
   ↙        ↘
PASS        FAIL / REGRESSION
 ↓               ↓
Continue     Execute Rollback Plan
                 ├── 1. Revert Container Images
                 ├── 2. Verify Database Schema Compatibility
                 ├── 3. Health & Smoke Test Validation
                 └── 4. Post-Incident Review
```

---

## 2. Immediate Container Rollback Procedure

If the application fails health checks or smoke tests after deploying a new release:

```bash
# 1. Stop current containers
docker compose down

# 2. Update .env image tag or git commit to previous known-good tag
git checkout v1.0.0-previous

# 3. Rebuild or pull previous stable container images
docker compose up -d

# 4. Verify service restoration
docker compose ps
curl -f http://localhost:8080/api/health
```

---

## 3. Database Schema Rollback Guidelines

Food Risk Analysis utilizes Flyway declarative migrations:
- `V1__create_users_table.sql`: Base user accounts
- `V2__create_food_analysis_sessions_table.sql`: Ephemeral sessions

### Safety Rule
- **Additive Migrations Only**: Production migrations must be backward-compatible with the immediately preceding application version.
- **Never Run Destructive DDL**: Never drop columns or rename active tables in a minor release.
- **Emergency Restore from Snapshot**:
  If data corruption occurs:
  ```powershell
  .\scripts\db-restore.ps1 -BackupFile ".\backups\pre_deployment_snapshot.sql"
  ```

---

## 4. Post-Rollback Validation Checklist

- [ ] Liveness probe returns `HTTP 200` (`/api/health`)
- [ ] Database connectivity verified (`"database":"UP"`)
- [ ] Authentication workflow verified (`.\scripts\production-smoke-test.ps1`)
- [ ] Ephemeral session creation and retrieval verified
- [ ] Incident post-mortem documented with root-cause analysis
