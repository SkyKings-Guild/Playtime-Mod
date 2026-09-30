package plun1331.skykings_playtime.client;

import net.hypixel.data.type.ServerType;

import java.sql.*;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Optional;

public class PlaytimeDatabase {
    Connection connection;
    int lastRowId = -1;

    public PlaytimeDatabase(String url) throws SQLException {
        this.connection = DriverManager.getConnection(url);
        Statement stmt = connection.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS playtime (" +
                "rowid INT NOT NULL," +
                "start FLOAT NOT NULL," +
                "end FLOAT," +
                "type TEXT NOT NULL," +
                "map TEXT NOT NULL," +
                "published INTEGER NOT NULL DEFAULT 0" +
                ")");
        stmt.execute("CREATE TABLE IF NOT EXISTS settings (" +
                "key TEXT NOT NULL," +
                "value TEXT" +
                ")");
        stmt.close();
        SkyKingsPlaytimeClient.LOGGER.info("Initialized database at " + url);
    }

    public Optional<PlaytimeRecord> getCurrentPlaytime() throws SQLException {
        Statement stmt = connection.createStatement();
        ResultSet results = stmt.executeQuery("SELECT * FROM playtime WHERE end IS NULL;");
        if (!results.next()) { // false if there are no rows
            return Optional.empty();
        }
        return Optional.of(new PlaytimeRecord(results));
    }

    private void getLastRecordId() throws SQLException {
        Statement stmt = connection.createStatement();
        ResultSet results = stmt.executeQuery("SELECT rowid FROM playtime ORDER BY rowid DESC LIMIT 1;");
        if (!results.next()) {
            lastRowId = -1;
            return;
        }
        lastRowId = results.getInt("rowid");
    }

    public void startPlaytime(ServerType serverType, String map) throws SQLException {
        if (getCurrentPlaytime().isPresent()) {
            throw new IllegalStateException("cannot start new playtime while one is in progress, call endPlaytime first");
        }
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("map cannot be null or empty");
        }
        if (lastRowId == -1) {
            getLastRecordId();
        }
        PreparedStatement stmt = connection.prepareStatement("INSERT INTO playtime (rowId, start, type, map) VALUES (?, ?, ?, ?)");
        stmt.setInt(1, ++lastRowId);
        stmt.setTimestamp(2, new Timestamp(Instant.now().getEpochSecond()));
        stmt.setString(3, serverType.getName());
        stmt.setString(4, map);
        stmt.execute();
        SkyKingsPlaytimeClient.LOGGER.info("Started playtime for server type {} on map {}", serverType.name(), map);
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

    public void splitCurrentPlaytime(ServerType serverType, String map) throws SQLException {
        if (getCurrentPlaytime().isEmpty()) {
            throw new IllegalStateException("cannot split playtime with none in progress");
        }
        endPlaytime();
        startPlaytime(serverType, map);
        SkyKingsPlaytimeClient.LOGGER.info("Split playtime from {} to {}", getCurrentPlaytime().get().type(), serverType.name());
    }

    public void deleteCurrentPlaytime() throws SQLException {
        if (getCurrentPlaytime().isEmpty()) {
            throw new IllegalStateException("cannot delete current playtime with none in progress");
        }
        PreparedStatement stmt = connection.prepareStatement("DELETE FROM playtime WHERE end IS NULL");
        stmt.execute();
        SkyKingsPlaytimeClient.LOGGER.info("Deleted current playtime");
    }

//    public LinkedList<PlaytimeRecord> getPlaytimeRecords() throws SQLException {
//        Statement stmt = connection.createStatement();
//        ResultSet results = stmt.executeQuery("SELECT * FROM playtime;");
//        LinkedList<PlaytimeRecord> records = new LinkedList<>();
//        while (results.next()) {
//            records.add(new PlaytimeRecord(results));
//        }
//        return records;
//    }

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
        SkyKingsPlaytimeClient.LOGGER.info("Marking playtime records as published: {}", records);
        PreparedStatement stmt = connection.prepareStatement(
                "UPDATE playtime SET published = 1 WHERE rowId = ?");
        for (PlaytimeRecord record : records) {
            stmt.setInt(1, record.rowId());
            stmt.addBatch();
        }
        stmt.executeBatch();
    }

    public void setSetting(String key, String value) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("DELETE FROM settings WHERE key = ?");
        stmt.setString(1, key);
        stmt.execute();
        PreparedStatement stmt2 = connection.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)");
        stmt2.setString(1, key);
        stmt2.setString(2, value);
        stmt2.execute();
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
        SkyKingsPlaytimeClient.LOGGER.info("Closed database connection");
    }
}
