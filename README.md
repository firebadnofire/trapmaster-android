# Trap Coach

Trap Coach is a **Progressive Web App (PWA)** for tracking and analyzing trap shooting sessions. It provides a clean Material Design-inspired interface, local storage for session history, and CSV export functionality — all offline-ready.

---

## 🎯 Features

* **Two Game Modes:**

  * *Record As You Go*: Log each hit or miss in real time.
  * *Record Full Round*: Quickly input results for a full round at once.

* **Game Summary:**

  * Displays per-round hit/miss icons.
  * Calculates total hits across 25 shots.
  * Shows a detailed round breakdown.

* **Game History:**

  * Automatically stores all games in the browser’s local storage.
  * Displays date/time and score for each recorded session.
  * Allows reopening any past game summary.

* **Export Capability:**

  * Export all stored games as a CSV file (`trap_coach_log_YYYY-MM-DD.csv`).
  * Each row includes: `game_start_time`, `round_number`, `shot_number`, and `result`.

* **Offline Support:**

  * Fully functional PWA via `service-worker.js`.
  * Can be installed to the home screen on mobile or desktop.

---

## 🧠 How It Works

### Local Storage Schema

Games are stored under the key `trapCoachGames` in the browser’s Local Storage:

```json
[
  {
    "rounds": [
      { "shots": [1, 0, 1, 1, 0] },
      { "shots": [1, 1, 0, 1, 1] }
    ],
    "currentRound": 0,
    "currentShot": 0,
    "startTime": "2025-11-11T12:00:00.000Z"
  }
]
```

### CSV Export Format

```
game_start_time,round_number,shot_number,result
2025-11-11T12:00:00Z,1,1,1
2025-11-11T12:00:00Z,1,2,0
```

---

## 🧩 UI Overview

* **Home Screen:** Start new games, review history, or export data.
* **Record Screens:** Intuitive full-width HIT/LOSS buttons or quick-select grids.
* **Summary Screen:** Visual overview of each round with color-coded icons.

All visuals are styled with Material Design 3 color variables and responsive layout helpers defined in CSS.

---

## ⚙️ Technical Details

* **Frontend:** Pure HTML, CSS, and Vanilla JS — no frameworks.
* **Design:** Material 3-inspired color scheme using CSS custom properties.
* **Storage:** Browser Local Storage.
* **Offline:** Service Worker registration at `/trapmaster/service-worker.js`.

---

## 📦 Installation

1. Serve the project directory over HTTPS or localhost.
2. Open the app in a modern browser.
3. Click *Add to Home Screen* or install the PWA.
4. Start logging your sessions!

---

## 🧾 License

This project is open for personal or educational use. Attribution is appreciated if reused or modified.

---

## 💡 Future Enhancements

* Persistent cloud sync (optional backup).
* Advanced analytics and visual charts.
* Integration with real-world shot sensors or scoring devices.
* Dark mode and theme customization.

---

**Author:** Archuserorg
**Version:** 1.0
**Last Updated:** November 2025

