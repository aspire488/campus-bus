# CAMPUS BUS — final release audit

**Status: PASS WITH LIMITATIONS** (2026-10-11) — build and all tests pass, primary student and admin workflows verified in the running app, the real database is intact. Limitations are listed at the end; none is critical.

## 1. Build and tests

| | Baseline (start of this pass) | Final |
|---|---|---|
| `mvn clean compile` | BUILD SUCCESS | BUILD SUCCESS (exit 0) |
| `mvn clean test` | 109 / 109 passed | **128 / 128 passed** (92 model, 36 real-MySQL on `college_bus_test`) after the GitHub-release changes below |
| `mvn clean compile exec:java` | — | window opens: "CAMPUS BUS · SCMS School of Engineering and Technology, Karukutty"; 0 errors in the log |

## 2. Controls

`docs/final-control-checklist.csv`: **21 screens / areas, 73 controls and behaviours — 69 PASS, 3 UNTESTED, 1 BLOCKED, 0 FAIL.**

- UNTESTED: full Tab focus order (Tab from username to password and Enter verified), the two "My reservations" shortcut buttons on the seat summary and the pass (same handler as the verified menu item).
- BLOCKED: printing on paper (deliberately not done on the real printer; the dialog and a Microsoft Print to PDF page were verified).

Create / edit / delete tested in the GUI on the test database: buses (add, edit, remove, refused while booked), routes (add with untimed stops, edit, remove, refused while used), timetable, schedule import (twice, idempotent), students (register, edit, remove), reservations (book, keep, cancel, rebook), administrators (add, remove, last-admin and self protection), institution name.

## 3. Fixed in this pass (3-round limit respected: 2 rounds used)

| # | Defect | Fix | Regression evidence |
|---|---|---|---|
| 1 | Printed pass included the live tracker and the paper-coloured background | print stylesheet: pass only, white page, colours kept, no page break inside | PDF via Microsoft Print to PDF inspected |
| 2 | Date wrapped on the printed pass | facts kept on one line | PDF |
| 3 | 1024×640: search results crushed (route text 80 px wide, "View seats" cut, sideways scroll) | two-line result rows and two-row filters on narrow pages | scrollWidth = window width at both sizes; screenshots |
| 4 | Admission numbers such as `SCS/10001/26` were rejected | 6–20 letters, digits, `/`, `-`, at least one digit; "Admission number" wording | `admissionNumbersAsIssued`, `badRegisterNumbersRejected`, `studentsSignInWithTheirAdmissionNumber` |
| 5 | No department / class structure (S1–S8, CS, CT, CO, EC, …) | class codes like `S3 CS2`; department derived (`Student.departmentOf`); semesters limited to S1–S8; suggestions follow the term (odd Jul–Dec, even Jan–Jun) | `departmentComesFromTheClassCode` (found a lower-case bug, fixed), `semestersAreOneToEight` |
| 6 | No department filtering for the office | Students: Class + Department columns and filter; Reservations: Dept column and filter (tables now support several filters) | GUI: CS → 26 of 154 students, 29 of 205 reservations |

Code changed: `Student`, `Reservation`, `ReservationDAO`, `StudentDAO`, `CollegeBusSystem`, `LoginFrame`, `StudentDashboard`, `AdminDashboard`, `TablePanel`, `style.css`, `database/demo-data.sql`, tests, `README.md`.

## 4. Skills and reviewers used (this project)

Ponytail (mode, `/ponytail-audit`), Karpathy guidelines, feature-dev workflow, Superpowers verification-before-completion (red-green proof of the route-edit lock-out test), Octopus security audit (single-provider, no external models), code-simplifier, PR Review Toolkit code reviewer. All reviewer findings were checked against the code before fixing; all confirmed findings were fixed with tests. Not used: commit commands (not a Git repository).

## 5. Name scan

Case-insensitive search for the AI-assistant vendor and product names listed in the release brief, over every project file (contents, names, hidden files, docs, resources, configuration): **no matches**. Root entries: `.gitignore`, `README.md`, `database/`, `db.properties` (local, git-ignored), `db.properties.example`, `docs/`, `pom.xml`, `src/`. Build output removed with `mvn clean`.

## 6. Real database (`college_bus_db`)

| | Start of pass | After final checks |
|---|---|---|
| reservations / students / buses / routes | 31 / 10 / 30 / 28 | 31 / 10 / 30 / 28 |
| admins | scms.transport | scms.transport |
| institution | SCMS School of Engineering and Technology, Karukutty | unchanged |

