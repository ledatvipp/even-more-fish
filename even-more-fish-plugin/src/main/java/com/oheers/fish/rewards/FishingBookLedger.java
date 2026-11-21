package com.oheers.fish.rewards;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns only supplemental books, never the native fish or its delivery. */
public final class FishingBookLedger {
    public record Entry(String receipt, UUID owner, String state, String enchant, int level, byte[] book) { }
    public record Choice(String enchant, int level) { }
    private FishingBookLedger() { }

    public static void initialize(Connection c) throws SQLException {
        try (var s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS fishing_books (receipt TEXT PRIMARY KEY,owner TEXT NOT NULL,state TEXT NOT NULL,enchant TEXT NOT NULL,level INTEGER NOT NULL,created INTEGER NOT NULL,automatic INTEGER NOT NULL,book BLOB)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS fishing_books_owner ON fishing_books(owner,created,state)");
        }
    }

    public static Entry find(Connection c, String receipt, UUID owner) throws SQLException {
        try (var s = c.prepareStatement("SELECT * FROM fishing_books WHERE receipt=?")) {
            s.setString(1, receipt);
            try (var row = s.executeQuery()) {
                if (!row.next()) return null;
                if (!owner.toString().equals(row.getString("owner"))) throw new SQLException("Fishing receipt owner mismatch");
                return new Entry(receipt, owner, row.getString("state"), row.getString("enchant"), row.getInt("level"), row.getBytes("book"));
            }
        }
    }

    /** Public catch identity is a replay key only; never feed it into the private lottery. */
    public static Entry decide(Connection c, String receipt, UUID owner, boolean automatic, long now,
                               long cooldownMillis, int hourlyCap, java.util.function.Supplier<Choice> draw) throws SQLException {
        Entry prior = find(c, receipt, owner);
        if (prior != null) return prior;
        Choice choice = draw.get();
        return admit(c, receipt, owner, automatic, choice.enchant(), choice.level(), now, cooldownMillis, hourlyCap);
    }

    /** First decision, cooldown and hourly quota commit together, across manual/auto and restart. */
    public static Entry admit(Connection c, String receipt, UUID owner, boolean automatic, String enchant,
                              int level, long now, long cooldownMillis, int hourlyCap) throws SQLException {
        if (receipt == null || !receipt.startsWith("catch:") || receipt.length() > 100 || owner == null
                || enchant == null || !enchant.isEmpty() && level < 1 || cooldownMillis < 0 || hourlyCap < 1)
            throw new IllegalArgumentException("Invalid fishing reward admission");
        c.setAutoCommit(false);
        try {
            Entry prior = find(c, receipt, owner);
            if (prior != null) { c.commit(); return prior; }
            String state = enchant.isEmpty() ? "MISS" : "PLANNED";
            if (!enchant.isEmpty()) {
                try (var s = c.prepareStatement("SELECT COUNT(*),MAX(created) FROM fishing_books WHERE owner=? AND state IN ('PLANNED','READY','CLAIMING','CLAIMED') AND created>=?")) {
                    s.setString(1, owner.toString()); s.setLong(2, now - Math.max(3600000L, cooldownMillis));
                    try (var rows = s.executeQuery()) {
                        rows.next();
                        if (rows.getInt(1) >= hourlyCap || rows.getObject(2) != null && now - rows.getLong(2) < cooldownMillis) state = "LIMITED";
                    }
                }
            }
            try (var s = c.prepareStatement("INSERT INTO fishing_books(receipt,owner,state,enchant,level,created,automatic) VALUES(?,?,?,?,?,?,?)")) {
                s.setString(1, receipt); s.setString(2, owner.toString()); s.setString(3, state);
                s.setString(4, enchant); s.setInt(5, level); s.setLong(6, now); s.setInt(7, automatic ? 1 : 0); s.executeUpdate();
            }
            c.commit(); return find(c, receipt, owner);
        } catch (SQLException | RuntimeException error) { c.rollback(); throw error; }
        finally { c.setAutoCommit(true); }
    }

    public static Entry materialize(Connection c, Entry entry, byte[] book) throws SQLException {
        if (book == null || book.length == 0 || book.length > 1048576) throw new IllegalArgumentException("Invalid native AE book");
        try (var s = c.prepareStatement("UPDATE fishing_books SET state='READY',book=? WHERE receipt=? AND owner=? AND state='PLANNED' AND book IS NULL")) {
            s.setBytes(1, book); s.setString(2, entry.receipt()); s.setString(3, entry.owner().toString()); s.executeUpdate();
        }
        return find(c, entry.receipt(), entry.owner());
    }

    public static boolean transition(Connection c, Entry entry, String from, String to) throws SQLException {
        if (!(from.equals("READY") && to.equals("CLAIMING") || from.equals("CLAIMING") && (to.equals("CLAIMED") || to.equals("READY"))))
            throw new IllegalArgumentException("Invalid fishing book transition");
        try (var s = c.prepareStatement("UPDATE fishing_books SET state=? WHERE receipt=? AND owner=? AND state=?")) {
            s.setString(1, to); s.setString(2, entry.receipt()); s.setString(3, entry.owner().toString()); s.setString(4, from);
            return s.executeUpdate() == 1;
        }
    }

    public static List<Entry> pending(Connection c, UUID owner) throws SQLException {
        var receipts = new ArrayList<String>();
        try (var s = c.prepareStatement("SELECT receipt FROM fishing_books WHERE owner=? AND state IN ('READY','PLANNED','CLAIMING') ORDER BY CASE state WHEN 'READY' THEN 0 WHEN 'PLANNED' THEN 1 ELSE 2 END,created LIMIT 64")) {
            s.setString(1, owner.toString());
            try (var rows = s.executeQuery()) { while (rows.next()) receipts.add(rows.getString(1)); }
        }
        var result = new ArrayList<Entry>();
        for (String receipt : receipts) result.add(find(c, receipt, owner));
        return List.copyOf(result);
    }
}
