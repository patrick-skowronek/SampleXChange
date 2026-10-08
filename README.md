# SampleXChange

SampleXChange converts FHIR resources from the [MII KDS profiles](https://simplifier.net/medizininformatikinitiative-modulbiobank/~resources?category=Profile) to the [BBMRI profiles](https://simplifier.net/bbmri.de/~resources?category=Profile). Conversion to MIABIS is planned. The tool is meant for exchanging data in a standardized way between a biobank and a data integration center.

## Environment configuration
The tool is configured entirely through the environment variables below.

### Operation mode
You choose the conversion by naming an input format and an output format.

- `SOURCE_FORMAT`: `MII_2025` or `MII_2026`
- `TARGET_FORMAT`: `BBMRI_DE` or `MIABIS_V3`

All four combinations are accepted, although the MIABIS output is still incomplete (see [To do](#to-do)). An unknown pair stops the tool at startup with a list of the valid ones.

If you are upgrading: these two variables replace the old `PROFILE` variable. `PROFILE=MII2BBMRI` becomes `SOURCE_FORMAT=MII_2025` with `TARGET_FORMAT=BBMRI_DE`.

#### What gets exported
The tool exports the aliquot group, which is the level directly above the aliquots, and any specimen that was never aliquoted. Mother samples and individual aliquots are not exported because bbmri.de and MIABIS do not count them as samples. Cell lines and organoids are skipped for the same reason.

MII 2026 states the sample level of each specimen, so the tool reads the hierarchy straight from the data. MII 2025 has no such marker, and the tool infers the level from the parent links instead. That inference is an approximation: on the official 2026 examples it disagrees with the declared levels in both directions, so output from MII 2025 data carries the same uncertainty.

### SSL configuration
SSL verification is set per server. Setting a variable to `true` turns verification off for that server, so the tool accepts self-signed certificates. Use this with care in production.
- `SOURCE_DISABLE_SSL`: defaults to `false`.
- `TARGET_DISABLE_SSL`: defaults to `false`.

### FHIR server configuration
Each server needs an authentication type: `KEYCLOAK`, `BEARER`, `BASIC` or `NONE`. Which other variables you need depends on that type. Use `NONE` for a server that is open or protected by the surrounding network.

#### Source FHIR server
- `SOURCE_URL`: URL of the source FHIR server.
- `SOURCE_AUTH_TYPE`: one of `KEYCLOAK`, `BEARER`, `BASIC`, `NONE`.
- `SOURCE_USERNAME` / `SOURCE_PASSWORD`: credentials for `BASIC`.
- `SOURCE_BEARERTOKEN`: static token for `BEARER`.
- `SOURCE_KEYCLOAK_TOKEN_URL`, `SOURCE_KEYCLOAK_CLIENT_ID`, `SOURCE_KEYCLOAK_CLIENT_SECRET`: required for `KEYCLOAK`.

#### Target FHIR server
- `TARGET_URL`: URL of the target FHIR server.
- `TARGET_AUTH_TYPE`: one of `KEYCLOAK`, `BEARER`, `BASIC`, `NONE`.
- `TARGET_USERNAME` / `TARGET_PASSWORD`: credentials for `BASIC`.
- `TARGET_BEARERTOKEN`: static token for `BEARER`.
- `TARGET_KEYCLOAK_TOKEN_URL`, `TARGET_KEYCLOAK_CLIENT_ID`, `TARGET_KEYCLOAK_CLIENT_SECRET`: required for `KEYCLOAK`.

`.env.example` has a complete template.

## Usage
Set the environment variables for your setup, then run the tool locally or with Docker. Afterwards, check that the resources on the target server look the way you expect.

## To do
- The conversion to MIABIS is at an early stage and will be developed further.

## Contributions
Issues, feature requests and pull requests are welcome.

## AI disclaimer
This project was written with the support of Claude AI by Anthropic.

## License
This project is licensed under the Apache 2.0 License. See the [LICENSE](./LICENSE) file for details.