Only change: signing in upgraded the admin's password hash to PBKDF2 600,000 iterations (intended). All destructive tests ran on `college_bus_test`; backups of the real database from before the SCMS migration are kept outside the project.

## 7. Security

Verified: prepared statements only; PBKDF2-HMAC-SHA256 (600k, random salt, constant-time compare, transparent upgrade, no timing hint for unknown names); student/admin separation enforced in Java, not only hidden in the UI; removed admins locked out, including open dialogs; first-run setup atomic and impossible once an admin exists; last admin protected; seat and one-seat-each-way rules backed by unique keys; demo data with public passwords only loaded on request (`-Ddemo=true`); no secrets in tracked files.

Residual risk: every installed copy holds the MySQL user and password in `db.properties` — anyone with that file can reach MySQL directly. Normal for a desktop JDBC application; a server API would be needed to remove it.

## 8. UI and print

Checked at 1024×640 and 1320×816 on login, search, seat map (25-stop route, 49 seats), pass, My reservations, admin tables and the admin seat map. Print: the system dialog opens with the installed printer; the printed PDF is one A4 page containing the pass only.

## 9. Remaining issues (by severity)

1. **Medium — credentials on every client** (see §7). Out of scope for a desktop app.
2. **Low — keyboard:** Tab focus order and Enter-to-submit not verified with real key presses.
3. **Low — route-line layout** has no automated test (verified by screenshots only).
4. **Low — admin tables read MySQL on the UI thread:** instant on localhost; a slow remote server could pause the window briefly.
5. **Low — the PDF does not state campus arrival or evening times;** the app shows the institution's settings as targets and estimates, and 5 routes plus 5 transcription points still need transport-office confirmation (`docs/scms-import-report.md`).
6. **Info — department codes ME, CE, AU, VL** are suggestions chosen for this demo (the institution may use other codes; the field accepts any text).

## 10. GitHub release: launcher and acceptance (2026-10-11)

Build: `mvn clean compile`, `mvn clean test` (128/128), `mvn clean package` (jar) all BUILD SUCCESS. Launcher `run-campus-bus.cmd`: window opens; Java missing / Maven missing / `db.properties` missing each print a clear message and exit 1.

Changes in this step: class typed into the drop-down is always saved (registration, profile, admin edit); new-password rule (later simplified on request to 4-64 characters); admin sign-in side states where admins are created.

Acceptance, synthetic students `TEST/10001/26`, `TEST/10002/26` (S3 CS2) and `TEST/10003/26` (S1 CS1) on `college_bus_test` only:

| # | Case | Result | Evidence |
|---|---|---|---|
| 1 | Register 3; reject duplicate admission no. / email, blank name, bad email, bad phone, mismatch, bad admission no., too-short password (`123`) | PASS | each message seen; rows and classes checked in MySQL |
| 2 | Valid login (lower-case admission no.), wrong password, unknown user, blank, logout; real keys: type, Tab, Enter | PASS | dashboard opened from real keystrokes |
| 3 | Search Kaloor (8 trips), unknown stop and bus (0); printed / estimated / campus-target times | PASS | |
| 4 | Book seat 5, shown in My reservations | PASS | |
| 5 | Second student, same seat | PASS | "Seat 5 was just taken" |
| 6 | Cancel (owner only; twice refused), rebook, seat free again; seat-map click, stop picker, confirm and cancel in the GUI | PASS | MySQL rows BOOKED / CANCELLED |
| 7 | Profile: invalid phone refused, valid save | PASS | MySQL |
| 8 | Admin login; student credentials refused on admin side; Students + Reservations department filters | PASS | CS 2 rows, EC 0 |
| 9 | Bus add / duplicate / edit / keep / remove; booked bus not removed; student removed (cannot sign in); admin add / duplicate / mismatch / remove | PASS | MySQL; route CRUD and admin cancellation from the previous pass, not repeated |
| 10 | Restart: booking, profile edit, removed student persisted | PASS | |
| 11 | MySQL stopped: clear message, no crash; restarted: saving works | PASS | 0 exceptions in log |
| 12 | Pass page, 11-stop route line, 1024x640 without sideways scroll, shutdown / relaunch | PASS WITH LIMITATION | paper printing BLOCKED; Print-to-PDF from the previous pass |

Real database after all steps: 31 reservations, 10 students, 30 buses (unchanged).
