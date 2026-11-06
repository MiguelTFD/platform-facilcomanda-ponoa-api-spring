SELECT 'CREATE DATABASE fcp_keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fcp_keycloak')\gexec
