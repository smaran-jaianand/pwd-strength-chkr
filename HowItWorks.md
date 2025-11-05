# HOW_IT_WORKS — Full Code Breakdown (MainGUI.java)

This file explains every important part of `MainGUI.java`. Use it to audit, modify, or extend the application.

---

## High-level architecture
Single-file Java Swing app. UI drives everything. Key logical pieces:

- UI components (Swing widgets)
- Password scoring (computeScore + classifyScore + estimateEntropy)
- Generator (constructive + mutate + fallback) running in a SwingWorker
- History model (DefaultTableModel) stores: index, masked, raw, score, label, timestamp
- Inline reveal via MouseListener on the JTable
- CSV export reading the raw column
- No persistent disk storage except optional CSV export

---

## Main sections in source

### 1) Fields / Globals
Key fields:
- `pwdField` — JPasswordField for user input.
- `genBtn`, `exportBtn`, `copyBtn`, `clearBtn` — controls.
- `bar`, `scoreLabel`, `verdictLabel` — UI for strength indicator.
- `suggestionsArea` — actionable tips.
- `historyModel` — `DefaultTableModel` with columns:
  0. `#` (int)
  1. `Password (masked)` (String shown in UI; toggled on click)
  2. `Password (raw)` (String, stored in model for export / reveal)
  3. `Score` (int)
  4. `Verdict` (String)
  5. `Time` (String timestamp)
- `historyTable` — JTable bound to `historyModel`.

Security note: raw password is stored in column 2. If you want no raw storage, remove that column and update callers.

---

### 2) `initUI()`
- Lays out UI (top input panel, middle progress/verdict, suggestions, bottom history panel).
- Wires listeners:
  - `showBox` toggles echo char in password field.
  - `copyBtn` copies current field contents to clipboard.
  - `clearBtn` clears field and UI.
  - `genBtn` starts `generateMaxStrengthPassword()` SwingWorker.
  - `exportBtn` calls `exportCSV()`.
  - `pwdField` has `DocumentListener` for live updates and `ActionListener` to commit (on Enter).
  - `historyTable` has `MouseListener` for inline reveal:
    - On click, if column == 1 (masked column) then toggles between masked value and raw value stored in column 2 by checking presence of `*`.

Note: `historyTable` column indices are based on the current model. If you hide/remove columns, index mapping changes.

---

### 3) `commitCurrentPassword()`
What it does:
- Reads current password: `String pwd = String.valueOf(pwdField.getPassword());`
- If blank → return silently.
- Computes `score = computeScore(pwd)` and `verdict = classifyScore(score)`.
- Builds `masked = maskForHistory(pwd)` — mask hides all but first/last char (or all if len <=2).
- Timestamp with `SimpleDateFormat("yyyy-MM-dd HH:mm:ss")`.
- Adds row: `[index, masked, raw, score, verdict, time]` to `historyModel`.

Important: This is how the raw password gets into the model for export/reveal.

---

### 4) Masking helper: `maskForHistory(String pwd)`
- If length <= 2, returns `*` repeated length times.
- Else returns `firstChar + '*' * (len-2) + lastChar`.

Modify if you want different masking behavior.

---

### 5) Scoring & Classification
#### `classifyScore(int score)`
- Maps numeric score to categories:
  - `<25` → "Very Weak"
  - `<40` → "Weak"
  - `<60` → "Moderate"
  - `<80` → "Strong"
  - `>=80` → "Very Strong"

#### `computeScore(String password)`
This is the guts. Step-by-step:
1. `score = 0`
2. `entropy = estimateEntropy(password)` — see below.
3. **Length influence**: `score += Math.min(password.length() * 3, 60);`
   - Up to 60 points for length. That allows a long diverse password to reach final 100.
4. **Diversity**: detects presence of lowercase, uppercase, digits, special.
   - `diversityCount` in [0,4]; `score += diversityCount * 8;` (up to +32)
5. **Penalties**:
   - Repetition: `if ((.)\1{2,})` → `score -= 6` (three identical chars in a row).
   - Sequence detection `containsSequence()` → `score -= 6` (abc / 123).
   - Exact common password match (from `COMMON_PASSWORDS` set) → `score -= 20`
6. **Entropy boost**:
   - `if entropy > 60` → +15
   - `else if entropy > 45` → +8
   - `else if entropy < 28` → -6
7. `return clamp(score, 0, 100)`.

Why these numbers? They were tuned so:
- Length and diversity dominate.
- Entropy gives a useful boost.
- Penalties prevent short, patterned, or common-pass cases from reaching 100.
- Score 100 is attainable for fixed-length (26) diverse passwords.

#### `estimateEntropy(String password)`
- Determines `charSpace` = sum of character class sizes present:
  - lowercase present → +26
  - uppercase present → +26
  - digits present → +10
  - special present → +32
- Returns: `length * log2(charSpace)`.
- This is a rough Shannon-style estimate assuming uniform random picks from the detected char space.

#### `containsSequence(String s)`
- Lowercases string.
- Checks every triplet `(c1,c2,c3)` for:
  - `c2 == c1 +/- 1` and `c3 == c2 +/- 1` with same direction.
