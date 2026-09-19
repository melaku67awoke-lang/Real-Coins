# Real-Coins Fix/Test Report

- Admin UI: mobile/phone layout, cyan + white + dark palette, no bright green/glow.
- Admin stats: Pending Users and Total Users (verified) labels.
- RC price: backend-authoritative GET/POST endpoints; admin-only update; immutable admin audit entry.
- Fixed internal USD/ETB rate: 186 ETB per USD; dashboard price remains RC/USD only.
- Backend JS syntax: PASS (`node --check`).
- Full Android APK build: NOT RUN locally because Gradle wrapper JAR/executable is absent from source package.
- Backend npm test suite: NOT RUN because node_modules is absent in the working package.
