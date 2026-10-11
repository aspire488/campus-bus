-- Fictional demo data for CAMPUS BUS (routes, buses, 150 students, reservations). Load it only for a demo or the tests:
--   mvn clean compile exec:java -Ddemo=true     (on a new, empty database)
-- It contains public demo passwords - never load it into a real institution's database.

-- ---------------- demo data (fictional) ----------------
-- No admin is shipped: on first launch the app asks the institution to create its own administrator.

INSERT INTO students (reg_no, name, email, password, department, phone) VALUES
('CBR24CS001', 'Joel Mathew',      'student@college.com',  SHA2('student123', 256), 'S3 CS1', '9000000201'),
('CBR24CS014', 'Ananya Menon',     'ananya@college.com',   SHA2('pass123', 256),    'S3 CS2', '9000000202'),
('CBR24EC007', 'Rahul Nair',       'rahul@college.com',    SHA2('pass123', 256),    'S3 EC1', '9000000206'),
('CBR24ME021', 'Fathima Rasheed',  'fathima@college.com',  SHA2('pass123', 256),    'S3 ME1', '9000000207'),
('CBR24CE003', 'Arjun Krishnan',   'arjun@college.com',    SHA2('pass123', 256),    'S3 CE1', '9000000203'),
('CBR24EE011', 'Sneha Thomas',     'sneha@college.com',    SHA2('pass123', 256),    'S3 CT1', '9000000204'),
('CBR24CS027', 'Aditya Varma',     'aditya@college.com',   SHA2('pass123', 256),    'S5 CS1', '9000000208'),
('CBR24EC019', 'Meera Joseph',     'meera@college.com',    SHA2('pass123', 256),    'S5 EC2', '9000000209'),
('CBR24ME008', 'Nikhil George',    'nikhil@college.com',   SHA2('pass123', 256),    'S5 AU1', '9000000210'),
('CBR24CS040', 'Diya Pillai',      'diya@college.com',     SHA2('pass123', 256),    'S1 CO1', '9000000205');

-- each route is stored once, from the first pick-up stop to the institution; buses also drive it in reverse
INSERT INTO routes (source, destination, stops, distance_km) VALUES
('Vyttila',  'College',  'Vyttila:0,Palarivattom:12,Edappally:22,Kalamassery:34,College:45', 21.5),
('Aluva',    'College',  'Aluva:0,Kalamassery:14,HMT Junction:25,College:40', 17.0),
('Kakkanad', 'College',  'Kakkanad:0,Thrikkakara:10,Pathadipalam:22,College:35', 14.2),
('Tripunithura', 'College', 'Tripunithura:0,Vyttila:14,Kaloor:30,Edappally:40,College:55', 26.8),
('Angamaly', 'College',  'Angamaly:0,Athani:15,Aluva:30,Kalamassery:45,College:60', 31.0);

-- timetable (Admin > Schedules > Timetable): classes 09:00, campus arrival window 08:30-08:45, all buses leave at 16:30.
-- Pick-up times are each bus's own; these demo routes print a campus time, so they arrive at 08:45.
INSERT INTO settings (name, value) VALUES ('class_start', '09:00'), ('arrive_from', '08:30'), ('arrive_by', '08:45'), ('evening_departure', '16:30');

INSERT INTO buses (bus_no, route_id, departure, arrival, return_departure, capacity, status) VALUES
('CB-01', 1, '08:00', '08:45', '16:30', 40, 'ACTIVE'),
('CB-02', 2, '08:05', '08:45', '16:30', 40, 'ACTIVE'),
('CB-03', 3, '08:10', '08:45', '16:30', 32, 'ACTIVE'),
('CB-04', 4, '07:50', '08:45', '16:30', 48, 'ACTIVE'),
('CB-05', 5, '07:45', '08:45', '16:30', 40, 'ACTIVE'),
('CB-06', 1, '08:00', '08:45', '16:30', 32, 'ACTIVE'),
('CB-07', 4, '07:50', '08:45', '16:30', 40, 'ACTIVE'),
('CB-08', 5, '07:45', '08:45', NULL,    40, 'ACTIVE'),
('CB-09', 3, '08:10', '08:45', '16:30', 32, 'MAINTENANCE');

