# Commons Backstage-Connect

This project contains clients for communicating with Backstage-Connect services (e.g., Usage Service).

## JSON Web Token Structure

The JSON Web Token contains the following data entries,

- `v` header (string), which is the version of our JWT structure (e.g., `1.0`)
- `key` claim (string), containing a unique customer-assigned identifier
- `iat` claim (long integer), which is the standard JWT issued-at-time
- [optional] `url` claim (string), which will override the service-URL, and is useful for testing

## JSON Web Token JAR

To generate the JAR file, containing a JWT with the customer-assigned "key", use the following,

```
jar cmf MANIFEST.MF forgerock-backstage-connect-config.jar backstage-connect.jwt
```

The file `backstage-connect.jwt` is plain-text, containing a signed and encoded JSON Web Token (JWT). The `MANIFEST.MF`
must contain OSGi metadata (e.g., `Bundle-SymbolicName`), in order to be read within an OSGi environment
(e.g., OpenIDM).

For example,

```
Manifest-Version: 1.0
Bundle-Description: ForgeRock Backstage Connect Client Config
Bundle-License: Commercial
Bundle-ManifestVersion: 2
Bundle-Name: ForgeRock Backstage Connect Client Config
Bundle-SymbolicName: org.forgerock.backstage.connect.config
Bundle-Vendor: ForgeRock AS
Bundle-Version: 1.0.0
Implementation-Build: 0
SCM-Revision: 0
```

Note that this same JWT can alternatively be provided to products via the `BACKSTAGE_CONNECT_JWT` environment variable,
for use cases such as Docker images. However, we consider the embedded JAR solution to be more customer friendly, for
downloads directed from the Backstage Connect website.
