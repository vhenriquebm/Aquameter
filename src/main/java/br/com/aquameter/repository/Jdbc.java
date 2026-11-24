package br.com.aquameter.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Conversões utilitárias entre java.time e os tipos JDBC usados com o Oracle. */
final class Jdbc {

    private Jdbc() {
    }

    static Timestamp ts(LocalDateTime dt) {
        return Timestamp.valueOf(dt);
    }

    static Date date(LocalDate d) {
        return Date.valueOf(d);
    }

    static Double nullableDouble(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}
