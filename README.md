# Password Inspector — Max Strength Edition

Author: Smaran (local build)
Date: 2025-11-06

## What this is
A standalone Java Swing application that:
- Evaluates password strength using a custom scoring algorithm (0–100).
- Live strength meter while typing.
- Generates fixed-length (26-char) passwords that the app's algorithm rates as 100/100.
- Keeps a local, in-memory history table of tested/generated passwords with masked display and raw values stored for internal use.
- Inline click-to-toggle reveal of a password in the history table (click the masked string to reveal/hide).
- Export history to CSV. (CSV contains raw passwords — see Security note below.)

## Files
- `MainGUI.java` — complete application source (single-file Swing app).
- `README.md` — this file.
- `HOW_IT_WORKS.md` — full code breakdown and tweak notes.

## Requirements
- Java JDK 17+ (OpenJDK or Oracle). `javac` and `java` must be on PATH.
- No external dependencies, pure Swing.

## Build & Run
From project directory:

If you use an IDE:
- Create a plain Java project, set Project SDK to JDK 17+, add `MainGUI.java` to `src`, run the `main` method.

## Quick usage
- Type a password in the top field — strength updates live.
- Press `Enter` to log the entry to the history (masked display).
- Click any masked password cell in the history to toggle it between masked and raw inline.
- Click **Generate (Max Strength)** to create a 26-character password that the app rates 100/100. The UI remains responsive while it's generated.
- Click **Export CSV** to export the history. CSV includes raw passwords (see Security note).

## Security note (READ THIS)
This app was built with features that **store raw passwords** in memory and allow exporting them to CSV. That is a deliberate design choice for debugging/testing. If you intend to use this beyond local testing (or handle other users’ data), **remove raw storage and export** immediately:
- Remove raw password column from `historyModel`.
- Update `commitCurrentPassword()` to store only masked passwords.
- Update `exportCSV()` to export masked values or disable export.

## Quick config tweaks
- Fixed generated password length (currently 26): change `FIXED_LENGTH` constant in the generator method.
- To auto-log generated passwords after generation, call `commitCurrentPassword()` at the end of the worker `done()` callback.
- To disable CSV exports: remove or stub `exportCSV()` and disable the export button in the UI.

## License
Use at your own risk. No warranty. This is a personal tool; secure it before you store or transport real user credentials.

