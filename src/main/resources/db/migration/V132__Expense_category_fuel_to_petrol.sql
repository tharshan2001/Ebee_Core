-- ExpenseCategory.FUEL was replaced by PETROL (and MILEAGE) without migrating existing rows.
-- Any FUEL row made GET /api/expenses fail ("No enum constant ...FUEL"), breaking the whole list.
UPDATE expenses SET category = 'PETROL' WHERE category = 'FUEL';
