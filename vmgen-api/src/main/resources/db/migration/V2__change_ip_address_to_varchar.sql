-- Change ip_address from INET to VARCHAR for simplicity and portability
ALTER TABLE audit_log ALTER COLUMN ip_address TYPE VARCHAR(45);