- Catches `abc`, `123`, `cba`, `321` sequences.

---

### 6) Generator: `generateMaxStrengthPassword()` and helpers
Goal: produce fixed-length passwords (26 chars) that compute to `score >= 100` under `computeScore()`.

#### Workflow
- Runs in a `SwingWorker` to avoid freezing the EDT (UI thread).
- `FIXED_LENGTH = 26` (currently).
- Steps:
  1. `constructGuaranteedStrongPassword(length)` — deterministic constructive approach; seeds diversity and avoids repeats/sequences.
  2. If the candidate hits `computeScore(candidate) >= 100`, return it.
  3. Else mutate the deterministic candidate up to N attempts (`mutatePasswordKeepLength()`), checking score each attempt.
  4. Else fallback to randomized `generatePassword(length)` for M attempts.
  5. If nothing reached 100 (very unlikely), return the deterministic candidate.

#### `constructGuaranteedStrongPassword(int length)`
- Seeds `sb` with one random char of each class: lower, upper, digit, special.
- Fills remaining characters avoiding immediate repeats and avoiding creating increasing/decreasing 3-char sequences.
- Shuffles resulting chars for randomness.
- Ensures final string contains all 4 classes — if missing, force-insert them at top positions.
- Returns final String of fixed length.

#### `mutatePasswordKeepLength(String base)`
- Swaps two random positions.
- Replaces one random position with a random char from full charset.
- Returns mutated string.

#### `generatePassword(int length)` (fallback)
- Seeds with one char from each class, fills rest with random picks, shuffles.

Why these steps:
- Deterministic build is fastest and most reliable to reach high score (guarantee diversity and avoid penalties).
- Mutation preserves length and attempts to fix remaining weak spots.
- Random fallback ensures extra coverage.

---

### 7) Concurrency & UI safety
- `generateMaxStrengthPassword()` uses `SwingWorker<String, Void>`:
  - `doInBackground()` runs off EDT (safe to loop/mutate).
  - `done()` executes on EDT and updates the UI (`pwdField.setText()` and `updateUIFor()`).
- All UI updates (changing components, table model updates) are done on the EDT.
- `DocumentListener` and `ActionListener` are on EDT — keep their body light (they are).

---

### 8) Inline reveal in table
- `historyTable.addMouseListener(new MouseAdapter(){ mouseClicked(...) })`:
  - Detects row/column clicked.
  - If clicked column is the masked password column (index 1) and row >= 0:
    - Reads masked string at `(row,1)` and raw string at `(row,2)`.
    - If masked contains `*` → sets model value `(row,1)` to raw (unmasked).
    - Else sets model value `(row,1)` back to `maskForHistory(raw)`.
- UX: one click toggles masked ↔ raw for that row's password cell.

Security note: toggling reveals the raw password in the UI — it's visible in memory and on-screen.

---

### 9) CSV export
- `exportCSV()` iterates rows and reads raw password from column 2.
- Writes CSV header: `Index,Password,Score,Verdict,Time`.
- Quotes the password field to escape commas / quotes.
- Writes file using `Files.newBufferedWriter`.

**If you need safety**: change export to use masked value or remove ability to export at all. See Tweak section.

---

### 10) Tweak points (where to change behavior quickly)
- **Disable raw storage/export**:
  - Remove the `Password (raw)` column from `historyModel`.
  - Change `commitCurrentPassword()` to add only `[index, masked, score, verdict, time]`.
  - Update `exportCSV()` to read masked column index instead.
- **Change generated password length**:
  - In `generateMaxStrengthPassword()` change `FIXED_LENGTH` constant.
  - Adjust scoring weights if you alter length drastically.
- **Auto-log generated passwords**:
  - Call `commitCurrentPassword()` at the end of `SwingWorker.done()` once you set the pwdField to the generated string.
- **Make generation faster**:
  - Increase the deterministic builder's length or tweak it to directly assemble a known-good template without many attempts.
- **Make generation deterministic for tests**:
  - Seed `rnd` with a fixed seed: `new SecureRandom(new byte[]{...})` or use `Random` with seed.

---

### 11) Debugging & testing tips
- Add `System.out.println()` in `doInBackground()` to track attempts if generation is failing.
- Use unit tests for `computeScore()`:
  - Test edge cases: short passwords, repeated chars, sequences, common passwords.
- Validate `estimateEntropy()` outputs with expected rough numbers.
- Ensure EDT safety: don't run long loops on UI thread — use `SwingWorker`.

---

### 12) Suggested improvements (next steps)
- Replace CSV export with encrypted vault (`AES`) to safely store generated passwords.
- Add optional password manager integration or KeePass-compatible export (use libraries).
- Add a hashed-only history where only salted hashes of raw passwords are stored (if you must persist).
- Add copy-with-timeout (automatically clear clipboard after N seconds).
- Replace Swing with JavaFX for modern UI and cleaner styling (requires additional setup).

---

### 13) Final security checklist (do this before sharing tool)
- Remove raw password storage if you will handle other people’s data.
- Remove CSV export or change it to export masked values.
- Add file encryption if storing on disk.
- Add a prominent warning in UI about raw password storage.

---

End of breakdown.
