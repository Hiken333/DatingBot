-- Initialization script for PostgreSQL with PostGIS

-- Create PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create spatial indexes function
CREATE OR REPLACE FUNCTION create_spatial_indexes() RETURNS void AS $$
BEGIN
    -- This will be executed after tables are created by Liquibase
    RAISE NOTICE 'PostGIS initialized successfully';
END;
$$ LANGUAGE plpgsql;

-- Set timezone
SET timezone = 'UTC';

