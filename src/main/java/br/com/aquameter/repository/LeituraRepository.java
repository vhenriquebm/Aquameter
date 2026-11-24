package br.com.aquameter.repository;

import br.com.aquameter.domain.TipoMedidor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static br.com.aquameter.repository.Jdbc.date;
import static br.com.aquameter.repository.Jdbc.ts;

@Repository
public class LeituraRepository {

    /** Linha da série diária: consumo de água e energia por dia, com média móvel de 7 dias da água. */
    public record PontoDiario(LocalDate dia, double agua, double energia, double aguaMediaMovel7d) {
    }

    /** Registro em lote usado pela carga de demonstração. */
    public record LeituraLote(long medidorId, LocalDateTime dataHora, double valor) {
    }

    private final JdbcTemplate jdbc;

    public LeituraRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void inserir(long medidorId, LocalDateTime dataHora, double valor) {
        jdbc.update("INSERT INTO leitura (medidor_id, data_hora, valor) VALUES (?, ?, ?)",
                medidorId, ts(dataHora), valor);
    }

    public void inserirLote(List<LeituraLote> leituras) {
        List<Object[]> args = new ArrayList<>(leituras.size());
        for (LeituraLote l : leituras) {
            args.add(new Object[]{l.medidorId(), ts(l.dataHora()), l.valor()});
        }
        jdbc.batchUpdate("INSERT INTO leitura (medidor_id, data_hora, valor) VALUES (?, ?, ?)", args);
    }

    /** Soma o consumo de um medidor no intervalo [inicio, fim). */
    public double somaMedidor(long medidorId, LocalDateTime inicio, LocalDateTime fim) {
        Double v = jdbc.queryForObject("""
                SELECT NVL(SUM(valor), 0) FROM leitura
                 WHERE medidor_id = ? AND data_hora >= ? AND data_hora < ?""",
                Double.class, medidorId, ts(inicio), ts(fim));
        return v == null ? 0 : v;
    }

    /** Soma o consumo de todos os medidores de um tipo na propriedade no intervalo [inicio, fim). */
    public double somaPorTipo(long propriedadeId, TipoMedidor tipo, LocalDateTime inicio, LocalDateTime fim) {
        Double v = jdbc.queryForObject("""
                SELECT NVL(SUM(l.valor), 0)
                  FROM leitura l JOIN medidor m ON m.id = l.medidor_id
                 WHERE m.propriedade_id = ? AND m.tipo = ? AND l.data_hora >= ? AND l.data_hora < ?""",
                Double.class, propriedadeId, tipo.name(), ts(inicio), ts(fim));
        return v == null ? 0 : v;
    }

    /** Consumo de energia no horário de ponta (18h às 20h59) no intervalo. */
    public double somaEnergiaPonta(long propriedadeId, LocalDateTime inicio, LocalDateTime fim) {
        Double v = jdbc.queryForObject("""
                SELECT NVL(SUM(l.valor), 0)
                  FROM leitura l JOIN medidor m ON m.id = l.medidor_id
                 WHERE m.propriedade_id = ? AND m.tipo = 'ENERGIA'
                   AND l.data_hora >= ? AND l.data_hora < ?
                   AND EXTRACT(HOUR FROM l.data_hora) BETWEEN 18 AND 20""",
                Double.class, propriedadeId, ts(inicio), ts(fim));
        return v == null ? 0 : v;
    }

    /** Média por hora do consumo de madrugada (0h às 4h59) de um medidor no intervalo. */
    public double mediaHorariaMadrugada(long medidorId, LocalDateTime inicio, LocalDateTime fim) {
        Double v = jdbc.queryForObject("""
                SELECT NVL(AVG(valor), 0) FROM leitura
                 WHERE medidor_id = ? AND data_hora >= ? AND data_hora < ?
                   AND EXTRACT(HOUR FROM data_hora) BETWEEN 0 AND 4""",
                Double.class, medidorId, ts(inicio), ts(fim));
        return v == null ? 0 : v;
    }

    /** Consumo do dia de hoje por medidor da propriedade (medidorId -> total). */
    public Map<Long, Double> consumoPorMedidor(long propriedadeId, LocalDateTime inicio, LocalDateTime fim) {
        Map<Long, Double> mapa = new HashMap<>();
        jdbc.query("""
                SELECT m.id, NVL(SUM(l.valor), 0) AS total
                  FROM medidor m
                  LEFT JOIN leitura l ON l.medidor_id = m.id AND l.data_hora >= ? AND l.data_hora < ?
                 WHERE m.propriedade_id = ?
                 GROUP BY m.id""",
                rs -> {
                    mapa.put(rs.getLong("id"), rs.getDouble("total"));
                },
                ts(inicio), ts(fim), propriedadeId);
        return mapa;
    }

    /**
     * Série diária a partir da view vw_consumo_diario, com função analítica do Oracle
     * (AVG ... OVER) para a média móvel de 7 dias do consumo de água.
     */
    public List<PontoDiario> serieDiaria(long propriedadeId, LocalDate desde) {
        return jdbc.query("""
                SELECT dia, agua, energia,
                       AVG(agua) OVER (ORDER BY dia ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS agua_mm7
                  FROM (
                        SELECT v.dia,
                               SUM(CASE WHEN m.tipo = 'AGUA' THEN v.total ELSE 0 END)    AS agua,
                               SUM(CASE WHEN m.tipo = 'ENERGIA' THEN v.total ELSE 0 END) AS energia
                          FROM vw_consumo_diario v
                          JOIN medidor m ON m.id = v.medidor_id
                         WHERE m.propriedade_id = ? AND v.dia >= ?
                         GROUP BY v.dia
                       )
                 ORDER BY dia""",
                (rs, i) -> new PontoDiario(
                        rs.getDate("dia").toLocalDate(),
                        rs.getDouble("agua"),
                        rs.getDouble("energia"),
                        rs.getDouble("agua_mm7")),
                propriedadeId, date(desde));
    }

    /** Totais diários de um medidor (usado para gerar alertas históricos na carga inicial). */
    public Map<LocalDate, Double> totaisDiarios(long medidorId, LocalDate desde) {
        Map<LocalDate, Double> mapa = new HashMap<>();
        jdbc.query("SELECT dia, total FROM vw_consumo_diario WHERE medidor_id = ? AND dia >= ?",
                rs -> {
                    mapa.put(rs.getDate("dia").toLocalDate(), rs.getDouble("total"));
                },
                medidorId, date(desde));
        return mapa;
    }
}
