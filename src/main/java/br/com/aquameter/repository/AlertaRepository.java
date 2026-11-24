package br.com.aquameter.repository;

import br.com.aquameter.domain.Alerta;
import br.com.aquameter.domain.TipoAlerta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static br.com.aquameter.repository.Jdbc.nullableDouble;
import static br.com.aquameter.repository.Jdbc.ts;

@Repository
public class AlertaRepository {

    private static final RowMapper<Alerta> MAPPER = (rs, i) -> new Alerta(
            rs.getLong("id"),
            rs.getLong("medidor_id"),
            rs.getString("medidor_nome"),
            rs.getString("setor"),
            rs.getTimestamp("data_hora").toLocalDateTime(),
            TipoAlerta.valueOf(rs.getString("tipo")),
            rs.getString("mensagem"),
            nullableDouble(rs, "valor"),
            nullableDouble(rs, "limite"),
            rs.getBoolean("resolvido"));

    private static final String SELECT = """
            SELECT a.*, m.nome AS medidor_nome, m.setor
              FROM alerta a JOIN medidor m ON m.id = a.medidor_id
            """;

    private final JdbcTemplate jdbc;

    public AlertaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void inserir(long medidorId, LocalDateTime dataHora, TipoAlerta tipo, String mensagem,
                        Double valor, Double limite, boolean resolvido) {
        jdbc.update("""
                INSERT INTO alerta (medidor_id, data_hora, tipo, mensagem, valor, limite, resolvido)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                medidorId, ts(dataHora), tipo.name(), mensagem, valor, limite, resolvido);
    }

    /** Já existe alerta deste tipo para o medidor no intervalo? Evita alertas duplicados no mesmo dia. */
    public boolean existe(long medidorId, TipoAlerta tipo, LocalDateTime inicio, LocalDateTime fim) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM alerta
                 WHERE medidor_id = ? AND tipo = ? AND data_hora >= ? AND data_hora < ?""",
                Long.class, medidorId, tipo.name(), ts(inicio), ts(fim));
        return n != null && n > 0;
    }

    public List<Alerta> listarPorPropriedade(long propriedadeId, boolean somenteAbertos) {
        String sql = SELECT + " WHERE m.propriedade_id = ?"
                + (somenteAbertos ? " AND a.resolvido = FALSE" : "")
                + " ORDER BY a.resolvido, a.data_hora DESC FETCH FIRST 50 ROWS ONLY";
        return jdbc.query(sql, MAPPER, propriedadeId);
    }

    public int contarAbertos(long propriedadeId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM alerta a JOIN medidor m ON m.id = a.medidor_id
                 WHERE m.propriedade_id = ? AND a.resolvido = FALSE""", Integer.class, propriedadeId);
        return n == null ? 0 : n;
    }

    public boolean resolver(long alertaId) {
        return jdbc.update("UPDATE alerta SET resolvido = TRUE WHERE id = ?", alertaId) > 0;
    }
}
