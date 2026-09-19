# Food Risk Analysis — Database Backup & Recovery Runbook

## 1. Backup Strategy & Cadence

| Parameter | Policy | Description |
| :--- | :--- | :--- |
| **Backup Type** | Full logical SQL dump | `pg_dump -F p -b -v` |
| **Cadence** | Automated Daily | Executed at 02:00 UTC |
| **Retention** | 7 Days Local | Automated pruning of files older than 7 days |
| **Storage Location** | `./backups/` & Encrypted Offsite | Encrypted S3 / Cloud Storage |
| **Pre-Release Snapshot** | Mandatory | Snapshot taken before applying migrations |

---

## 2. Automated Backup Execution

Execute the backup script:

### PowerShell (Windows / Developer)
```powershell
.\scripts\db-backup.ps1 -BackupDir ".\backups" -RetentionDays 7
```

### Dockerized Execution
```bash
docker compose exec -T postgres pg_dump -U postgres -d food_risk_analysis > ./backups/foodrisk_backup_$(date +%Y%m%d_%H%M%S).sql
```

---

## 3. Disaster Recovery & Restore Procedure

In the event of database failure or corrupted state:

1. **Notify Stakeholders & Pause Traffic**:
   Route incoming traffic to maintenance page if required.
2. **Execute Database Restore**:
   ```powershell
   .\scripts\db-restore.ps1 -BackupFile ".\backups\foodrisk_food_risk_analysis_20260918.sql"
   ```
3. **Verify Data Integrity**:
   - Inspect user record count
   - Verify active and historical analysis sessions
   - Execute production smoke test suite:
     ```powershell
     .\scripts\production-smoke-test.ps1
     ```

---

## 4. Periodic Restore Drill Verification

- **Frequency**: Monthly recovery simulation in staging environment.
- **Objective**: Ensure that backup dumps remain readable, uncorrupted, and accurately reconstruct tables, indices, and foreign keys.
