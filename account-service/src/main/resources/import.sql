-- Seeds fijos del reto (specs/02-account-service-spec.md §5)
INSERT INTO accounts (id, account_number, customer_id, balance, currency, status, version, created_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', 'ACC-000001', '11111111-1111-1111-1111-111111111111', 1000.00, 'PEN', 'ACTIVE', 0, CURRENT_TIMESTAMP);

INSERT INTO accounts (id, account_number, customer_id, balance, currency, status, version, created_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000002', 'ACC-000002', '22222222-2222-2222-2222-222222222222', 500.00, 'PEN', 'ACTIVE', 0, CURRENT_TIMESTAMP);

INSERT INTO accounts (id, account_number, customer_id, balance, currency, status, version, created_at)
VALUES ('aaaaaaaa-0000-0000-0000-000000000003', 'ACC-000003', '22222222-2222-2222-2222-222222222222', 250.00, 'PEN', 'BLOCKED', 0, CURRENT_TIMESTAMP);
