# Legal Research MCP stack module

Platform Zero module for the authenticated legal-research MCP service. It overlays the Kotlin service, immutable container build, component metadata, service contracts, and runtime definition.

## Validation

Run the complete module contract and security suite from a workspace containing the generator:

```sh
../sso-stack-generator/scripts/test-module.sh --all .
```

A live smoke test requires the deployed PostgreSQL, OpenSearch, and Keycloak dependencies.

