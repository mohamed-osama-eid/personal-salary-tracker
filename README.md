# Payment Tracker

**Payment Tracker** is a personal work and salary-cycle tracker for tutors. It keeps daily shift records, calculates earnings as you work, tracks sessions and student counts, and helps you stay on top of a 30-day payment cycle.

## What it does

- Tracks progress through a 30-day salary cycle and shows the days remaining.
- Calculates the current payout, pending wallet amount, overtime, and earned income for each logged shift.
- Records onsite and work-from-home shifts, including scheduled/actual times, double shifts, and six-hour shifts.
- Logs normal and camp sessions, with student counts for every session block.
- Breaks session activity down by type and size, with total sessions and hours.
- Provides a calendar-style view of the cycle and a complete log history.
- Lets you remove incorrect logs and manage salary, bonus, and session-reward rules from a settings screen.
- Supports multiple recurring payment cycles.

## Tech stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose with Material Design 3 |
| Architecture | MVVM with Kotlin Coroutines and StateFlow |
| Local persistence | Room Database (SQLite), powered by KSP |
| Navigation | Jetpack Navigation Compose with type-safe state routing |
| Build system | Gradle with Kotlin DSL (`.gradle.kts`) |
| Testing | Robolectric for local JVM tests and Roborazzi for UI screenshot verification |

## Screenshots

| Dashboard | Shift logging |
| --- | --- |
| ![Dashboard showing cycle progress, net payout, and session analysis](images/7.jpeg) | ![Onsite shift logging form](images/5.jpeg) |

| Work-from-home sessions | Cycle calendar and recent logs |
| --- | --- |
| ![Work-from-home session logging form](images/4.jpeg) | ![Calendar view and recent shift logs](images/6.jpeg) |

| Full shift history | Salary and reward settings |
| --- | --- |
| ![All active cycle shift records](images/3.jpeg) | ![Core contract valuation settings](images/1.jpeg) |

| Payment-cycle management |
| --- |
| ![Registered billing cycles and create-cycle form](images/2.jpeg) |

## How the cycle is organized

The dashboard separates the cycle into two payout areas:

- **Current Payout Block (Days 1-22):** shows income available in the current payout.
- **Pending Wallet Reserve (Days 23-30):** keeps the remaining cycle earnings separate until the cycle is complete.

## Configurable rules

Payment Tracker lets you tailor calculations to your work arrangement:

- Payment cycle name and base monthly pay
- Optional media flat bonus
- Normal-session rewards for low and high student counts
- Camp-session rewards for low and high student counts

## Adding a shift

1. Choose **Onsite Shift** or **Work From Home**.
2. Enter the date and, for onsite work, the scheduled start, arrival, and finish times.
3. Select any applicable shift type, such as double or six-hour shift.
4. Add normal or camp session blocks with the number of students.
5. Submit the log. The dashboard, calendar, session analysis, and payout calculations update accordingly.
