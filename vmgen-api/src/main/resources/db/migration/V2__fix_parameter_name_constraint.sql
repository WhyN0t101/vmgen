-- Allow digits in script parameter names (e.g. VAR1, DB2_HOST).
-- The original constraint only matched [A-Z_]; digits are needed because
-- the script-content auto-detector uses {{VAR1}} style placeholders.
ALTER TABLE script_parameters
    DROP CONSTRAINT chk_script_parameters_name_format;

ALTER TABLE script_parameters
    ADD CONSTRAINT chk_script_parameters_name_format
        CHECK (parameter_name ~ '^[A-Z_][A-Z0-9_]*$');
