# CAMPUS BUS — Bus Reservation System for Schools, Colleges and Universities

KTU S3 B.Tech CSE · Object Oriented Programming course project
**Java · Swing · HTML5 / CSS3 / JavaScript (JavaFX WebView) · JDBC · MySQL 8**

![Login](docs/screenshots/01-login.png)

A desktop application for any educational institution's bus service. Students find a bus that passes their stop, see the time it reaches that stop, pick a seat on a picture of the bus (green = free, red = booked) and get a bus pass. The transport office sets one timetable for the whole institution, manages buses, routes, students, reservations and administrator accounts, and sees who sits where.

This implementation **expands the initial proposal (PPT)**: every module, class, method and OOP concept from the PPT is kept; the seat map, route lines, two-way buses, timetable, live position estimate, bus pass and admin analytics are additions.

**Two ways to use it:**

| | Who it is for | Needs |
|---|---|---|
| **[Windows release (zip)](#1-windows-release-download-the-zip-and-run)** | anyone running the app or giving the demo | Windows 10/11 64-bit and internet once. **No Java, no Maven, no MySQL install** |
| **[From source](#3-build-and-run-from-source)** | developers and evaluators reading the code | JDK 17+, Maven 3.8+, MySQL 8 |

### Screenshots

Taken from the running app with synthetic test accounts (no real student data).

| Student | |
|---|---|
| ![Home](docs/screenshots/02-student-home.png) Home: next bus and where it is | ![Find a bus](docs/screenshots/03-find-a-bus.png) Find a bus (earlier version: the search now asks for Trip + Your stop) |
| ![Seat map](docs/screenshots/04-seat-map.png) Seat map and stop picker | ![Bus pass](docs/screenshots/05-campus-pass.png) Bus pass |
| ![My reservations](docs/screenshots/06-my-reservations.png) My reservations | |

| Admin | |
|---|---|
| ![Overview](docs/screenshots/07-admin-overview.png) Overview | ![Buses](docs/screenshots/08-admin-buses.png) Buses |
| ![Schedules](docs/screenshots/09-admin-schedules.png) Schedules | ![Reservations](docs/screenshots/10-admin-reservations.png) Reservations with department filter |
| ![Admin seat map](docs/screenshots/11-admin-seat-map.png) Seat map with passenger list | |

---

## 1. Windows release: download the zip and run

### The release file

| | |
|---|---|
| Release page | https://github.com/aspire488/campus-bus/releases/tag/v1.1 |
| File | **`CAMPUS-BUS-1.1-windows-x64.zip`** (76 MB) |
| Direct download | https://github.com/aspire488/campus-bus/releases/download/v1.1/CAMPUS-BUS-1.1-windows-x64.zip |
| SHA-256 | `f5d88a27d932e88a61587d998820d263fe70acabdc001bc6e2a7ba917e90d558` |
| Built from | the commit tagged [`v1.1`](https://github.com/aspire488/campus-bus/tree/v1.1) |

> **v1.1** contains the October 2026 changes: Trip + Your stop search, the confirmation email with the PDF pass, the safety fixes, and admin messages that no longer hide behind an older one. The previous release stays available at https://github.com/aspire488/campus-bus/releases/tag/v1.0.

The zip contains the app, its libraries, **a bundled Java 25 runtime** (Eclipse Temurin, made with `jlink`) and three scripts. You do **not** install Java or Maven. MySQL is not inside the zip: `setup-windows.cmd` downloads it once (see below).

**Needs:** Windows 10 or 11, 64-bit · about 1 GB free disk (the zip, the unpacked app and MySQL) · 4 GB RAM or more · internet for the first setup · the Microsoft Visual C++ runtime (already on most PCs; setup checks it and gives the download link if it is missing).

### Option A: with the mouse

1. Open the **Direct download** link above. The browser saves `CAMPUS-BUS-1.1-windows-x64.zip` to *Downloads*.
2. Right-click the zip → **Extract All…** → choose a folder (e.g. the Desktop) → **Extract**. Spaces in the folder name are fine.
   Do **not** double-click the scripts while they are still inside the zip; they only work in the extracted folder.
3. Open the extracted folder `CAMPUS-BUS-1.1-windows-x64` and double-click **`setup-windows.cmd`** (once per computer and Windows user).
4. Double-click **`run-campus-bus.cmd`** to open the app (every time).

### Option B: with PowerShell commands

Open **PowerShell** (Start menu → type *PowerShell*) and paste these lines one block at a time:

```powershell
# 1. download the zip to your Downloads folder
Set-Location "$env:USERPROFILE\Downloads"
Invoke-WebRequest -Uri "https://github.com/aspire488/campus-bus/releases/download/v1.1/CAMPUS-BUS-1.1-windows-x64.zip" -OutFile "CAMPUS-BUS-1.1-windows-x64.zip" -UseBasicParsing

# 2. check it is the genuine file: must print True
(Get-FileHash ".\CAMPUS-BUS-1.1-windows-x64.zip" -Algorithm SHA256).Hash -eq "F5D88A27D932E88A61587D998820D263FE70ACABDC001BC6E2A7BA917E90D558"

# 3. unzip to the Desktop and go into the folder
Expand-Archive ".\CAMPUS-BUS-1.1-windows-x64.zip" -DestinationPath "$env:USERPROFILE\Desktop" -Force
Set-Location "$env:USERPROFILE\Desktop\CAMPUS-BUS-1.1-windows-x64"

# 4. first time only: private MySQL + demo database (takes a few minutes)
.\setup-windows.cmd

# 5. every time: open the app
.\run-campus-bus.cmd
```

In PowerShell a script in the current folder must be started with `.\` in front (`.\run-campus-bus.cmd`). In the old **Command Prompt** (`cmd.exe`) use `cd /d "%USERPROFILE%\Desktop\CAMPUS-BUS-1.1-windows-x64"` and then `setup-windows.cmd` / `run-campus-bus.cmd` without `.\`.

### What the three scripts do

| Script | When | What it does |
|---|---|---|
| **`setup-windows.cmd`** | once | Downloads MySQL 8.4.11 (280 MB) from mysql.com and refuses it unless its SHA-256 matches. Installs a **private MySQL for this Windows user** in `%LOCALAPPDATA%\CampusBus`: no admin rights, port **3310**, reachable only from this computer. Creates the demo database **`campus_bus_demo`** with fictional data and the administrator `demo.admin`, gives every account a **new random password**, and writes the settings (`db.properties`) and the sign-ins (`demo-accounts.txt`) to `%APPDATA%\CampusBus`, a folder only this Windows user can read. Then it opens `demo-accounts.txt` in Notepad. It never prints a password. Running it again changes nothing. Measured on the developer's PC: 101 seconds including the download; allow 5–10 minutes on a slower connection. |
| **`run-campus-bus.cmd`** | every time | Starts that MySQL, opens CAMPUS BUS with the bundled Java, and stops MySQL again when you close the app. The black console window stays open while the app runs; do not close it. |
| **`reset-demo.cmd`** | when the demo data should start fresh | After you type `DELETE`, deletes **only** `campus_bus_demo` (every booking and account made during demos) and creates it again with **new** passwords (see `demo-accounts.txt` again). Anything else typed cancels it. |

**Offline setup:** download `https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.11-winx64.zip` on another computer and copy it next to `setup-windows.cmd`; setup then uses (and checks) that file instead of downloading.

### Demo sign-in

Open `%APPDATA%\CampusBus\demo-accounts.txt` (Notepad opens it after setup; later: press Win+R, paste `notepad %APPDATA%\CampusBus\demo-accounts.txt`, Enter). It lists:

- **Admin** (login screen → *Admin*): `demo.admin` and its password
- **Students** (login screen → *Student*): `student@college.example.com`, `ananya@college.example.com`, `rahul@college.example.com` with their passwords, and one shared password for the other 147 demo students

The passwords are created on that computer and exist nowhere else; keep the file private. The demo data is fictional: *Demo College of Engineering*, 5 routes, 13 buses, 150 students and about 800 reservations around the current date (815 when counted after a fresh setup).

### Presentation day: checklist

1. **The day before**, on the laptop you will present with: download, unzip, run `setup-windows.cmd`, then open the app once with `run-campus-bus.cmd` and sign in as admin and as a student. This removes the internet and download risk.
2. Keep `demo-accounts.txt` open, or write the sign-ins down.
3. Start the app with `run-campus-bus.cmd` (not anything inside the `CAMPUS BUS` subfolder).
4. To show a clean demo: `reset-demo.cmd` → type `DELETE` → use the **new** passwords from `demo-accounts.txt`.

### Troubleshooting (Windows release)

| You see | Do this |
|---|---|
| *Windows protected your PC* (SmartScreen) on a `.cmd` file | Click **More info** → **Run anyway**. The scripts are plain text and the app runs on the signed Java in the folder. |
| *The Microsoft Visual C++ runtime is missing* | Install https://aka.ms/vs/17/release/vc_redist.x64.exe, then run `setup-windows.cmd` again. |
| *Download failed* | Check the internet, or use the offline setup above. |
| *No database settings yet. Run setup-windows.cmd once* | Setup has not been run for this Windows user; run it. |
| *Port 3310 is used by another program* | Restart Windows and try again. |
| The app says *Database unavailable* / *Can't reach MySQL* | Close the app and start it again with `run-campus-bus.cmd` (it starts the database). |
| *CAMPUS BUS was not found next to this script* | You ran a script from inside the zip or moved it. Extract the whole zip and run the scripts in the extracted folder. |
| `setup-windows.cmd : The term ... is not recognized` in PowerShell | Type `.\setup-windows.cmd` (with `.\`) inside the extracted folder. |

**Remove everything:** delete the extracted folder, `%LOCALAPPDATA%\CampusBus` (MySQL and the demo data) and `%APPDATA%\CampusBus` (settings and passwords).

### Security notes

- MySQL listens only on `127.0.0.1:3310`; other computers cannot connect. Its root password and the app password are random, stored in `%APPDATA%\CampusBus` with access for the current Windows user only, and never printed.
- The app refuses to log in to MySQL as `root` without a password, and the demo setup refuses any database other than `campus_bus_demo`.
- The release is not code-signed. That is why there is no `CAMPUS BUS.exe`: an unsigned `.exe` was blocked by Windows Smart App Control in testing, while the bundled Java's `javaw.exe` is signed (Eclipse Foundation) and starts normally.
- Passwords in the app are deliberately simple for a classroom demo (4–64 characters, no other rules). They are stored only as salted PBKDF2 hashes.

### Two databases, not one

| Database | Where | Used by |
|---|---|---|
| `campus_bus_demo` | private MySQL on **port 3310**, `%LOCALAPPDATA%\CampusBus` | the Windows release (`setup-windows.cmd`, `run-campus-bus.cmd`, `reset-demo.cmd`) |
| `college_bus_db` | your own MySQL, normally **port 3306** | running from source with your `db.properties` (the institution's real data, e.g. the SCMS schedule) |
| `college_bus_test` | your own MySQL | the automated tests only (reset before every test) |

The release never touches `college_bus_db`, and the tests never touch it either.

### Tested so far

The v1.0 zip was tested on **one** computer, the developer's Windows 11 Home (64-bit) with Smart App Control on, using fresh Windows profiles: download, checksum, setup, second setup (no change), launch from folders with spaces and from other working folders, student and admin sign-in, search, seat booking, seat conflict, cancel and rebook, persistence after restart, database stopped and recovered, reset, clean shutdown, and unpacking with Windows *Extract All*. **Not yet verified:** a second physical computer, Windows 10, and other antivirus / SmartScreen configurations. It is not claimed to work on every Windows PC.

The October 2026 fixes were checked on the same computer only, in sandboxes on drive D: (the real demo install was not touched): a full setup from the release zip under a user folder with an accent and a space (`José Test`), two app windows open at once (the database keeps running when the second window is closed; closing the window opened first stops it), and every booking and admin screen driven with real mouse clicks from the packaged release. **A clean installation on another PC has not been tested.**

The v1.1 zip was checked on the same computer in a sandbox on drive D: (own `LOCALAPPDATA`/`APPDATA`, port 3330; the real demo install and port 3310 were not touched): setup, setup again (no change), setup with the settings file missing while demo tables exist (refused, nothing deleted), setup after an interrupted first run (starts over), launch with `run-campus-bus.cmd`, admin sign-in, overview, a refused action followed by a second message (the newer message is shown), and closing the window (the private MySQL stops). Schedule import (Windows file picker), Windows 10 and a second PC were **not** tested.

---

## 2. Accounts

| Role | How it is created |
|---|---|
| First administrator | first-run screen **Set up your institution** (name of the institution + admin, password typed twice); the Windows demo setup creates `demo.admin` instead |
| More administrators | Admin → *Admins* → *Add admin* (only a signed-in admin; the admin sign-in side says so) |
| Students | *Create a student account* on the Student side of the login screen (password typed twice) |
| Demo accounts | `setup-windows.cmd`: random passwords in `%APPDATA%\CampusBus\demo-accounts.txt` on that computer |

Student sign-in, admin sign-in, student sign-up and admin creation are four separate paths. No real password is written in this repository.

---

## 3. Build and run from source

**You need:** JDK 17 or newer, Apache Maven 3.8+, a running MySQL 8 server. Windows, macOS or Linux, with a desktop (the app is a window, not a website).

```bash
git clone https://github.com/aspire488/campus-bus.git
cd campus-bus
```

Create a MySQL user for the app (once, as the MySQL root user; choose your own password):

```sql
CREATE USER 'campus_bus'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL ON college_bus_db.*   TO 'campus_bus'@'localhost';
GRANT ALL ON college_bus_test.* TO 'campus_bus'@'localhost';   -- only needed for mvn test
```

Copy `db.properties.example` to `db.properties` and fill in that user and password (`db.user=campus_bus`, `db.password=…`). `db.properties` is git-ignored. The app looks for it in the folder you start from, then in the project folder, then in `%APPDATA%\CampusBus` (Windows) or `~/.campus-bus` (elsewhere). Every value can also come from an environment variable (`CAMPUS_BUS_DB_HOST`, `CAMPUS_BUS_DB_USER`, `CAMPUS_BUS_DB_PASSWORD`, `CAMPUS_BUS_DB_NAME`) or a `-D` option. With no settings the app stops with a clear message; it never guesses a MySQL user. The database and its tables are created on the first launch.

Start the app from the project folder:

| Where | Command |
|---|---|
| Windows PowerShell | `.\run-campus-bus.cmd` |
| Windows Command Prompt | `run-campus-bus.cmd` |
| macOS / Linux | `sh run-campus-bus.sh` |
| Any system | `mvn clean compile exec:java` (later: `mvn exec:java`) |

`run-campus-bus.cmd` checks Java, Maven and the settings and explains any problem. In a source checkout `setup-windows.cmd` and `reset-demo.cmd` also work (they then use your Java and Maven).

On the very first launch on an empty database the app opens **Set up your institution**; after that it opens at the login screen. The admin then sets the timetable and imports the bus schedule (Admin → Routes → *Import schedule*, e.g. `database/scms-schedule-2025-08-18.txt`); students create their own accounts. `mvn clean compile exec:java -Ddemo=true` on a new empty database loads the fictional `database/demo-data.sql` with the fixed passwords the automated tests use; never load it into a real institution's database.

**Build the Windows release yourself:** `package-windows.cmd` (needs a JDK 21+ with `jlink`, e.g. Eclipse Temurin, and Maven). It runs the tests, builds the jar, makes the Java runtime with `jlink` and writes `target\dist\CAMPUS-BUS-1.1-windows-x64.zip`; `-SkipTests` skips the tests. `mvn package` alone builds a library jar plus `target/lib`; it is not a double-click jar.

---

## 4. Problem statement

An institution runs many buses for hundreds of students. Handled by hand, nobody knows which seats are free, when the bus reaches a stop, or who is on which bus. A computerised system lets each student reserve a seat on a specific bus, direction and date, and lets the transport office manage the fleet from one place.

## 5. Objectives

1. A simple system for managing bus reservations at any school, college or university.
2. Students can check bus details, timings at their own stop and reserve available seats.
3. Student, bus, route, timetable and reservation records are kept in MySQL.
4. Java OOP concepts applied in a practical application.

## 6. How the buses work (the rules the app enforces)

- **Published times are kept exactly.** Each bus has its own first pick-up time and each stop its printed time; a stop the schedule prints without a time is shown as an estimate (≈), never as a fact. The timetable never moves a printed time.
- **One timetable for the whole institution:** class start, the campus arrival window and one evening departure (SCMS: classes 08:45, buses reach campus 08:20–08:40, everyone leaves at 16:00). When the schedule prints no campus time, the window's end is shown as the **target** ("by 08:40"). Routes whose last printed pick-up is within 20 minutes of the window are flagged for the transport office to confirm.
- **One bus, both ways:** a bus drives its route to the institution in the morning and the same route reversed in the evening (or mornings only). Each direction has its own seats.
- **Every stop has a daily time** (`Stop:minutesFromStart`, or just `Stop` when none is printed). Students see the time the bus reaches *their* stop, and every booking records where the student **gets on** (morning) or **gets off** (evening); the admin passenger list shows it.
- **Every trip has the campus at one end.** *Find a bus* asks for the **Trip** (*To campus* = your stop → campus in the morning, *From campus* = campus → your stop in the evening) and **Your stop**; there is no stop-to-stop search, and the server refuses any other direction or a stop that is not on that trip's route. Stops spelled differently on different buses ("Edapally H.S" / "Edapally H S") are matched as one.
- **One seat per student each way, each day** (all morning buses arrive together, so nobody can ride two). One booking per seat per bus/date/direction. A booking locks the student and the bus while it is checked and saved, so two clicks, two students, or an admin editing the bus at the same moment cannot slip past these rules.
- **History is kept:** a bus with booked trips on record (past ones included) cannot be deleted - set it to maintenance; a route edit or a bus move cannot drop a stop that students are booked at.
- Bookings open 7 days ahead and close when the bus leaves its first stop; cancelling frees the seat immediately.

## 7. Modules (from the PPT)

| Module | What it does | Where |
|---|---|---|
| Student | Register, update and display student details | `Student`, `StudentDAO`, Login → *Create a student account*, *Profile* |
| Bus | Add / update / remove buses, routes, timetable, capacity | `Bus`, `Route`, `Timetable`, `BusDAO`, Admin → *Buses*, *Routes*, *Schedules* |
| Reservation | Book, view, cancel seats | `Reservation`, `ReservationService`, `ReservationDAO`, *Find a bus*, *My reservations* |
| Availability | Free seats per bus, direction and date | `Bus.checkAvailability()`, `ReservationDAO.bookedSeats()`, seat map |
| Admin | Manage buses, schedules, students, reservations, admins | `Admin`, `AdminDAO`, `AdminDashboard` |

## 8. Features

**Student**
- Create an account (password typed twice; admission number such as `SCS/10001/26`; class such as `S3 CS2`) and sign in with email or admission number
- Home: next bus with **Where's my bus** (position on the route line, last/next stop, minutes to arrival — estimated from the timetable and refreshed every 30 s; buses carry no GPS)
- Find a bus: **Trip** (*To campus* or *From campus*) and **Your stop** (⇄ switches to the trip back), date (today + 7 days), leaving after, bus number, sort; each result shows the time at *your* stop. There is no stop-to-stop search: every trip has the campus at one end
- Choose **your stop** (getting on / getting off) when booking
- Seat map drawn as the bus seen from above (driver, door, aisle): available green, booked red, selected, unavailable, yours — with vacant / booked counters
- Bus pass after booking (seat, bus, your stop and its time, route with every stop, reference), printable through the system print dialog, saved as a PDF with **Save pass (PDF)**, and emailed on booking when mail is set up
- My reservations: Upcoming / On the road / Completed / Cancelled with inline cancel confirmation
- Profile editing

**Admin (transport office)**
- Overview: buses running, seats booked / open today, occupancy, cancellations, today's departure board, a 14-day bookings chart — all read from MySQL
- Buses: add, edit (route, evening trip yes/no, capacity, status), remove (refused while students hold upcoming seats)
- Routes: add, edit (checked against every bus on it before saving), remove (refused while buses use the route), **Import schedule** from a text file (idempotent, verified by reading back)
- Schedules: every departure both ways; **Timetable** (class start, campus arrival window, evening departure; printed pick-up times never move); **Stop times** for any trip (printed / ≈ estimated / campus target); **Seat map** with the passenger list and each passenger's stop
- Students: search, filter by department (worked out from the class: S3 CS2 → CS; semesters S1–S8), edit, remove (confirmation shows how many upcoming seats are released; the student can no longer sign in)
- Reservations: loaded one period at a time (today + 7 days, today, tomorrow, last 7 / 30 days); search, filter by department and status, cancel
- Administrators: add another admin, remove one (never yourself, never the last one)

**Everywhere:** toast notifications, inline form errors, empty states, "database unavailable" states that recover when MySQL returns, loading state, restrained motion.

The pass's bar pattern is **decorative** (generated from the reservation code); it is not a scannable code.

---

### Booking confirmation email (with the PDF pass)

After a booking is **saved**, the app sends a confirmation email on a background thread: a branded HTML message with a plain-text version, the travel pass attached as a PDF. The PDF pass is a picture of the very pass the app shows on *Your pass* (drawn off-screen from the same data), with the key details as text under it; the same booking always gives the identical PDF, whether emailed or saved with **Save pass (PDF)**. The email is not needed for the booking: if mail is not set up, down or refuses, the booking stays confirmed, the pass page shows *Confirmation email: sent / sending… / not sent / not set up*, offers **Send again** when it was not sent (an email already sent is never sent twice; not for trips that have left), and **Save pass (PDF)** always works offline. Nothing is sent for a booking that was cancelled meanwhile. Settings go in `db.properties` (or `CAMPUS_BUS_MAIL_*` environment variables); see `db.properties.example`. Never put them in the repository. `-Dcampus.bus.mail.off=true` switches email off whatever the settings say; every test run uses it, so tests never send real mail.

**Gmail.** Use a Gmail account with 2-Step Verification on, create an **App Password** (Google Account → Security → 2-Step Verification → App passwords) and set `mail.user=<that Gmail address>` and `mail.password=<the 16-letter app password>`. The app connects to `smtp.gmail.com:587` with STARTTLS (override with `mail.host` / `mail.port` for another provider). A normal Gmail password does not work and must not be used. Personal Gmail accounts are limited to about 500 messages a day. Confirmations go to the address stored for the student, exactly as stored (any provider); an admin can correct a placeholder address in **Admin → Students → Edit** without touching the student's id, password or bookings.

## 9. Architecture

```
                    JAVA DESKTOP APP  (Main → first-run setup → LoginFrame)
                              │
          ┌───────────────────┴────────────────────┐
     JAVA SWING                              HTML / CSS / JS  (JavaFX WebView)
     LoginFrame, setup + forms (FormDialog), Home, Find a bus, Seat map,
     Dashboard route-line menu,              Bus pass, My reservations,
     Admin tables (TablePanel), Profile,     Admin overview + seat map / passengers
     Toasts, JOptionPane                           │  window.app = WebBridge
          │                     WebPanel = JFXPanel + WebView (inside Swing)
          └───────────────────┬────────────────────┘
       CollegeBusSystem · ReservationService · ScheduleImport · Admin   ← business rules (Java)
                              │
              StudentDAO · AdminDAO · BusDAO · ReservationDAO    ← SQL (PreparedStatement only)
                              │
                 JDBC (Connection, PreparedStatement, ResultSet)
                              │
      MySQL: college_bus_db (source / institution) · campus_bus_demo (Windows release)
```

**How HTML/CSS/JS is integrated:** `WebPanel` is a Swing component (`JFXPanel`) hosting a WebView. It loads `resources/web/index.html` and gives JavaScript a Java object (`WebBridge`) as `window.app`. JavaScript calls e.g. `app.book("3", "2026-10-09", "OUT", "12", "Kaloor")`; Java checks the rules, talks to MySQL and returns JSON (`{"ok":true,"data":…}`). JavaScript only draws (SVG route lines, seat grid, pass). The Swing menu tells the web view which page to show; the web page tells Swing which menu item to highlight. Bridge calls check the signed-in role, so a student window cannot run admin calls and never receives other students' names.

### Project structure

```
campus-bus/
├── setup-windows.cmd · run-campus-bus.cmd · reset-demo.cmd   Windows: setup, launch, demo reset
├── run-campus-bus.sh            macOS / Linux launcher (from source)
├── package-windows.cmd          builds the Windows release zip
├── scripts/campus-bus.ps1       the logic behind the three Windows scripts
├── packaging/                   package-windows.ps1 (jlink + zip), README-WINDOWS.txt (inside the zip)
├── pom.xml                      Maven build (exec:java launches the app; package copies libraries to target/lib)
├── db.properties.example        copy to db.properties (git-ignored)
├── database/
│   ├── schema.sql               tables and constraints
│   ├── demo-data.sql            fictional demo data (routes, buses, students, reservations)
│   └── scms-schedule-2025-08-18.txt   the SCMS bus schedule, transcribed for Import schedule
├── docs/                        screenshots, release audit, control checklist, SCMS import report
└── src/
    ├── main/java/com/collegebus/
    │   ├── Main.java            starts the app (or --setup-demo for the Windows setup)
    │   ├── model/    Displayable, User, Student, Admin, Bus, Route, Timetable, Reservation
    │   ├── dao/      StudentDAO, AdminDAO, BusDAO, ReservationDAO, Queries
    │   ├── service/  CollegeBusSystem, ReservationService, ScheduleImport, DemoSetup,
    │   │             Notifier, SmtpMail (confirmation email), PassPdf, PassImage (the PDF pass)
    │   ├── gui/      LoginFrame, Dashboard, StudentDashboard, AdminDashboard,
    │   │             WebPanel, WebBridge, TablePanel, FormDialog, Toast, Theme
    │   └── util/     DatabaseConnection, Json
    ├── main/resources/web/      index.html · css/style.css · js/app.js, student.js, admin.js · assets/fonts
    └── test/java/com/collegebus/
        ├── ModelTest.java       validation and rules, no database
        ├── DatabaseTest.java    real JDBC/MySQL tests on college_bus_test
        ├── RegressionTest.java  one test per fixed defect (October 2026 audit and reviews)
        ├── TestDb.java          prepares college_bus_test for the database test classes
        ├── gui/WebBridgeTest.java       the page bridge, as the screens call it
        └── service/NotificationTest.java  confirmation email and PDF pass (fake mail provider)
```

---

## 10. OOP concepts — where to point in the viva

| Concept | Where | What to say |
|---|---|---|
| **Encapsulation** | `User`, `Student`, `Bus`, `Timetable`, `Reservation` | Fields are `private`; changes go through methods that validate first — `Student.updateStudent()` rejects a 9-digit phone, `Bus.updateBus()` rejects an evening trip that starts before the bus arrives, `Timetable` refuses an arrival window that ends after classes start. |
| **Inheritance** | `User` → `Student`, `User` → `Admin`; `Dashboard` → `StudentDashboard`, `AdminDashboard` | Common fields (id, name, email, password hash) and `checkPassword()` live once in `User`; the menu, page switching and toasts live once in `Dashboard`. |
| **Polymorphism** | `User.getRole()`, `User.openDashboard()`, `displayDetails()`, `Dashboard.open()`, `Bus.getRoute()` | `LoginFrame.signIn()` gets a `User` and calls `user.openDashboard(system)` — a Student opens the student window, an Admin the console, no `if/else` on type. `Json.of()` turns *any* `Displayable` into JSON through `toMap()`. A bus and its `returnTrip()` answer `getRoute()` / `getDeparture()` differently, so search, seat map and pass work for both directions with one code path. |
| **Abstraction** | `abstract class User`, `abstract class Dashboard`, `interface Displayable` | They define *what* subclasses must do (`getRole()`, `openDashboard()`, `open(page)`, `toMap()`), not *how*. |

### PPT methods → code

| PPT | Implemented in |
|---|---|
| `Student.registerStudent() / updateStudent() / displayStudent()` | `model/Student.java` |
| `Bus.addBus() / updateBus() / displayBus() / checkAvailability()` | `model/Bus.java` |
| `Reservation.bookSeat() / cancelBooking() / displayBooking()` | `model/Reservation.java` |
| `Admin.addBus() / removeBus() / manageSchedule() / viewReservations()` | `model/Admin.java` (`manageSchedule` sets the institution timetable) |
| `CollegeBusSystem.searchBus() / manageReservations() / displayDetails()` | `service/CollegeBusSystem.java` |

---

## 11. Database design

Tables `students`, `admins`, `settings`, `routes`, `buses`, `reservations` (the same schema in `college_bus_db`, `campus_bus_demo` and `college_bus_test`).

- Primary keys everywhere; foreign keys `buses.route_id → routes`, `reservations.student_id → students` and `reservations.bus_id → buses` (both `ON DELETE CASCADE`: removing a student frees their seats)
- `CHECK`: capacity 8–60, arrival after departure, evening trip after arrival, seat number > 0
- `UNIQUE` on admission number, email, bus number, admin username
- `settings` holds the institution name and timetable; `reservations.email_status` records the confirmation email (SENT / PENDING / FAILED / NOT_CONFIGURED, NULL = never tried), kept apart from the booking itself
- A bus with booked trips on record (past ones included) cannot be deleted - set it to maintenance instead, so the travel history stays; removing a **student** still removes their bookings (as before)
- On start-up the app adds what an older database lacks (it never drops anything); it waits at most 60 s for a table another session is using, and needs the `ALTER` privilege (the documented `GRANT ALL` includes it). Row locks are waited for at most 10 s, then the user sees *busy, try again*
- Passwords stored as salted PBKDF2-HMAC-SHA256 (600,000 iterations, `User.hash()`), never as text; older SHA-256 or cheaper hashes are upgraded at the next sign-in; the database password is never in the source
- **Double-booking is blocked by MySQL itself:** `reservations.active` is a generated column (`1` while BOOKED, `NULL` when CANCELLED). `UNIQUE (bus_id, travel_date, direction, seat_no, active)` allows one *booked* row per seat, while cancelled rows (NULL) never clash, so a cancelled seat can be booked again. `UNIQUE (student_id, travel_date, direction, active)` stops one student holding two seats in the same direction on the same day.
- Built for real volume: counts are done with `COUNT(*)` in MySQL and the admin reservation list loads one period at a time.
- First-admin setup is one atomic statement (`INSERT … SELECT … WHERE NOT EXISTS (SELECT 1 FROM admins)`), so it can never create a second admin.

---

## 12. Viva answers

**How does Java talk to MySQL?**
`DatabaseConnection.getConnection()` → `DriverManager.getConnection(url, user, password)`. DAOs create a `PreparedStatement` with `?` placeholders, set values, run `executeQuery()` → `ResultSet` or `executeUpdate()`, all in try-with-resources so connections close automatically. Prepared statements also prevent SQL injection.

**How does a seat reservation happen?**
```
Seat map (JS) → app.book(bus, date, direction, seat, stop) → WebBridge.book() → ReservationService.bookSeat()
   1. date not in the past                     4. seat not already taken           (DAO queries)
   2. trip exists (return trip only if         5. Reservation.bookSeat(): seat ≤ capacity,
      the bus runs back), not full                bus active, not departed, ≤ 7 days ahead
   3. no other seat that day in that direction
→ ReservationDAO.insert() → INSERT … (PreparedStatement) → MySQL UNIQUE keys = final guard
→ JSON back to JS → bus pass page; Swing toast "Reservation confirmed"
```

**How are bus times decided?**
`Timetable` (class start, campus arrival window, evening time). Pick-up times are the bus's own, as printed, and never change. A route that prints its campus time arrives then; otherwise the window's end is the campus **target**. The evening trip leaves at the one evening time, with every stop on the way back estimated. `Admin.manageSchedule(timetable)` applies it to every bus in one transaction (`BusDAO.retime`).

**Where's my bus?** `Bus.estimatePosition()` compares the clock with the timetable and the stop minutes. It is an estimate — there is no GPS on the buses; a real tracker would replace that one method.

**What happens on cancel?** `ReservationService.cancelBooking()` checks the reservation belongs to the student, `Reservation.cancelBooking()` refuses if already cancelled or the bus has left, then `UPDATE … SET status = 'CANCELLED' WHERE … AND status = 'BOOKED'` (two clicks at once cannot both succeed). The seat is free immediately.

**Why HTML/CSS/JS inside Swing?** Visual parts (seat map, route lines, pass, charts) are simpler in HTML/SVG/CSS; data-heavy admin screens (tables + forms) suit Swing. Both use the same Java business logic.

**How does the Windows release run without Java installed?** `package-windows.cmd` uses `jlink` to build a small Java runtime containing only the modules the app needs, and puts it next to the app's jars. `run-campus-bus.cmd` starts the app with that runtime's `javaw.exe`.

---

## 13. Presentation workflow

**Student:** sign in as `student@college.example.com` (password from `demo-accounts.txt`) → Home (next bus, Where's my bus) → *Find a bus* → Trip *To campus*, Your stop *Edappally*, *Tomorrow* (the time shown is when the bus reaches Edappally; ⇄ shows the trip back) → *View seats* → green seat → *Confirm seat* → pass with "Confirmed" stamp → *My reservations* → *Cancel* → the seat is green again. Try ⇄ to search the trip home. Sign in as `ananya@college.example.com` and try the same seat: it is refused.

**Admin:** sign in as `demo.admin` → Overview → *Buses* → *Add bus* → *Edit* (evening trip: No) → *Schedules* → *Timetable* (change class start: every bus re-times) → *Stop times* → *Seat map* (passenger list) → *Students* → *Remove* (see the confirmation) → *Reservations* (choose a period, filter by department) → *Admins* → *Add admin*.

## 14. Tests

```bash
mvn clean test
```

`ModelTest` (87 tests, no database) checks validation and rules: registration, bus/route/timetable rules, return trips, the position estimate, JSON escaping, the settings lookup, the root-without-password guard and the demo-setup guard. `DatabaseTest` (37 tests) runs the real JDBC code against a separate database, `college_bus_test`, reset from `schema.sql` before each test: login, setup-only-once, admin accounts, search both ways, booking rules, seat races (concurrent bookings and cancellations), timetable re-timing, student removal, overview numbers, and that the demo setup refuses any database but `campus_bus_demo`. Without a reachable MySQL or database settings, every test that needs the database (DatabaseTest, RegressionTest, WebBridgeTest and most of NotificationTest) is reported as **skipped**, not failed; any other start-up error makes them fail.

`RegressionTest` (26 tests) has one test per defect fixed in the October 2026 audit (direction search, stop spellings, direction/stop checks on the server, the database-wipe guard, four booking/admin races, bus-delete history, route edits, error messages, damaged password hashes, email correction) - those 14 each fail on the v1.0 code - plus 5 for problems the independent review found in the first version of the fixes (install folders with accents, a half-finished first run, a route edit racing a booking, spelling-only route edits, a slow database upgrade mistaken for an outage), each shown to fail without its fix, plus 7 from the final multi-agent review (legacy bookings blocking bus edits, a stale route copy, a locked bus reported as an outage, damaged password hashes, names in Indian scripts, duplicate stop spellings, boarding stops hidden from the picker). `WebBridgeTest` (2 tests) calls the page bridge the way the screens do: a booking that is saved is always reported as booked, and a programming error is refused and logged. `NotificationTest` covers the confirmation email and the PDF pass with a fake mail provider - nothing real is ever sent - including that the emailed pass is byte-identical to the saved one, stays on one page with long names, and that names cannot inject markup into the email. All database test classes use only `college_bus_test` (a database whose name does not end in `_test` can never be reset).

| Run | Result |
|---|---|
| v1.1 release build (`package-windows.ps1`, isolated MySQL 8.4 on port 3320, email switched off) | 166 tests (87 + 37 + 26 + 2 + 14), 0 failures, 0 skipped |
| October 2026 audit fixes after review, MySQL 8.4 (isolated server, port 3320) | 166 tests (87 + 37 + 26 + 2 + 14), 0 failures, 0 skipped |
| Source with MySQL, commit `c2b9772` (the code in v1.0; later commits changed only the zip packaging script) | 124 tests, 0 failures, 0 skipped (as reported at the time) |
| Clean anonymous clone of `c83de68` without database settings, during the release | 124 run, 0 failures, 37 skipped |
| v1.0 README update (commit 241d9a1), MySQL stopped | 124 run, 0 failures, 37 skipped (re-run to check the build; the database tests were not exercised) |

## 15. Troubleshooting (source)

| Problem | Fix |
|---|---|
| *Can't reach MySQL* | Start the MySQL server, then check `db.host` in `db.properties`. |
| *MySQL refused access* | Wrong `db.user` / `db.password`, or that user has no rights on `db.name`. |
| *Refusing to log in as MySQL root without a password* | Use a dedicated MySQL user with a password (section 3). |
| *No database settings* | Create `db.properties` from `db.properties.example` in the project folder. |
| Setup screen does not appear | An administrator already exists in this database; sign in as admin. |
| *CAMPUS BUS - database not changed* | The database holds data but has no `reservations` table. Restore it from a backup, or set `db.name` to a new, empty database. |
| *CAMPUS BUS - database update failed* | Another window or database tool is using the tables (the update waits at most 60 s and removes nothing), or the MySQL user lacks the `ALTER` privilege. Close the other program and start the app again. |
| *The system is busy right now* | Another booking or an admin edit held the same bus for over 10 s. Nothing was saved; try again. |
| `mvn` not recognised | Install Maven and add its `bin` folder to PATH, or run `Main` from your IDE. |
| `cd /d` fails in PowerShell | That is Command Prompt syntax; in PowerShell use `Set-Location D:\campus-bus`. |
| Window too big | The window sizes itself to your screen; it needs at least 1024 × 640. |

Fonts: Overpass and Overpass Mono, SIL Open Font License (`src/main/resources/web/assets/fonts/OFL.txt`).

## 16. The SCMS schedule

`database/scms-schedule-2025-08-18.txt` is a hand transcription of *College Bus Schedule w.e.f 18.08.2025* (SCMS School of Engineering and Technology, Karukutty): 21 buses, 259 boarding points (86 printed without a time). Its header lists every transcription decision and the 5 ambiguous values to confirm with the transport office; driver names and phone numbers are deliberately not included. `docs/scms-import-report.md` compares what the database holds with the file, bus by bus. The Windows demo uses fictional routes instead; an admin can import this file there too (Admin → Routes → *Import schedule*).

## 17. Using it on several computers

| Question | Answer |
|---|---|
| Can another computer get the source? | Yes: the repository is public, `git clone` needs no account. |
| Can it run the app? | Yes: on Windows with the release zip (section 1), elsewhere from source with its own MySQL (section 3). Each computer then has its **own, separate data**. |
| Do two computers share the same buses and bookings? | Only if both `db.properties` point to the **same MySQL server**. That server must be reachable on the network and secured: a dedicated MySQL user per installation with only the needed rights, TLS, and a firewall that admits only the college network or a VPN. Never expose MySQL to the open internet. The Windows release deliberately accepts local connections only. For many users across the internet, a small server API in front of the database is the safer design. |

## 18. Known limitations

- The Windows release was tested on one Windows 11 computer only (section 1, *Tested so far*); Windows 10, a second physical PC and other antivirus / SmartScreen setups are unverified.
- The release is not code-signed; SmartScreen may ask for confirmation on the `.cmd` files.
- First setup needs internet (or the MySQL zip copied in) and about 1 GB of disk.
- `run-campus-bus.cmd` keeps a console window open while the app runs. The private database belongs to the **first** window you opened: closing that window stops the database, also for any second CAMPUS BUS window still open, so close the first window last. If a console window is closed while its app is still open, the private MySQL keeps running (on 127.0.0.1 only, port 3310, reachable from this computer only) until you log off or restart Windows.
- Windows user names with letters outside the computer's language code page (for example Malayalam letters on an English Windows): MySQL can only use such a folder through its short (8.3) name. Windows creates those on the system drive by default, where user folders normally are; if they are switched off, `setup-windows.cmd` stops with a "MySQL could not initialise" or "did not start" message, after MySQL has been downloaded and unpacked; no existing data is touched. Accented Latin names (é, ü, ñ ...) work either way.
- Demo passwords are random per computer, but the automated tests use fixed passwords for their fixture data (`database/demo-data.sql`); those are only loaded by the tests or by `-Ddemo=true`, and only into `campus_bus_demo` or a `*_test` database (other names: ignored with a console message).
- The app is a desktop program; it is not a web service, and buses have no GPS (positions are estimates).
- The PDF pass is a picture of the in-app pass, so names in any script show as they do in the app; only the line of selectable text under the picture uses a Latin font and shows other scripts as `?`. Drawing a pass takes about a second, needs a desktop session, and briefly opens a hidden window off-screen.
- Email delivery is only as good as the provider's answer: "sent" means Gmail accepted the message, not that it reached the inbox (it may land in spam).
- Keyboard-only use is incomplete: the Student/Admin switch, the left menu and *Log out* need the mouse.
- *Save pass (PDF)* opens the system save dialog without an owner window, so it can appear behind the app; drawing a pass briefly opens a hidden off-screen window, which may take keyboard focus for a moment.
- Saving a route re-times its buses in a second step; if that step fails, the route is saved and the buses keep their old times until the next timetable save.
- Database calls still run on the screen's thread; a slow database makes a window pause (now for at most about 15 seconds instead of indefinitely).
