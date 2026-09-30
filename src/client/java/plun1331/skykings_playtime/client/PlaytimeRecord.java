package plun1331.skykings_playtime.client;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public record PlaytimeRecord(Timestamp start, Timestamp end, String type, String map) {
    public PlaytimeRecord(ResultSet resultSet) throws SQLException {
        this(resultSet.getTimestamp("start"), resultSet.getTimestamp("end"), resultSet.getString("type"), resultSet.getString("map"));
    }
}
