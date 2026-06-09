# Admin Database Backup API

**Base URL:** `http://localhost:8085/api/admin/db`

## Authentication

All endpoints require JWT with `ADMIN` role:

```
Authorization: Bearer <JWT_TOKEN>
```

Non-admin users receive `403 Forbidden`.

---

## Create Backup

**POST** `/api/admin/db/backup`

Triggers a `pg_dump` of the database. The dump file is verified with `pg_restore -l` after creation. Corrupt backups are deleted immediately and the request fails.

**Response (200):**
```json
{
  "success": true,
  "message": "Backup created successfully",
  "data": {
    "name": "ebee_db_20260609_133154.dump",
    "size": 139469,
    "path": "/Users/antonabitharshan/sites/Ebee_Core/backups/ebee_db_20260609_133154.dump",
    "verified": true
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Filename (timestamped) |
| `size` | `long` | File size in bytes |
| `path` | `String` | Absolute path on server |
| `verified` | `boolean` | Whether `pg_restore -l` passed |

**Error (backup corrupt):**
```json
{
  "success": false,
  "message": "An unexpected error occurred: Backup verification failed — file is corrupt",
  "data": null
}
```

---

## List Backups

**GET** `/api/admin/db/backups`

Returns all `.dump` files in the backups directory, sorted newest first.

**Response:**
```json
{
  "success": true,
  "message": "Backups retrieved successfully",
  "data": [
    {
      "name": "ebee_db_20260609_133154.dump",
      "size": 139469,
      "lastModified": 1780992114138
    },
    {
      "name": "ebee_db_20260609_132459.dump",
      "size": 139442,
      "lastModified": 1780991699426
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Filename |
| `size` | `long` | File size in bytes |
| `lastModified` | `long` | Unix timestamp (millis) of last modification |

---

## Download Backup

**GET** `/api/admin/db/backups/{filename}`

Downloads a backup file as `application/octet-stream` with `Content-Disposition: attachment`.

**Example:**
```bash
curl -O -J "http://localhost:8085/api/admin/db/backups/ebee_db_20260609_133154.dump" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:** Binary stream with headers:
```
HTTP/1.1 200 OK
Content-Type: application/octet-stream
Content-Length: 139469
Content-Disposition: attachment; filename="ebee_db_20260609_133154.dump"
```

**Error (not found):** `404` with standard error response.

---

## Upload Backup

**POST** `/api/admin/db/backups/upload`

Upload a `.dump` file to the server's backups directory. The file is verified with `pg_restore -l` after upload. If a file with the same name exists, it is auto-renamed with a `_1`, `_2`, etc. suffix.

**Request:** `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | `MultipartFile` | Yes | The `.dump` file to upload |

**Example:**
```bash
curl -X POST "http://localhost:8085/api/admin/db/backups/upload" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@my_backup.dump"
```

**Response:**
```json
{
  "success": true,
  "message": "Backup uploaded successfully",
  "data": {
    "name": "my_backup.dump",
    "size": 139469,
    "verified": true
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Final filename on server (may differ if renamed) |
| `size` | `long` | File size in bytes |
| `verified` | `boolean` | Whether `pg_restore -l` passed |

**Errors:**
- `400`: File is empty
- `400`: File not a valid dump (`verified: false`)

---

## Restore Backup

**POST** `/api/admin/db/restore?filename=`

Restores the database from a backup file. **Destructive operation** — the current database is dropped and recreated.

**Safety:** The server automatically creates a backup **before** restoring, so you always have a rollback point.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `filename` | `String` | Yes | Name of the `.dump` file in the backups directory |

**Example:**
```bash
curl -X POST "http://localhost:8085/api/admin/db/restore?filename=ebee_db_20260609_133154.dump" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response:**
```json
{
  "success": true,
  "message": "Restore complete. Pre-restore backup: ebee_db_20260609_133200.dump",
  "data": {
    "restoredFrom": "ebee_db_20260609_133154.dump",
    "preRestoreBackup": "ebee_db_20260609_133200.dump"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `restoredFrom` | `String` | The backup file that was restored |
| `preRestoreBackup` | `String` | Auto-created pre-restore backup filename |

**Errors:**
- `404`: Backup file not found
- `400`: Dump file is invalid or corrupt
- `500`: Restore process failed (dropdb, createdb, or pg_restore error)

---

## CLI Equivalent

These operations use the same underlying commands as the `scripts/db.sh` script:

| API Endpoint | CLI Command |
|---|---|
| `POST /backup` | `./scripts/db.sh backup` |
| `GET /backups` | `./scripts/db.sh list` |
| `POST /restore?filename=...` | `./scripts/db.sh restore backups/...` |
| `POST /backups/upload` | Manual file copy to `backups/` directory |

---

## Typical UI Flow

1. **Dashboard loads** → `GET /api/admin/db/backups` → displays backup list
2. **Admin clicks "Backup Now"** → `POST /api/admin/db/backup` → new backup appears in list
3. **Admin selects a backup** → option to download or restore
4. **Admin clicks "Download"** → `GET /api/admin/db/backups/{filename}` → file downloads
5. **Admin clicks "Restore"** → confirmation dialog → `POST /api/admin/db/restore?filename=...` → pre-restore backup created, then restored
6. **Admin drag-and-drops a file** → `POST /api/admin/db/backups/upload` → file uploaded and verified
