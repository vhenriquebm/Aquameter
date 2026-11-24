package br.com.aquameter.repository;

import br.com.aquameter.domain.Propriedade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class PropriedadeRepository {

    private static final RowMapper<Propriedade> MAPPER = (rs, i) -> new Propriedade(
            rs.getLong("id"),
            rs.getString("nome"),
            rs.getString("produtor"),
            rs.getString("municipio"),
            rs.getString("uf"),
            rs.getDouble("area_ha"),
            rs.getString("cultura_principal"),
            rs.getDouble("tarifa_agua_m3"),
            rs.getDouble("tarifa_energia_kwh"));

    private final JdbcTemplate jdbc;

    public PropriedadeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Propriedade> listar() {
        return jdbc.query("SELECT * FROM propriedade ORDER BY id", MAPPER);
    }

    public Optional<Propriedade> buscar(long id) {
        return jdbc.query("SELECT * FROM propriedade WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public long contar() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM propriedade", Long.class);
        return n == null ? 0 : n;
    }

    public long inserir(Propriedade p) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO propriedade (nome, produtor, municipio, uf, area_ha, cultura_principal,
                                             tarifa_agua_m3, tarifa_energia_kwh)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", new String[]{"ID"});
            ps.setString(1, p.nome());
            ps.setString(2, p.produtor());
            ps.setString(3, p.municipio());
            ps.setString(4, p.uf().toUpperCase());
            ps.setDouble(5, p.areaHa());
            ps.setString(6, p.culturaPrincipal());
            ps.setDouble(7, p.tarifaAguaM3());
            ps.setDouble(8, p.tarifaEnergiaKwh());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }
}
