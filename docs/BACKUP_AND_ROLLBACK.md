# DriveSphere Production Backup, Restoration & Deployment Rollback Procedures

## 1. Production Database Backup Strategy

### 1.1 Automated Full Logical Backup (`mysqldump`)
Execute consistent online backups using InnoDB transaction isolation without locking production read/write operations:

```bash
#!/bin/bash
# scripts/backup-mysql.sh
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="/var/backups/drivesphere"
BACKUP_FILE="${BACKUP_DIR}/drivesphere_backup_${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

mysqldump \
  --host="${DB_HOST:-127.0.0.1}" \
  --port="${DB_PORT:-3306}" \
  --user="${DB_USERNAME}" \
  --password="${DB_PASSWORD}" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --hex-blob \
  --set-gtid-purged=OFF \
  "${DB_NAME:-drivesphere}" | gzip -9 > "${BACKUP_FILE}"

# Enforce secure file permissions (owner read-only)
chmod 600 "${BACKUP_FILE}"

# Replicate to off-site immutable object storage (AWS S3 / GCS)
# aws s3 cp "${BACKUP_FILE}" s3://drivesphere-encrypted-backups/daily/
```

### 1.2 Binary Log Retention (Point-in-Time Recovery - PITR)
Ensure MySQL binary logging (`log_bin = ON`) is active with 7-day retention to support point-in-time recovery to any exact transaction prior to an incident.

---

## 2. Restoration & Verification Procedure

### 2.1 Restoration Testing to Staging Schema
Before any restore to production, test and validate the backup file on a disposable schema:

```bash
# 1. Create a clean verification database
mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" -e \
  "DROP DATABASE IF EXISTS drivesphere_restore_test; CREATE DATABASE drivesphere_restore_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. Decompress and restore data
gunzip -c /var/backups/drivesphere/drivesphere_backup_YYYYMMDD_HHMMSS.sql.gz | \
  mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" drivesphere_restore_test

# 3. Validate Flyway schema history integrity
mvn -f backend/pom.xml flyway:validate \
  -Dflyway.url="jdbc:mysql://localhost:3306/drivesphere_restore_test" \
  -Dflyway.user="${DB_USERNAME}" \
  -Dflyway.password="${DB_PASSWORD}"

# 4. Verify table and record sanity
mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" drivesphere_restore_test -e \
  "SELECT 'users' AS tbl, COUNT(*) AS count FROM users UNION ALL \
   SELECT 'vehicles', COUNT(*) FROM vehicles UNION ALL \
   SELECT 'bookings', COUNT(*) FROM bookings UNION ALL \
   SELECT 'payments', COUNT(*) FROM payments;"
```

---

## 3. Deployment Rollback Procedures

### 3.1 Application Rollback (Docker Compose)
If a critical runtime defect, latency spike, or HTTP 500 regression is detected post-deployment:

```bash
# Step 1: Export previous known-stable Docker image tag
export DRIVESPHERE_IMAGE_TAG="v1.0.0-stable"

# Step 2: Gracefully redeploy previous backend and frontend containers
docker compose -f docker-compose.prod.yml up -d --no-deps --build drivesphere-backend drivesphere-frontend

# Step 3: Verify container health status
docker compose -f docker-compose.prod.yml ps
curl -f http://localhost:8080/api/health
```

### 3.2 Database Migration Rollback Strategy
1. **Additive-First Schema Rule**: DriveSphere Flyway migrations (`V1` to `V6`) follow the backward-compatible expansion pattern. New columns (`hold_expires_at`, `preferred_vehicle_type`, `last_service_odometer_km`) are nullable or carry safe defaults, allowing the previous backend version to execute against the updated schema without rollback.
2. **Critical Schema Failure / Data Corruption**:
   If a migration severely corrupted state:
   ```bash
   # Stop application traffic
   docker compose -f docker-compose.prod.yml stop drivesphere-backend

   # Restore from pre-deployment snapshot
   mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" drivesphere < pre_deployment_snapshot.sql

   # Restart with previous known-stable application image
   docker compose -f docker-compose.prod.yml start drivesphere-backend
   ```

---

## 4. Post-Rollback Verification Checklist
- [ ] Backend health check returns `{"status":"UP"}` on `GET /api/health`.
- [ ] Authenticated Customer profile retrieval succeeds without HTTP 500.
- [ ] Flyway migrations match expected baseline in `flyway_schema_history`.
- [ ] Razorpay webhook endpoint responds `200 OK` with valid signature verification.
- [ ] Driver and customer real-time GPS telemetry connections are operational.