INSERT INTO reservations (student_id, bus_id, travel_date, direction, seat_no, status) VALUES
(1, 1, CURDATE() - INTERVAL 3 DAY, 'OUT',    7,  'BOOKED'),
(1, 1, CURDATE() - INTERVAL 3 DAY, 'RETURN', 12, 'BOOKED'),
(1, 2, CURDATE() - INTERVAL 1 DAY, 'OUT',    5,  'CANCELLED'),
(1, 1, CURDATE() + INTERVAL 1 DAY, 'RETURN', 9,  'BOOKED'),
(2, 1, CURDATE(), 'OUT', 3,  'BOOKED'),  (3, 1, CURDATE(), 'OUT', 4,  'BOOKED'),
(4, 1, CURDATE(), 'OUT', 10, 'BOOKED'),  (5, 1, CURDATE(), 'OUT', 11, 'BOOKED'),
(6, 2, CURDATE(), 'OUT', 1,  'BOOKED'),  (7, 2, CURDATE(), 'OUT', 2,  'BOOKED'),
(8, 3, CURDATE(), 'OUT', 6,  'BOOKED'),  (9, 4, CURDATE(), 'OUT', 14, 'BOOKED'),
(10, 1, CURDATE(), 'RETURN', 8, 'BOOKED'),  (2, 1, CURDATE(), 'RETURN', 15, 'BOOKED'),
(3, 1, CURDATE(), 'RETURN', 16, 'BOOKED'),  (4, 2, CURDATE(), 'RETURN', 1,  'BOOKED'),
(5, 6, CURDATE(), 'OUT', 20, 'CANCELLED'),
(2, 1, CURDATE() + INTERVAL 1 DAY, 'OUT', 3,  'BOOKED'), (3, 1, CURDATE() + INTERVAL 1 DAY, 'OUT', 4,  'BOOKED'),
(6, 1, CURDATE() + INTERVAL 1 DAY, 'OUT', 8,  'BOOKED'), (7, 1, CURDATE() + INTERVAL 1 DAY, 'OUT', 21, 'BOOKED'),
(8, 2, CURDATE() + INTERVAL 1 DAY, 'OUT', 12, 'BOOKED'), (9, 3, CURDATE() + INTERVAL 1 DAY, 'OUT', 2,  'BOOKED'),
(10, 1, CURDATE() + INTERVAL 1 DAY, 'RETURN', 18, 'BOOKED'), (4, 1, CURDATE() + INTERVAL 1 DAY, 'RETURN', 19, 'BOOKED'),
(5, 2, CURDATE() - INTERVAL 1 DAY, 'OUT', 9,  'BOOKED'), (6, 3, CURDATE() - INTERVAL 2 DAY, 'OUT', 6,  'BOOKED'),
(7, 4, CURDATE() - INTERVAL 2 DAY, 'OUT', 30, 'BOOKED'), (8, 5, CURDATE() - INTERVAL 4 DAY, 'OUT', 22, 'BOOKED'),
(9, 1, CURDATE() - INTERVAL 5 DAY, 'OUT', 13, 'BOOKED'), (10, 2, CURDATE() - INTERVAL 6 DAY, 'OUT', 17, 'CANCELLED');

-- ---------------- realistic volume (fictional) ----------------
-- Enough riders to be realistic but light enough for a demo laptop: 140 more students (150 in all), 4 more buses,
-- and about 70% of them booked both ways for the last few days, today and tomorrow (~780 reservations).
INSERT INTO students (reg_no, name, email, password, department, phone)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 140)
SELECT CONCAT('CBR24G', LPAD(n, 3, '0')),
       CONCAT(ELT(1 + n % 20, 'Aarav', 'Ananya', 'Mohammed', 'Fatima', 'Liam', 'Sofia', 'Chen', 'Mei', 'Diego', 'Lucia',
                  'Kwame', 'Amara', 'Arjun', 'Priya', 'Noah', 'Emma', 'Yusuf', 'Aisha', 'Kenji', 'Hana'), ' ',
              ELT(1 + (n DIV 20) % 20, 'Nair', 'Menon', 'Thomas', 'Joseph', 'Khan', 'Silva', 'Wang', 'Okafor', 'Garcia', 'Smith',
                  'Patel', 'Kumar', 'Rahman', 'George', 'Mathew', 'Ito', 'Rossi', 'Mensah', 'Varma', 'Pillai')),
       CONCAT('student', n, '@college.com'), SHA2('pass123', 256),
       CONCAT('S', ELT(1 + n % 4, '1', '3', '5', '7'), ' ', ELT(1 + (n DIV 4) % 8, 'CS', 'CT', 'CO', 'EC', 'ME', 'CE', 'AU', 'VL'), 1 + n % 2),
       CONCAT('9', LPAD(n, 9, '0'))
FROM seq;

INSERT INTO buses (bus_no, route_id, departure, arrival, return_departure, capacity, status) VALUES
('CB-10', 1, '08:00', '08:45', '16:30', 48, 'ACTIVE'), ('CB-11', 2, '08:05', '08:45', '16:30', 48, 'ACTIVE'),
('CB-12', 3, '08:10', '08:45', '16:30', 48, 'ACTIVE'), ('CB-13', 4, '07:50', '08:45', '16:30', 48, 'ACTIVE');

INSERT INTO reservations (student_id, bus_id, travel_date, direction, seat_no, status)
WITH riders AS (
       SELECT student_id, ROW_NUMBER() OVER (ORDER BY student_id) - 1 AS r
       FROM students WHERE reg_no LIKE 'CBR24G%' AND student_id % 10 < 7),
     trips (d, dir) AS (SELECT 1, 'OUT' UNION ALL SELECT 1, 'RETURN' UNION ALL SELECT 0, 'OUT' UNION ALL SELECT 0, 'RETURN'
       UNION ALL SELECT -1, 'OUT' UNION ALL SELECT -1, 'RETURN' UNION ALL SELECT -2, 'OUT' UNION ALL SELECT -3, 'OUT')
SELECT x.student_id, (SELECT bus_id FROM buses WHERE bus_no = CONCAT('CB-', 10 + x.r % 4)), CURDATE() + INTERVAL t.d DAY, t.dir,
       x.r DIV 4 + 1, IF(x.r % 25 = 0 AND t.d = 1 AND t.dir = 'RETURN', 'CANCELLED', 'BOOKED')
FROM riders x CROSS JOIN trips t;
