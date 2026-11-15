# Verification and deployment

- [x] Run focused quota/store/hook/cinematic tests, then core JUnit and build compatible variants.
- [x] Run `qa/run-phase9-regression.sh`; report exact limits of headless evidence.
- [x] Review touched source, preserve unrelated dirty files and validate UTF-8.
- [x] Update existing player/admin documentation.
- [x] Back up Skyblock's current EMF JAR, upload EMF alone to both SFTP targets and verify SHA-256 readback.
- [x] Record artifact, test results, upload/backup paths and remaining live checks in `reports/verification-and-deployment.md`.

Files: core `autofish/*`, native cinematic service and accepted-catch dispatcher, quota/hook/store/cinematic JUnit tests, command AutoFish handlers in 1.20/1.21/26.1, shared plugin permission metadata, existing fishing documentation.

Risks: native visuals and real server performance cannot be certified by unit tests; BoxPvP has no existing EMF JAR and will initialize EMF defaults on first startup. Rollback: restore the backed-up Skyblock JAR; remove the newly added BoxPvP JAR before restart if needed. Do not edit runtime data or enable Simulator.
