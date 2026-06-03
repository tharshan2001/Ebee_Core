# Category API Reference

## Base URL

```
http://localhost:8085/api/categories
```

## Authentication

Read endpoints (`GET`) are accessible to any authenticated user. Mutations (`POST`, `PUT`, `DELETE`) require ADMIN role:

```
Authorization: Bearer <JWT_TOKEN>
```

---

## Soft Delete

Categories use **soft delete** — deleting a category sets `active` to `false` instead of removing the row. The category disappears from the dropdown (`GET /api/categories`) but existing products referencing it continue to work. Re-creating a category with the same name reactivates it.

---

## Endpoints

### List Categories (Dropdown)

**GET** `/api/categories`

Returns only active categories, sorted alphabetically.

**Response:**
```json
{
  "success": true,
  "message": "Categories retrieved successfully",
  "data": [
    {
      "id": "529bddea-8a4e-4f44-b7b1-312bba763182",
      "name": "Books",
      "active": true
    },
    {
      "id": "cfc48831-53c8-4303-a60f-fe22a7446d56",
      "name": "Clothing",
      "active": true
    }
  ]
}
```

---

### Get Category by ID

**GET** `/api/categories/{id}`

Returns the category regardless of active status (so inactive categories can still be inspected).

**Response:**
```json
{
  "success": true,
  "message": "Category retrieved successfully",
  "data": {
    "id": "54398ebc-da4f-4fbf-92e6-8786421e425d",
    "name": "Garden",
    "active": false
  }
}
```

---

### Create Category

**POST** `/api/categories`

**Request body:**
```json
{
  "name": "Furniture"
}
```

- `name` — required, max 100 characters, trimmed
- If an inactive category with the same name exists, it is **reactivated**
- If an active category with the same name exists, returns **400**

**Response (201):**
```json
{
  "success": true,
  "message": "Category created successfully",
  "data": {
    "id": "5279c795-787c-4eee-b1c2-400c127e17bb",
    "name": "Furniture",
    "active": true
  }
}
```

---

### Update (Rename) Category

**PUT** `/api/categories/{id}`

**Request body:**
```json
{
  "name": "Home & Furniture"
}
```

- `name` — required, max 100 characters
- Automatically reactivates the category if it was inactive
- Name uniqueness is enforced against other **active** categories only

**Response:**
```json
{
  "success": true,
  "message": "Category updated successfully",
  "data": {
    "id": "5279c795-787c-4eee-b1c2-400c127e17bb",
    "name": "Home & Furniture",
    "active": true
  }
}
```

---

### Delete Category (Soft Delete)

**DELETE** `/api/categories/{id}`

Sets `active` to `false`. The category is hidden from the dropdown but still exists in the database. Products referencing this category are unaffected.

- Returns **400** if already deleted

**Response:**
```json
{
  "success": true,
  "message": "Category deleted successfully",
  "data": null
}
```

---

## Product Integration

When creating or updating a product, the `category` field is validated against **active** categories only:

```bash
curl -X POST http://localhost:8085/api/products \
  -H "Authorization: Bearer <TOKEN>" \
  -F "name=Chair" -F "itemCode=CH001" -F "category=Furniture" -F "price=150" -F "costPrice=80"
```

If the category does not exist or has been soft-deleted, the API returns:

```json
{
  "success": false,
  "message": "Category not found: Furniture",
  "data": null
}
```

---

## Error Responses

| Status | Example Message | When |
|--------|-----------------|------|
| 400 | `Category already exists: ...` | Duplicate active name |
| 400 | `Category is already deleted` | Double delete |
| 400 | `Category not found: ...` | Product references inactive/missing category |
| 400 | `name: Category name is required` | Blank name |
| 400 | `name: Category name must not exceed 100 characters` | Name too long |
| 404 | `Category not found with id: '...'` | Invalid UUID |
