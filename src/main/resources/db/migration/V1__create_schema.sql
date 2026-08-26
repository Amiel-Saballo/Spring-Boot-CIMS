CREATE TABLE permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE app_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE RESTRICT
);

CREATE TABLE user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(160) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    role_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_user_account_role FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE RESTRICT
);

CREATE TABLE unit_of_measure (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE clinic_location (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE system_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    category VARCHAR(20) NOT NULL,
    unit_of_measure_id BIGINT NOT NULL,
    reorder_level INT NOT NULL,
    reorder_quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_item_reorder_level CHECK (reorder_level BETWEEN 0 AND 100),
    CONSTRAINT chk_item_reorder_quantity CHECK (reorder_quantity BETWEEN 0 AND 500),
    CONSTRAINT fk_item_uom FOREIGN KEY (unit_of_measure_id) REFERENCES unit_of_measure(id) ON DELETE RESTRICT
);

CREATE TABLE supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(180) NOT NULL UNIQUE,
    contact_person VARCHAR(160),
    contact_no VARCHAR(80),
    address VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE receiving_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    received_by BIGINT NOT NULL,
    approved_by BIGINT,
    status VARCHAR(20) NOT NULL,
    ref_no VARCHAR(100) NOT NULL UNIQUE,
    date_received DATE NOT NULL,
    remarks VARCHAR(150),
    return_reason VARCHAR(150),
    cancellation_reason VARCHAR(150),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_receiving_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receiving_received_by FOREIGN KEY (received_by) REFERENCES user_account(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receiving_approved_by FOREIGN KEY (approved_by) REFERENCES user_account(id) ON DELETE RESTRICT
);

CREATE TABLE receiving_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiving_transaction_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    brand VARCHAR(120),
    batch_number VARCHAR(120),
    expiry_date DATE,
    model VARCHAR(120),
    serial_number VARCHAR(160),
    asset_tag VARCHAR(160),
    location_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_receiving_line_qty CHECK (quantity > 0),
    CONSTRAINT fk_receiving_line_tx FOREIGN KEY (receiving_transaction_id) REFERENCES receiving_transaction(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receiving_line_item FOREIGN KEY (item_id) REFERENCES item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receiving_line_location FOREIGN KEY (location_id) REFERENCES clinic_location(id) ON DELETE RESTRICT
);

CREATE TABLE batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    receiving_transaction_id BIGINT NOT NULL,
    batch_number VARCHAR(120),
    quantity_received INT NOT NULL,
    on_hand INT NOT NULL,
    expiry_date DATE,
    brand VARCHAR(120),
    location_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_batch_quantity_received CHECK (quantity_received >= 0),
    CONSTRAINT chk_batch_on_hand CHECK (on_hand >= 0),
    CONSTRAINT fk_batch_item FOREIGN KEY (item_id) REFERENCES item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_receiving FOREIGN KEY (receiving_transaction_id) REFERENCES receiving_transaction(id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_location FOREIGN KEY (location_id) REFERENCES clinic_location(id) ON DELETE RESTRICT
);

CREATE TABLE equipment_unit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    receiving_transaction_id BIGINT NOT NULL,
    asset_tag VARCHAR(160) NOT NULL UNIQUE,
    serial_number VARCHAR(160) NOT NULL UNIQUE,
    brand VARCHAR(120),
    model VARCHAR(120),
    location_id BIGINT NOT NULL,
    acquired_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_equipment_item FOREIGN KEY (item_id) REFERENCES item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_equipment_receiving FOREIGN KEY (receiving_transaction_id) REFERENCES receiving_transaction(id) ON DELETE RESTRICT,
    CONSTRAINT fk_equipment_location FOREIGN KEY (location_id) REFERENCES clinic_location(id) ON DELETE RESTRICT
);

CREATE TABLE issuance_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference_number VARCHAR(100) NOT NULL UNIQUE,
    date_issued DATE NOT NULL,
    employee_number VARCHAR(80) NOT NULL,
    employee_name VARCHAR(160) NOT NULL,
    department VARCHAR(120),
    supervisor VARCHAR(160),
    chief_complaint VARCHAR(255) NOT NULL,
    disposition VARCHAR(120) NOT NULL,
    remarks VARCHAR(500),
    recorded_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_issuance_recorded_by FOREIGN KEY (recorded_by) REFERENCES user_account(id) ON DELETE RESTRICT
);

CREATE TABLE issuance_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    issuance_transaction_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_issuance_line_qty CHECK (quantity > 0),
    CONSTRAINT fk_issuance_line_tx FOREIGN KEY (issuance_transaction_id) REFERENCES issuance_transaction(id) ON DELETE RESTRICT,
    CONSTRAINT fk_issuance_line_item FOREIGN KEY (item_id) REFERENCES item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_issuance_line_batch FOREIGN KEY (batch_id) REFERENCES batch(id) ON DELETE RESTRICT
);

CREATE TABLE disposal_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference_number VARCHAR(100) NOT NULL UNIQUE,
    disposal_date DATE NOT NULL,
    item_id BIGINT NOT NULL,
    batch_id BIGINT,
    equipment_unit_id BIGINT,
    quantity INT NOT NULL,
    reason VARCHAR(180) NOT NULL,
    remarks VARCHAR(500),
    recorded_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_disposal_qty CHECK (quantity > 0),
    CONSTRAINT fk_disposal_item FOREIGN KEY (item_id) REFERENCES item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_disposal_batch FOREIGN KEY (batch_id) REFERENCES batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_disposal_equipment FOREIGN KEY (equipment_unit_id) REFERENCES equipment_unit(id) ON DELETE RESTRICT,
    CONSTRAINT fk_disposal_user FOREIGN KEY (recorded_by) REFERENCES user_account(id) ON DELETE RESTRICT
);

CREATE TABLE transaction_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_type VARCHAR(20) NOT NULL,
    transaction_date TIMESTAMP(6) NOT NULL,
    reference_number VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    item_id BIGINT,
    quantity_before INT,
    quantity_after INT,
    detail VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_transaction_log_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transaction_log_item FOREIGN KEY (item_id) REFERENCES item(id) ON DELETE RESTRICT
);

CREATE TABLE report_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_type VARCHAR(40) NOT NULL,
    generated_by BIGINT NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    parameters_json VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_report_generated_by FOREIGN KEY (generated_by) REFERENCES user_account(id) ON DELETE RESTRICT
);

CREATE INDEX idx_receiving_status_date ON receiving_transaction(status, date_received);
CREATE INDEX idx_batch_item_status_expiry ON batch(item_id, status, expiry_date);
CREATE INDEX idx_equipment_item_status ON equipment_unit(item_id, status);
CREATE INDEX idx_issuance_date ON issuance_transaction(date_issued);
CREATE INDEX idx_transaction_log_date ON transaction_log(transaction_date);
CREATE INDEX idx_transaction_log_type_item ON transaction_log(transaction_type, item_id);
