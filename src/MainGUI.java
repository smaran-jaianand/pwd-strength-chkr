import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.regex.*;

public class MainGUI extends JFrame {
    private final JPasswordField pwdField = new JPasswordField(24);
    private final JCheckBox showBox = new JCheckBox("Show");
    private final JButton checkBtn = new JButton("Check Strength");
    private final JLabel resultLabel = new JLabel("Enter password and press Check", SwingConstants.CENTER);
    private final JProgressBar bar = new JProgressBar(0, 100);

    private static final Set<String> COMMON_PASSWORDS = new HashSet<>(Arrays.asList(
            "password", "123456", "123456789", "qwerty", "abc123", "111111",
            "password1", "12345678", "iloveyou", "admin", "welcome", "letmein"
    ));

    public MainGUI() {
        super("Password Strength — No BS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        top.add(new JLabel("Password:"));
        top.add(pwdField);
        top.add(showBox);

        JPanel mid = new JPanel(new BorderLayout(8, 8));
        mid.add(checkBtn, BorderLayout.NORTH);
        mid.add(bar, BorderLayout.CENTER);

        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 14f));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(resultLabel, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(mid, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);

        // Actions
        checkBtn.addActionListener(e -> doCheck());
        pwdField.addActionListener(e -> doCheck()); // Enter key
        showBox.addActionListener(e -> toggleShow(showBox.isSelected()));

        // initial bar look
        bar.setStringPainted(true);

        setVisible(true);
    }

    private void toggleShow(boolean show) {
        if (show) {
            pwdField.setEchoChar((char)0);
        } else {
            pwdField.setEchoChar('\u2022');
        }
    }

    private void doCheck() {
        String pwd = String.valueOf(pwdField.getPassword());
        if (pwd == null || pwd.isBlank()) {
            resultLabel.setText("Invalid (Empty Password)");
            bar.setValue(0);
            bar.setForeground(Color.RED);
            return;
        }
        int score = computeScore(pwd);
        String label = classifyScore(score);
        resultLabel.setText(String.format("%s — %d/100", label, score));
        bar.setValue(score);
        if (score < 25) bar.setForeground(Color.RED);
        else if (score < 60) bar.setForeground(Color.ORANGE);
        else bar.setForeground(new Color(0, 128, 0)); // green-ish
    }

    // compute score (same logic as your CLI; returns 0..100)
    private static int computeScore(String password) {
        int score = 0;
        double entropy = estimateEntropy(password);

        // length scoring
        score += Math.min(password.length(), 20);

        // diversity
        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        boolean hasDigit = Pattern.compile("\\d").matcher(password).find();
        boolean hasSpecial = Pattern.compile("[^A-Za-z0-9]").matcher(password).find();

        int diversity = (hasLower ? 1 : 0) + (hasUpper ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        score += diversity * 5;

        // repetition penalty
        if (Pattern.compile("(.)\\1{2,}").matcher(password).find())
            score -= 5;

        // sequence penalty
        if (containsSequence(password))
            score -= 5;

        // common password penalty
        if (COMMON_PASSWORDS.contains(password.toLowerCase()))
            score -= 10;

        // entropy
        if (entropy > 50) score += 10;
        else if (entropy > 40) score += 5;
        else if (entropy < 25) score -= 5;

        // clamp
        score = Math.max(0, Math.min(score, 100));
        return score;
    }

    private static String classifyScore(int score) {
        if (score < 25) return "Very Weak";
        else if (score < 40) return "Weak";
        else if (score < 60) return "Moderate";
        else if (score < 80) return "Strong";
        else return "Very Strong";
    }

    private static double estimateEntropy(String password) {
        int charSpace = 0;
        if (password.matches(".*[a-z].*")) charSpace += 26;
        if (password.matches(".*[A-Z].*")) charSpace += 26;
        if (password.matches(".*[0-9].*")) charSpace += 10;
        if (password.matches(".*[^A-Za-z0-9].*")) charSpace += 32;
        if (charSpace == 0) return 0;
        return password.length() * (Math.log(charSpace) / Math.log(2));
    }

    private static boolean containsSequence(String s) {
        String lower = s.toLowerCase();
        for (int i = 0; i < lower.length() - 2; i++) {
            char c1 = lower.charAt(i);
            char c2 = lower.charAt(i + 1);
            char c3 = lower.charAt(i + 2);
            if ((c2 == c1 + 1 && c3 == c2 + 1) || (c2 == c1 - 1 && c3 == c2 - 1))
                return true;
        }
        return false;
    }

    public static void main(String[] args) {
        // set a sane look and feel if available
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(MainGUI::new);
    }
}
