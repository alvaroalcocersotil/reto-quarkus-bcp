-- Seeds fijos del reto (specs/01-customer-service-spec.md §5)
INSERT INTO customers (id, first_name, last_name, document_type, document_number, email, phone_number, status, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Maria', 'Garcia', 'DNI', '87654321', 'maria.garcia@mail.com', '+51987654321', 'ACTIVE', CURRENT_TIMESTAMP);

INSERT INTO customers (id, first_name, last_name, document_type, document_number, email, phone_number, status, created_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'Juan', 'Perez', 'DNI', '12345678', 'juan.perez@mail.com', '+51912345678', 'SUSPENDED', CURRENT_TIMESTAMP);
