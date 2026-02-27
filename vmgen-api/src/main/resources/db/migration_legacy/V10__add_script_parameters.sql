-- Parameterised scripts for configurable behaviour at request time.

-- Parameter definitions attached to a script.
CREATE TABLE script_parameters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,

    -- Identification
    parameter_name VARCHAR(100) NOT NULL,       -- e.g. "DATABASE_PASSWORD"
    display_name VARCHAR(255) NOT NULL,         -- e.g. "Database password"
    description TEXT,
    display_order INTEGER DEFAULT 0,

    -- Type and value source
    parameter_type VARCHAR(20) NOT NULL,        -- 'string', 'integer', 'choice', 'boolean', 'path'
    value_source VARCHAR(30) NOT NULL DEFAULT 'user_input',
                                                -- 'user_input', 'auto_random', 'auto_choice', 'user_input_or_random'

    -- User input configuration
    default_value TEXT,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    validation_rules JSONB,

    -- Random / auto-generation configuration
    random_config JSONB,

    -- For future use (e.g. allowing the learner to override an auto-choice)
    user_can_override BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (script_id, parameter_name),

    CONSTRAINT chk_script_parameters_value_source CHECK (
        value_source IN ('user_input', 'auto_random', 'auto_choice', 'user_input_or_random')
    ),

    CONSTRAINT chk_script_parameters_type CHECK (
        parameter_type IN ('string', 'integer', 'choice', 'boolean', 'path')
    ),

    CONSTRAINT chk_script_parameters_name_format CHECK (
        parameter_name ~ '^[A-Z_]+$'
    )
);

-- Choice values for parameters of type "choice".
CREATE TABLE script_parameter_choices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parameter_id UUID NOT NULL REFERENCES script_parameters(id) ON DELETE CASCADE,

    choice_value VARCHAR(255) NOT NULL,
    choice_label VARCHAR(255) NOT NULL,
    description TEXT,
    display_order INTEGER DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (parameter_id, choice_value)
);

-- Concrete parameter values for a given VM request and script.
CREATE TABLE vm_request_script_parameters (
    vm_request_id UUID NOT NULL REFERENCES vm_requests(id) ON DELETE CASCADE,
    script_id UUID NOT NULL REFERENCES scripts(id),
    parameter_name VARCHAR(100) NOT NULL,
    parameter_value TEXT NOT NULL,
    was_auto_generated BOOLEAN NOT NULL DEFAULT FALSE,

    PRIMARY KEY (vm_request_id, script_id, parameter_name)
);

CREATE INDEX idx_script_parameters_script ON script_parameters(script_id, display_order);
CREATE INDEX idx_parameter_choices_param ON script_parameter_choices(parameter_id, display_order);
CREATE INDEX idx_vm_request_script_params_request ON vm_request_script_parameters(vm_request_id);

