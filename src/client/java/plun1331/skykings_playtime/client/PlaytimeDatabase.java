package plun1331.skykings_playtime.client;

import net.hypixel.data.type.ServerType;

import java.sql.*;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Optional;

public class PlaytimeDatabase {
    Connection connection;

    public PlaytimeDatabase(String url) throws SQLException {
        this.connection = DriverManager.getConnection(url);
        Statement stmt = connection.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS playtime (" +
                "start FLOAT NOT NULL," +
                "end FLOAT," +
                "type TEXT NOT NULL," +
                "map TEXT NOT NULL," +
                "published INTEGER NOT NULL DEFAULT 0" +
                ")");
        try {
            stmt.execute("ALTER TABLE playtime ADD COLUMN published INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column name")) {
                throw e;
            }
        }
        stmt.execute("CREATE TABLE IF NOT EXISTS settings (" +
                "key TEXT NOT NULL," +
                "value TEXT" +
                ")");
        stmt.close();
    }

    public Optional<PlaytimeRecord> getCurrentPlaytime() throws SQLException {
        Statement stmt = connection.createStatement();
        ResultSet results = stmt.executeQuery("SELECT * FROM playtime WHERE end IS NULL;");
        if (!results.next()) { // false if there are no rows
            return Optional.empty();
        }
        return Optional.of(new PlaytimeRecord(results));
    }

    public void startPlaytime(ServerType serverType, String map) throws SQLException {
        if (getCurrentPlaytime().isPresent()) {
            throw new IllegalStateException("cannot start new playtime while one is in progress, call endPlaytime first");
        }
        PreparedStatement stmt = connection.prepareStatement("INSERT INTO playtime (start, type, map) VALUES (?, ?, ?)");
        stmt.setTimestamp(1, new Timestamp(Instant.now().getEpochSecond()));
        stmt.setString(2, serverType.getName());
        stmt.setString(3, map);
        stmt.execute();
        SkyKingsPlaytimeClient.LOGGER.info("Started playtime for server type " + serverType.name() + " on map " + map);
    }

    public void endPlaytime() throws SQLException {
        if (getCurrentPlaytime().isEmpty()) {
            throw new IllegalStateException("cannot end playtime with none in progress");
        }
        PreparedStatement stmt = connection.prepareStatement("UPDATE playtime SET end = ? WHERE end IS NULL");
        stmt.setTimestamp(1, new Timestamp(Instant.now().getEpochSecond()));
        stmt.execute();
        SkyKingsPlaytimeClient.LOGGER.info("Ended playtime");
    }

    public void endPlaytimeVolatile() throws SQLException {
        if (getCurrentPlaytime().isEmpty()) {
            throw new IllegalStateException("cannot end playtime with none in progress");
        }
        PreparedStatement stmt = connection.prepareStatement("UPDATE playtime SET end = ? WHERE end IS NULL");
        stmt.setTimestamp(1, new Timestamp(-1));
        stmt.execute();
        SkyKingsPlaytimeClient.LOGGER.info("Ended playtime (volatile)");
    }

    public LinkedList<PlaytimeRecord> getPlaytimeRecords() throws SQLException {
        Statement stmt = connection.createStatement();
        ResultSet results = stmt.executeQuery("SELECT * FROM playtime;");
        LinkedList<PlaytimeRecord> records = new LinkedList<>();
        while (results.next()) {
            records.add(new PlaytimeRecord(results));
        }
        return records;
    }

    public LinkedList<PlaytimeRecord> getUnpublishedPlaytimeRecords() throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM playtime WHERE end IS NOT NULL AND end >= 0 AND published = 0");
        ResultSet results = stmt.executeQuery();
        LinkedList<PlaytimeRecord> records = new LinkedList<>();
        while (results.next()) {
            records.add(new PlaytimeRecord(results));
        }
        return records;
    }

    public void markPlaytimePublished(LinkedList<PlaytimeRecord> records) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(
                "UPDATE playtime SET published = 1 WHERE start = ? AND end = ? AND type = ? AND map = ?");
        for (PlaytimeRecord record : records) {
            stmt.setTimestamp(1, record.start());
            stmt.setTimestamp(2, record.end());
            stmt.setString(3, record.type());
            stmt.setString(4, record.map());
            stmt.addBatch();
        }
        stmt.executeBatch();
    }

    public void setSetting(String key, String value) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)");
        stmt.setString(1, key);
        stmt.setString(2, value);
        stmt.execute();
    }

    public Optional<String> getSetting(String key) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("SELECT value FROM settings WHERE key = ?");
        stmt.setString(1, key);
        ResultSet results = stmt.executeQuery();
        if (!results.next()) {
            return Optional.empty();
        }
        return Optional.of(results.getString("value"));
    }

    public void close() throws SQLException {
        connection.close();
    }
}
