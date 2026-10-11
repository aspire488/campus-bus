package com.collegebus.model;

import com.collegebus.service.CollegeBusSystem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.HexFormat;
import javax.swing.JFrame;

/**
 * ABSTRACTION + INHERITANCE: common parent of Student and Admin.
 * It cannot be created directly; each subclass decides its role and its dashboard.
 */
public abstract class User implements Displayable {
    private int id;                      // ENCAPSULATION: private fields, public getters/setters
    private String name;
    private String email;
    private String passwordHash;

    protected User(int id, String name, String email, String passwordHash) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /** Implemented differently by Student and Admin (POLYMORPHISM). */
    public abstract String getRole();

    /** Each user type opens its own window - the login screen never needs an if/else on the type. */
    public abstract JFrame openDashboard(CollegeBusSystem system);

    private static final int ITERATIONS = 600_000;                 // OWASP 2023+ guidance for PBKDF2-HMAC-SHA256
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Checks a password against the stored PBKDF2 hash, or an older unsalted SHA-256 one (see needsRehash()). */
    public boolean checkPassword(String plain) {
        String[] p = passwordHash.split("\\$");
        byte[] expected, actual;
        if (p.length == 4 && p[0].equals("pbkdf2")) {
            expected = Base64.getDecoder().decode(p[3]);
            actual = pbkdf2(plain, Base64.getDecoder().decode(p[2]), Integer.parseInt(p[1]));
        } else {
            expected = passwordHash.getBytes(StandardCharsets.US_ASCII);
            actual = sha256Hex(plain).getBytes(StandardCharsets.US_ASCII);
        }
        return MessageDigest.isEqual(expected, actual);             // constant-time comparison
    }

    /** True for an old unsalted SHA-256 hash or a PBKDF2 hash with fewer iterations: upgraded at the next successful sign-in. */
    public boolean needsRehash() {
        String[] p = passwordHash.split("\\$");
        return !(p.length == 4 && p[0].equals("pbkdf2") && Integer.parseInt(p[1]) >= ITERATIONS);
    }

    /** Passwords are never stored as plain text: "pbkdf2$iterations$salt$hash" (PBKDF2-HMAC-SHA256, random 16-byte salt). */
    /** Rule for every new password: 8-64 characters with upper case, lower case, a digit and a symbol, and not an easily guessed one. */
    public static void checkNewPassword(String pw) {
        if (pw.length() < 8 || pw.length() > 64) throw new IllegalArgumentException("Password needs 8-64 characters.");
        if (!pw.matches(".*[A-Z].*") || !pw.matches(".*[a-z].*") || !pw.matches(".*\\d.*") || !pw.matches(".*[^A-Za-z0-9\\s].*"))
            throw new IllegalArgumentException("Password needs an upper-case letter, a lower-case letter, a digit and a symbol (e.g. ! @ # -).");
        String plain = pw.toLowerCase().replaceAll("[^a-z]", "");
        if (pw.chars().distinct().count() < 5 || java.util.List.of("password", "qwerty", "admin", "welcome", "letmein", "abc", "iloveyou", "college", "student").stream().anyMatch(plain::startsWith)
                || pw.contains("1234") || pw.contains("0000"))
            throw new IllegalArgumentException("That password is too easy to guess. Avoid common words and sequences like 1234.");
    }

    public static String hash(String plain) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(pbkdf2(plain, salt, ITERATIONS));
    }

    private static byte[] pbkdf2(String plain, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(plain.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(String plain) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String getFirstName() { return name.split(" ")[0]; }

    @Override
    public String displayDetails() { return getRole() + ": " + name + " <" + email + ">"; }

    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
