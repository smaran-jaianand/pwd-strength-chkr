import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.*;

public class MainGUI extends JFrame {
    // UI components
    private final JPasswordField pwdField = new JPasswordField(28);
    private final JCheckBox showBox = new JCheckBox("Show");
    private final JButton copyBtn = new JButton("Copy");
    private final JButton clearBtn = new JButton("Clear");
    private final JButton genBtn = new JButton("Generate (Max Strength)");
    private final JButton exportBtn = new JButton("Export CSV");

    private final JLabel verdictLabel = new JLabel("Type password...", SwingConstants.CENTER);
    private final JLabel scoreLabel = new JLabel("0/100");
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JTextArea suggestionsArea = new JTextArea(6, 36);

    private final DefaultTableModel historyModel = new DefaultTableModel(
            new String[]{"#", "Password (masked)", "Score", "Verdict", "Time"}, 0);
    private final JTable historyTable = new JTable(historyModel);
    private final SecureRandom rnd = new SecureRandom();

    private static final Set<String> COMMON_PASSWORDS = new HashSet<>(Arrays.asList(
            "password", "123456", "123456789", "qwerty", "abc123", "111111",
            "password1", "12345678", "iloveyou", "admin", "welcome", "letmein"
    ));
    private TableColumn hiddenPasswordColumn = null;

