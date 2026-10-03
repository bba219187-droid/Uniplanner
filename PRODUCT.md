# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
University students anywhere in the world, at any level (licenciatura/bachelor, master's, PhD). They juggle several courses at once, with tests, assignment deadlines, timetables and their own study time, often alongside gym, health and a social life. The first real users are students at IPCA (Portugal), the author's own course, so Portuguese-speaking students are the earliest test group, but nothing in the product may assume one university or country.

## Product Purpose
UniPlanner helps a student run their whole semester from their phone: knowing what is due, studying enough for each course, and keeping the rest of life on track. Success means a student opens it every day, plans the week from it, and ends the semester having studied the hours they meant to and met every deadline.

## Positioning
Everything a student needs in one app, and free. Study timer and hours per course, agenda with tests and deadlines, timetable, Moodle, grades and ECTS average, gym, health, friends, groups and chat live together instead of in five separate apps, at no cost to the student.

## Operating Context
- Used on Android phones, many times a day, often in short bursts between classes.
- A weekly plan arrives every Monday; reminders come before tests and deadlines.
- Imports deadlines from the university Moodle (calendar iCal export link) and opens Moodle inside the app to submit work, because some universities (IPCA included) disable Moodle's mobile web service.
- Timetables come from the school system (SIGA at IPCA), an iCal link, or a photo.
- Reads and writes the phone's own calendar (Samsung/Google) in both directions.
- Online from day one: Google sign-in, data copied to the account, groups and chat between classmates.
- Distributed today as an APK from GitHub releases; Google Play is planned but not set up yet.

## Capabilities and Constraints
- Native Android: Kotlin, Jetpack Compose, Firebase (Auth, Firestore). minSdk 26.
- Main tabs: Estudo (study), Agenda, Ginásio (gym), Amigos (friends), Saúde (health) and Mais (settings). The settings button must stay in the bottom bar.
- Languages: Portuguese and English.
- Firebase is on the free plan: no Storage, so shared files are limited (up to 5 MB, sent in chunks).
- Chat has end-to-end encryption with a PIN, plus voice/video calls.
- Location sharing with friends and an opt-in admin map; every permission is asked at the end of the first-run questionnaire, never earlier.
- Android only. No iPhone version (decided 2026-10-02).
- Undecided: a paid "Pro" version is planned for later, with no defined contents. The core app stays free.

## Brand Commitments
- Name: **UniPlanner**. Company shown in the app: **TecSolve** ("© 2026 TecSolve" on the entry screen).
- The author, Pedro Oliveira, must not appear anywhere inside the app. He is credited only on GitHub (README, license, commit author).
- Language in the app is plain and friendly, written for students, not technical.

## Evidence on Hand
- Working app on branch `feature/primeira-versao` (v0.2.x), with real screens for every tab.
- In-app changelog at `app/src/main/assets/novidades.json`.
- No testimonials, user counts, store ratings, press or partner universities exist yet. Do not invent any.

## Product Principles
1. One place for the whole semester: a new feature should connect to study, agenda or courses, not live as an island.
2. Free for every student: never put core study or planning behind a paywall.
3. Works for any university in the world: no hard-coded school, country, grading scale or language.
4. Respect the phone and the person: permissions asked once and explained, location only with explicit consent, private chat encrypted.
5. Fast between classes: the most common action on each tab should take one or two taps.
