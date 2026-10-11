-- College Bus Reservation System - MySQL schema (tables only; demo rows are in demo-data.sql)
-- Run:  mysql -u root -p < database/schema.sql   (the app also runs this automatically on first launch)

CREATE DATABASE IF NOT EXISTS college_bus_db;
USE college_bus_db;

DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS buses;
DROP TABLE IF EXISTS routes;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS admins;
DROP TABLE IF EXISTS settings;

CREATE TABLE students (
  student_id  INT AUTO_INCREMENT PRIMARY KEY,
  reg_no      VARCHAR(20)  NOT NULL UNIQUE,
  name        VARCHAR(80)  NOT NULL,
  email       VARCHAR(100) NOT NULL UNIQUE,
  password    VARCHAR(255) NOT NULL,                -- PBKDF2 (pbkdf2$iterations$salt$hash); older rows: SHA-256 hex, upgraded at next sign-in
  department  VARCHAR(40)  NOT NULL,
  phone       VARCHAR(15),
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admins (
  admin_id  INT AUTO_INCREMENT PRIMARY KEY,
  username  VARCHAR(40) NOT NULL UNIQUE,
  name      VARCHAR(80) NOT NULL,
  password  VARCHAR(255) NOT NULL
);

-- one row per setting; holds the institution name chosen at first-run setup
CREATE TABLE settings (
  name   VARCHAR(40)  PRIMARY KEY,
  value  VARCHAR(120) NOT NULL
);

CREATE TABLE routes (
  route_id     INT AUTO_INCREMENT PRIMARY KEY,
  source       VARCHAR(40)  NOT NULL,
  destination  VARCHAR(40)  NOT NULL,
  stops        VARCHAR(1500) NOT NULL,            -- "Stop:minutesFromStart,Stop,..." - a stop without minutes has no published time
  distance_km  DECIMAL(5,1) NULL CHECK (distance_km > 0)   -- NULL = not published
);

CREATE TABLE buses (
  bus_id     INT AUTO_INCREMENT PRIMARY KEY,
  bus_no     VARCHAR(10) NOT NULL UNIQUE,
  route_id   INT NOT NULL,
  departure  TIME NOT NULL,                       -- towards the institution
  arrival    TIME NOT NULL,
  return_departure TIME NULL,                     -- same bus back along the reversed route; NULL = one way only
  capacity   INT  NOT NULL CHECK (capacity BETWEEN 8 AND 60),
  status     ENUM('ACTIVE','MAINTENANCE') NOT NULL DEFAULT 'ACTIVE',
  CHECK (arrival > departure),
  CHECK (return_departure IS NULL OR return_departure > arrival),
  FOREIGN KEY (route_id) REFERENCES routes(route_id),
  INDEX idx_bus_departure (departure)
);

CREATE TABLE reservations (
  res_id       INT AUTO_INCREMENT PRIMARY KEY,
  student_id   INT  NOT NULL,
  bus_id       INT  NOT NULL,
  travel_date  DATE NOT NULL,
  direction    ENUM('OUT','RETURN') NOT NULL DEFAULT 'OUT',   -- OUT = towards the institution, RETURN = back
  seat_no      INT  NOT NULL CHECK (seat_no > 0),
  stop         VARCHAR(40) NULL,                  -- where the student gets on (morning) or off (evening); NULL on older bookings
  status       ENUM('BOOKED','CANCELLED') NOT NULL DEFAULT 'BOOKED',
  booked_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  -- 1 while BOOKED, NULL when CANCELLED: NULLs never clash in a UNIQUE key,
  -- so the database itself blocks double-booking but allows re-booking a cancelled seat.
  active       TINYINT GENERATED ALWAYS AS (IF(status = 'BOOKED', 1, NULL)) STORED,
  UNIQUE KEY uq_seat_per_trip    (bus_id, travel_date, direction, seat_no, active),
  UNIQUE KEY uq_student_per_trip (student_id, travel_date, direction, active),   -- one bus each way per day: they all run at the same time
  INDEX idx_res_date (travel_date),
  FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE,
  FOREIGN KEY (bus_id)     REFERENCES buses(bus_id)        ON DELETE CASCADE
) AUTO_INCREMENT = 1040;