    public MainGUI() {
        super("Password Inspector — Max Strength Edition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        initUI();
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
        setVisible(true);
    }

    private void initUI() {
        // === Top Panel (input + actions) ===
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        inputPanel.add(new JLabel("Password:"));
        pwdField.setEchoChar('\u2022');
        inputPanel.add(pwdField);
        inputPanel.add(showBox);
        inputPanel.add(copyBtn);
        inputPanel.add(clearBtn);
        inputPanel.add(genBtn);

        // === Middle Section (progress + verdict) ===
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(420, 24));
        verdictLabel.setFont(verdictLabel.getFont().deriveFont(Font.BOLD, 14f));
        scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD, 12f));
        JPanel mid = new JPanel(new BorderLayout(8, 8));
        JPanel topMid = new JPanel(new BorderLayout());
        topMid.add(bar, BorderLayout.CENTER);
        topMid.add(scoreLabel, BorderLayout.EAST);
        mid.add(topMid, BorderLayout.NORTH);
        mid.add(verdictLabel, BorderLayout.SOUTH);

        // === Suggestions Box ===
        suggestionsArea.setEditable(false);
        suggestionsArea.setLineWrap(true);
        suggestionsArea.setWrapStyleWord(true);
        suggestionsArea.setBorder(BorderFactory.createTitledBorder("Suggestions"));
        suggestionsArea.setBackground(new Color(250, 250, 250));

        // === History Table ===
        historyTable.setFillsViewportHeight(true);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.getColumnModel().getColumn(0).setMaxWidth(40);

        JScrollPane histScroll = new JScrollPane(historyTable);
        histScroll.setPreferredSize(new Dimension(540, 140));

        // === History Panel ===
        JPanel histPanel = new JPanel(new BorderLayout());
        histPanel.setBorder(BorderFactory.createTitledBorder("History"));
        histPanel.add(histScroll, BorderLayout.CENTER);

        // Bottom panel under history for export + visibility controls
        JPanel bottomHistPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JCheckBox showPasswords = new JCheckBox("Show passwords in table");
        bottomHistPanel.add(showPasswords);
        bottomHistPanel.add(exportBtn);
        histPanel.add(bottomHistPanel, BorderLayout.SOUTH);

        // === Assemble Left Side (Main UI) ===
        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.add(inputPanel, BorderLayout.NORTH);
        left.add(mid, BorderLayout.CENTER);
        left.add(suggestionsArea, BorderLayout.SOUTH);

        add(left, BorderLayout.NORTH);
        add(histPanel, BorderLayout.SOUTH);

        // === Actions ===
        showBox.addActionListener(e -> toggleShow(showBox.isSelected()));
        copyBtn.addActionListener(e -> copyToClipboard(String.valueOf(pwdField.getPassword())));
        clearBtn.addActionListener(e -> {
            pwdField.setText("");
            updateUIFor("");
        });
        exportBtn.addActionListener(e -> exportCSV());
        genBtn.addActionListener(e -> generateMaxStrengthPassword());

        // Live update while typing
        pwdField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { liveUpdate(); }
            public void removeUpdate(DocumentEvent e) { liveUpdate(); }
            public void changedUpdate(DocumentEvent e) { liveUpdate(); }
        });

        // Pressing Enter logs the current password
        pwdField.addActionListener(e -> commitCurrentPassword());

        // === Show/Hide Password Column in Table ===
        showPasswords.addActionListener(e -> {
            boolean visible = showPasswords.isSelected();
            TableColumnModel colModel = historyTable.getColumnModel();
            try {
                if (visible) {
                    // add raw password column (index 2)
                    if (hiddenPasswordColumn != null) {
                        colModel.addColumn(hiddenPasswordColumn);
                        colModel.moveColumn(colModel.getColumnCount() - 1, 2);
                        hiddenPasswordColumn = null;
                    }
                } else {
                    // hide raw password column
                    if (hiddenPasswordColumn == null && colModel.getColumnCount() > 2) {
                        hiddenPasswordColumn = colModel.getColumn(2);
                        colModel.removeColumn(hiddenPasswordColumn);
                    }
                }
            } catch (Exception ex) {
                System.err.println("Column toggle error: " + ex.getMessage());
            }
        });
    }

    // UI behaviors
    private void toggleShow(boolean show) {
        pwdField.setEchoChar(show ? (char)0 : '\u2022');
    }

    private void liveUpdate() {
        updateUIFor(String.valueOf(pwdField.getPassword()));
    }

    private void updateUIFor(String pwd) {
        if (pwd == null || pwd.isBlank()) {
            verdictLabel.setText("Type password...");
            suggestionsArea.setText("Enter a password to see actionable suggestions.");
            bar.setValue(0);
            scoreLabel.setText("0/100");
            bar.setForeground(Color.RED);
            return;
        }
        int score = computeScore(pwd);
        String verdict = classifyScore(score);
        verdictLabel.setText(verdict);
        scoreLabel.setText(score + "/100");
        bar.setValue(score);
        setBarColor(score);
        suggestionsArea.setText(makeSuggestions(pwd, score));
    }

    private void setBarColor(int score) {
        if (score < 25) bar.setForeground(Color.RED);
        else if (score < 60) bar.setForeground(Color.ORANGE);
        else bar.setForeground(new Color(0, 140, 0));
    }

    private void commitCurrentPassword() {
        String pwd = String.valueOf(pwdField.getPassword());
        if (pwd == null || pwd.isBlank()) return;

        int score = computeScore(pwd);
        String verdict = classifyScore(score);
        String masked = maskForHistory(pwd);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        historyModel.addRow(new Object[]{
                historyModel.getRowCount() + 1, // #
                masked,                         // masked display
                pwd,                            // raw password (hidden)
                score,
                verdict,
                time
        });
    }


    private String maskForHistory(String pwd) {
        if (pwd.length() <= 2) return "*".repeat(pwd.length());
        return pwd.charAt(0) + "*".repeat(Math.max(0, pwd.length() - 2)) + pwd.charAt(pwd.length() - 1);
    }

    // Password computation (updated so 100 is attainable)
    public static String classifyScore(int score) {
        if (score < 25) return "Very Weak";
        else if (score < 40) return "Weak";
        else if (score < 60) return "Moderate";
        else if (score < 80) return "Strong";
        else return "Very Strong";
    }

    private static int computeScore(String password) {
        int score = 0;
        double entropy = estimateEntropy(password);

        // Increased length influence so 100 can be reached for long diverse passwords
        score += Math.min(password.length() * 3, 60); // up to 60 points for length

        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        boolean hasDigit = Pattern.compile("\\d").matcher(password).find();
        boolean hasSpecial = Pattern.compile("[^A-Za-z0-9]").matcher(password).find();

        int diversity = (hasLower ? 1 : 0) + (hasUpper ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        score += diversity * 8;

        // penalties
        if (Pattern.compile("(.)\\1{2,}").matcher(password).find()) score -= 6; // repetition
        if (containsSequence(password)) score -= 6; // sequences
        if (COMMON_PASSWORDS.contains(password.toLowerCase())) score -= 20; // exact common password

        // entropy influence
        if (entropy > 60) score += 15;
        else if (entropy > 45) score += 8;
        else if (entropy < 28) score -= 6;

        return Math.max(0, Math.min(score, 100));
    }

    private static double estimateEntropy(String password) {
        int charSpace = 0;
        if (password.matches(".*[a-z].*")) charSpace += 26;
        if (password.matches(".*[A-Z].*")) charSpace += 26;
        if (password.matches(".*[0-9].*")) charSpace += 10;
        if (password.matches(".*[^A-Za-z0-9].*")) charSpace += 32;
        if (charSpace == 0) return 0.0;
        return password.length() * (Math.log(charSpace) / Math.log(2));
    }

    private static boolean containsSequence(String s) {
        String lower = s.toLowerCase();
        for (int i = 0; i < lower.length() - 2; i++) {
            char c1 = lower.charAt(i);
            char c2 = lower.charAt(i + 1);
            char c3 = lower.charAt(i + 2);
            if ((c2 == c1 + 1 && c3 == c2 + 1) || (c2 == c1 - 1 && c3 == c2 - 1)) return true;
        }
        return false;
    }

    private String makeSuggestions(String pwd, int score) {
        List<String> s = new ArrayList<>();
        if (COMMON_PASSWORDS.contains(pwd.toLowerCase())) s.add("Common password — change immediately.");
        if (pwd.length() < 12) s.add("Increase length to 12+ characters.");
        if (!Pattern.compile("[A-Z]").matcher(pwd).find()) s.add("Add uppercase letters.");
        if (!Pattern.compile("[a-z]").matcher(pwd).find()) s.add("Add lowercase letters.");
        if (!Pattern.compile("\\d").matcher(pwd).find()) s.add("Add digits.");
        if (!Pattern.compile("[^A-Za-z0-9]").matcher(pwd).find()) s.add("Add symbols.");
        if (Pattern.compile("(.)\\1{2,}").matcher(pwd).find()) s.add("Avoid repeated characters.");
        if (containsSequence(pwd)) s.add("Avoid simple sequences.");
        if (score >= 100) s.add("This password is maxed out. Military-grade.");
        if (s.isEmpty()) s.add("Looks good. Consider lengthening further for extra safety.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.size(); i++) sb.append(i + 1).append(". ").append(s.get(i)).append("\n");
        return sb.toString();
    }

    // Utilities
    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        JOptionPane.showMessageDialog(this, "Password copied to clipboard (be careful).", "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    // --- UPDATED EXPORT: exports real (unmasked) passwords ---
    private void exportCSV() {
        if (historyModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No history to export.", "Empty", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export History CSV");
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv"))
            file = new File(file.getParentFile(), file.getName() + ".csv");

        try (BufferedWriter bw = Files.newBufferedWriter(file.toPath())) {
            // write header
            bw.write("Index,Password,Score,Verdict,Time\n");

            // now export actual password (raw column) instead of masked
            for (int r = 0; r < historyModel.getRowCount(); r++) {
                String idx = historyModel.getValueAt(r, 0).toString();
                String rawPwd = historyModel.getValueAt(r, 2).toString(); // column 2 = raw password
                String score = historyModel.getValueAt(r, 3).toString();
                String verdict = historyModel.getValueAt(r, 4).toString();
                String time = historyModel.getValueAt(r, 5).toString();

                // escape quotes and commas if needed
                rawPwd = "\"" + rawPwd.replace("\"", "\"\"") + "\"";
                bw.write(String.format("%s,%s,%s,%s,%s%n", idx, rawPwd, score, verdict, time));
            }

            JOptionPane.showMessageDialog(this, "Exported (unmasked) passwords to:\n" + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    // --- FIXED-LENGTH MAX-STRENGTH GENERATOR (runs off EDT) ---
    private void generateMaxStrengthPassword() {
        final int FIXED_LENGTH = 26; // fixed length for max-strength
        genBtn.setEnabled(false);
        genBtn.setText("Generating...");
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                // deterministic constructive build first
                String pwd = constructGuaranteedStrongPassword(FIXED_LENGTH);
                if (computeScore(pwd) >= 100) return pwd;

                // mutate the deterministic candidate a number of times (preserves fixed length)
                for (int attempt = 0; attempt < 2000; attempt++) {
                    String candidate = mutatePasswordKeepLength(pwd);
                    if (computeScore(candidate) >= 100) return candidate;
                }

                // fallback: randomized builds of same fixed length
                for (int attempt = 0; attempt < 5000; attempt++) {
                    String candidate = generatePassword(FIXED_LENGTH);
                    if (computeScore(candidate) >= 100) return candidate;
                }

                // last resort: return the deterministic candidate (very likely very strong)
                return pwd;
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    pwdField.setText(result);
                    updateUIFor(result);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainGUI.this, "Generation failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    genBtn.setEnabled(true);
                    genBtn.setText("Generate (Max Strength)");
                }
            }
        };
        worker.execute();
    }

    // Construct a strong fixed-length password that avoids repeats/sequences and ensures diversity.
    private String constructGuaranteedStrongPassword(int length) {
        final String lowers = "abcdefghijklmnopqrstuvwxyz";
        final String uppers = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String digits = "0123456789";
        final String specials = "!@#$%^&*()-_=+[]{};:,.<>?/";
        final String all = lowers + uppers + digits + specials;

        // Start with guaranteed diversity seeds
        StringBuilder sb = new StringBuilder(length);
        sb.append(lowers.charAt(rnd.nextInt(lowers.length())));
        sb.append(uppers.charAt(rnd.nextInt(uppers.length())));
        sb.append(digits.charAt(rnd.nextInt(digits.length())));
        sb.append(specials.charAt(rnd.nextInt(specials.length())));

        // Fill the rest carefully to avoid simple patterns and long repeats
        char last = sb.charAt(sb.length() - 1);
        for (int i = 4; i < length; i++) {
            char pick;
            int tries = 0;
            do {
                pick = all.charAt(rnd.nextInt(all.length()));
                tries++;
                if (pick == last) continue; // avoid immediate repeats
                // avoid making an increasing or decreasing 3-char sequence
                if (i >= 2) {
                    char c1 = sb.charAt(i - 2);
                    char c2 = sb.charAt(i - 1);
                    if ((c2 == c1 + 1 && pick == c2 + 1) || (c2 == c1 - 1 && pick == c2 - 1)) continue;
                }
                if (tries > 25) break; // accept pick after many tries
                break;
            } while (true);
            sb.append(pick);
            last = pick;
        }

        // Shuffle to break constructed ordering
        List<Character> chars = new ArrayList<>();
        for (char c : sb.toString().toCharArray()) chars.add(c);
        Collections.shuffle(chars, rnd);
        StringBuilder out = new StringBuilder();
        for (char c : chars) out.append(c);

        // Final quick sanitization: ensure all 4 classes exist (if any missing, force-insert)
        String candidate = out.toString();
        boolean hasLower = Pattern.compile("[a-z]").matcher(candidate).find();
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(candidate).find();
        boolean hasDigit = Pattern.compile("\\d").matcher(candidate).find();
        boolean hasSpecial = Pattern.compile("[^A-Za-z0-9]").matcher(candidate).find();

        StringBuilder finalSb = new StringBuilder(candidate);
        int idx = 0;
        if (!hasLower) finalSb.setCharAt(idx++ % length, lowers.charAt(rnd.nextInt(lowers.length())));
        if (!hasUpper) finalSb.setCharAt(idx++ % length, uppers.charAt(rnd.nextInt(uppers.length())));
        if (!hasDigit) finalSb.setCharAt(idx++ % length, digits.charAt(rnd.nextInt(digits.length())));
        if (!hasSpecial) finalSb.setCharAt(idx++ % length, specials.charAt(rnd.nextInt(specials.length())));

        return finalSb.toString();
    }

    // Slight mutation that preserves fixed length: swap two positions and replace one random char
    private String mutatePasswordKeepLength(String base) {
        char[] arr = base.toCharArray();
        // swap two random positions
        int i = rnd.nextInt(arr.length);
        int j = rnd.nextInt(arr.length);
        char tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
        // replace a random position with a random char from full space
        final String all = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+[]{};:,.<>?/";
        arr[rnd.nextInt(arr.length)] = all.charAt(rnd.nextInt(all.length()));
        return new String(arr);
    }

    // Randomized fixed-length generator for fallback
    private String generatePassword(int length) {
        final String lowers = "abcdefghijklmnopqrstuvwxyz";
        final String uppers = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String digits = "0123456789";
        final String specials = "!@#$%^&*()-_=+[]{};:,.<>?/";
        final String all = lowers + uppers + digits + specials;

        StringBuilder sb = new StringBuilder(length);
        // seed with diversity to increase odds
        sb.append(lowers.charAt(rnd.nextInt(lowers.length())));
        sb.append(uppers.charAt(rnd.nextInt(uppers.length())));
        sb.append(digits.charAt(rnd.nextInt(digits.length())));
        sb.append(specials.charAt(rnd.nextInt(specials.length())));
        for (int i = 4; i < length; i++) sb.append(all.charAt(rnd.nextInt(all.length())));
        List<Character> chars = new ArrayList<>();
        for (char c : sb.toString().toCharArray()) chars.add(c);
        Collections.shuffle(chars, rnd);
        StringBuilder result = new StringBuilder();
        for (char c : chars) result.append(c);
        return result.toString();
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(MainGUI::new);
    }
}
