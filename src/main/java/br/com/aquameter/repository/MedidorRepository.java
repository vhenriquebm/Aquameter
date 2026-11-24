package br.com.aquameter.repository;

import br.com.aquameter.domain.Medidor;
import br.com.aquameter.domain.TipoMedidor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class MedidorRepository {

    private static final RowMapper<Medidor> MAPPER = (rs, i) -> new Medidor(
            rs.getLong("id"),
            rs.getLong("propriedade_id"),
            rs.getString("nome"),
            TipoMedidor.valueOf(rs.getString("tipo")),
            rs.getString("setor"),
            rs.getString("unidade"),
            rs.getDouble("limite_diario"),
            rs.getBoolean("ativo"));

    private final JdbcTemplate jdbc;

    public MedidorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Medidor> listarPorPropriedade(long propriedadeId) {
        return jdbc.query("SELECT * FROM medidor WHERE propriedade_id = ? ORDER BY tipo, id", MAPPER, propriedadeId);
    }

    public List<Medidor> listarAtivos() {
        return jdbc.query("SELECT * FROM medidor WHERE ativo = TRUE ORDER BY id", MAPPER);
    }

    public Optional<Medidor> buscar(long id) {
        return jdbc.query("SELECT * FROM medidor WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public long inserir(Medidor m) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO medidor (propriedade_id, nome, tipo, setor, unidade, limite_diario, ativo)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE)""", new String[]{"ID"});
            ps.setLong(1, m.propriedadeId());
            ps.setString(2, m.nome());
            ps.setString(3, m.tipo().name());
            ps.setString(4, m.setor());
            ps.setString(5, m.tipo().unidade());
            ps.setDouble(6, m.limiteDiario());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }
}
