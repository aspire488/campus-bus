# SCMS bus schedule — import validation report

Generated 2026-10-11 from the **real database `college_bus_db`**, compared stop by stop with `database/scms-schedule-2025-08-18.txt`
(the hand transcription of *College Bus Schedule w.e.f 18.08.2025.pdf*, SCMS School of Engineering and Technology, Karukutty).

- Buses in the PDF: **21** · imported: **21** · stored exactly as transcribed: **21** · mismatches: **0**
- Boarding points: **259** · with a printed time: **173** · without a printed time: **86** (shown in the app as ≈ estimates)
- Campus arrival: not printed in the PDF. Shown as the institution's target window **08:20–08:40** (classes 08:45), never as a printed time.
- Evening trips: not in the PDF. All buses leave campus at **16:00** (institution setting); every evening stop time is an estimate.
- Seats: not in the PDF. **49** per bus (institution setting; editable per bus).
- Driver names and phone numbers in the PDF were deliberately not imported.
- Transcription decisions and the 5 ambiguous values are listed at the top of the schedule file (notes 1–11), including the renaming of "Trichur" to "Thrissur" on buses 15 and 16 at the institution's request.

**Routes to confirm with the transport office** (last printed pick-up less than 20 minutes before the arrival window opens): 10, 24, 22, 26, 28.

| Bus | First stop | Printed start | Last printed pick-up | Boarding points | Untimed | Campus | Evening | Seats | Stored vs file | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| 05 | Poothotta | 06:50 | HMT 07:45 | 19 | 3 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 06 | Aroor | 07:05 | Anchumana 07:35 | 11 | 4 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 07 | Thoppumpadi | 07:05 | Thottakkattukara 07:55 | 8 | 2 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 08 | High Court | 07:20 | Paravoor Junction 07:55 | 10 | 5 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 09 | Palarivattom | 07:30 | Kalamassery Appolo Tyres 07:40 | 7 | 2 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 10 | Kakkanad Alinchuvadu | 07:25 | companypady 08:10 | 17 | 8 | 08:40 (target) | 16:00 | 49 | MATCH | check |
| 11 | Edapally Toll | 07:30 | Edaplly station 07:30 | 3 | 1 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 12 | Njarakkal | 07:00 | Chengamanadu 07:50 | 24 | 4 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 24 | Thrissur | 07:00 | Konur 08:05 | 6 | 0 | 08:40 (target) | 16:00 | 49 | MATCH | check |
| 15 | Thrissur | 07:00 | Perambra 07:50 | 10 | 3 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 16 | Thrissur | 07:00 | Kolathure 07:40 | 13 | 6 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 19 | Paravoor M. O | 07:20 | Kadungallore 07:50 | 11 | 5 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 21 | Moovathupuzha | 06:40 | Telk 07:58 | 21 | 6 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 22 | Perumbavoor | 07:45 | Oliyan kappela 08:10 | 12 | 6 | 08:40 (target) | 16:00 | 49 | MATCH | check |
| 23 | Vaduthala Bridge | 07:20 | Vapalassery 08:00 | 10 | 3 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 25 | MOONNUPEEDIKA | 07:15 | Chalakkudy 07:58 | 15 | 2 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 26 | Kodungallore | 07:20 | Pulikkakadavu 08:05 | 8 | 2 | 08:40 (target) | 16:00 | 49 | MATCH | check |
| 27 | Kakknad bus stand | 07:05 | Telk 07:55 | 20 | 13 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 28 | KMK | 07:20 | Pulikkakadavu 08:05 | 20 | 11 | 08:40 (target) | 16:00 | 49 | MATCH | check |
| 14 | S.C.M.S. Muttom | 07:40 | Angamaly 08:00 | 3 | 0 | 08:40 (target) | 16:00 | 49 | MATCH |  |
| 20 | VARAPUZHA | 07:15 | Thottakkattukara 07:55 | 11 | 0 | 08:40 (target) | 16:00 | 49 | MATCH |  |
