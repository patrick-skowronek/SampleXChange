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
Each FHIR server URL must be an http or https URL ending in `/fhir`, such as `http://localhost:8080/fhir`.

The authentication type is `KEYCLOAK`, `BEARER`, `BASIC` or `NONE`, and defaults to `NONE` when it is not set.

#### Source FHIR server
- `SOURCE_URL`: URL of the source FHIR server.
- `SOURCE_AUTH_TYPE`: one of `KEYCLOAK`, `BEARER`, `BASIC`, `NONE` (default).
- `SOURCE_USERNAME` / `SOURCE_PASSWORD`: credentials for `BASIC`.
- `SOURCE_BEARERTOKEN`: static token for `BEARER`.
- `SOURCE_KEYCLOAK_TOKEN_URL`, `SOURCE_KEYCLOAK_CLIENT_ID`, `SOURCE_KEYCLOAK_CLIENT_SECRET`: required for `KEYCLOAK`.

#### Target FHIR server
- `TARGET_URL`: URL of the target FHIR server.
- `TARGET_AUTH_TYPE`: one of `KEYCLOAK`, `BEARER`, `BASIC`, `NONE` (default).
- `TARGET_USERNAME` / `TARGET_PASSWORD`: credentials for `BASIC`.
- `TARGET_BEARERTOKEN`: static token for `BEARER`.
- `TARGET_KEYCLOAK_TOKEN_URL`, `TARGET_KEYCLOAK_CLIENT_ID`, `TARGET_KEYCLOAK_CLIENT_SECRET`: required for `KEYCLOAK`.

`.env.example` has a complete template.

## How to test
Two Docker Compose files let you try the tool on your own data. `blazes.yml` starts two Blaze FHIR servers, the source on `http://localhost:8081/fhir` and the target on `http://localhost:8082/fhir`. `docker-compose.yml` runs SampleXChange against them.

1. Start the two servers:
   ```bash
   docker compose -f blazes.yml up -d
   ```
2. Upload your data to the source server as a FHIR transaction bundle:
   ```bash
   curl -X POST -H 'Content-Type: application/fhir+json' --data-binary @my-bundle.json http://localhost:8081/fhir
   ```
   To see how it works without your own data, use one of the examples in `src/test/resources`: `mii.json` for MII 2025 or `mii2026-bundle.json` for MII 2026.
3. Run SampleXChange:
   ```bash
   docker compose up
   ```
4. Look at the result on the target server, for example at `http://localhost:8082/fhir/Specimen`.

`docker-compose.yml` is set to `SOURCE_FORMAT=MII_2025`. For MII 2026 data, change it to `MII_2026`.

## Usage
Set the environment variables for your setup, then run the tool locally or with Docker. Afterwards, check that the resources on the target server look the way you expect.

## Keeping the target up to date
SampleXChange runs as a single job. Each run writes every exported resource to the target with its source id, so existing resources are updated and new ones added. SampleXChange never deletes anything from the target, so a resource removed from the source stays there.

To keep the target in line with the source, clear the target server and run a full export regularly, for example once a month.

## To do
- The conversion to MIABIS is at an early stage and will be developed further.

## Contributions
Issues, feature requests and pull requests are welcome.

## AI disclaimer
This project was written with the support of Claude AI by Anthropic.

## License
This project is licensed under the Apache 2.0 License. See the [LICENSE](./LICENSE) file for details.
