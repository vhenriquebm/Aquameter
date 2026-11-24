package br.com.aquameter.repository;

import br.com.aquameter.domain.CategoriaInsumo;
import br.com.aquameter.domain.InsumoAplicacao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.com.aquameter.repository.Jdbc.date;

@Repository
public class InsumoRepository {

    private static final RowMapper<InsumoAplicacao> MAPPER = (rs, i) -> new InsumoAplicacao(
            rs.getLong("id"),
            rs.getLong("propriedade_id"),
            rs.getDate("data_aplicacao").toLocalDate(),
            rs.getString("insumo"),
            CategoriaInsumo.valueOf(rs.getString("categoria")),
            rs.getDouble("quantidade"),
            rs.getString("unidade"),
            rs.getDouble("custo_total"),
            rs.getString("setor"));

    private final JdbcTemplate jdbc;

    public InsumoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InsumoAplicacao> listar(long propriedadeId) {
        return jdbc.query("""
                SELECT * FROM insumo_aplicacao WHERE propriedade_id = ?
                 ORDER BY data_aplicacao DESC, id DESC FETCH FIRST 100 ROWS ONLY""", MAPPER, propriedadeId);
    }

    public void inserir(long propriedadeId, InsumoAplicacao i) {
        jdbc.update("""
                INSERT INTO insumo_aplicacao (propriedade_id, data_aplicacao, insumo, categoria,
                                              quantidade, unidade, custo_total, setor)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                propriedadeId, date(i.dataAplicacao()), i.insumo(), i.categoria().name(),
                i.quantidade(), i.unidade(), i.custoTotal(), i.setor());
    }

    public double custoTotal(long propriedadeId, LocalDate inicio, LocalDate fimExclusivo) {
        Double v = jdbc.queryForObject("""
                SELECT NVL(SUM(custo_total), 0) FROM insumo_aplicacao
                 WHERE propriedade_id = ? AND data_aplicacao >= ? AND data_aplicacao < ?""",
                Double.class, propriedadeId, date(inicio), date(fimExclusivo));
        return v == null ? 0 : v;
    }

    /** Custo por categoria no intervalo, ordenado do maior para o menor. */
    public Map<String, Double> custoPorCategoria(long propriedadeId, LocalDate inicio, LocalDate fimExclusivo) {
        Map<String, Double> mapa = new LinkedHashMap<>();
        jdbc.query("""
                SELECT categoria, SUM(custo_total) AS total FROM insumo_aplicacao
                 WHERE propriedade_id = ? AND data_aplicacao >= ? AND data_aplicacao < ?
                 GROUP BY categoria ORDER BY total DESC""",
                rs -> {
                    mapa.put(rs.getString("categoria"), rs.getDouble("total"));
                },
                propriedadeId, date(inicio), date(fimExclusivo));
        return mapa;
    }
}
