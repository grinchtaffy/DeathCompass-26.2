# Automated GitHub compile for Death Compass 1.3

The workflow builds via Java 25 and Gradle 9.5.1, then validates actual entries inside
the playable JAR (metadata, entrypoint, HUD, settings, secondary-key mixins, hazard sounds).

Upload ALL project files and folder contents at repository root, including `scripts/`.
Run **Actions > Build and Verify Death Compass** and download the verified artifact only
if it finishes with a green check. A green check is not a substitute for in-game testing.

For any red check, open the failed build step and provide the FIRST compiler or workflow
error (do not just send `Process completed with exit code 1`).
