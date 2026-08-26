# Updating the Eclipse Project

If you already imported an earlier generated CIMS project into Eclipse:

1. Stop the running Spring Boot application.
2. Back up your existing project folder if you made personal code changes.
3. Replace the project files with this updated version, or import this ZIP as a fresh Maven project.
4. In Eclipse, right-click the project → **Maven → Update Project…**.
5. Enable **Force Update of Snapshots/Releases** and click OK.
6. Run **Project → Clean**.
7. Start MySQL.
8. Run `ClinicInventoryApplication.java` again.
9. Open **http://localhost:8080/**. Do not open `index.html` directly from the filesystem.
10. Sign in. Data entered from one browser is stored through the REST API in MySQL and can be read by another browser after signing in.

If you previously used the old localStorage prototype, that old browser-local mock data is intentionally not imported into MySQL because it was not authoritative database data.
