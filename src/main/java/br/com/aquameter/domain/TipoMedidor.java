package br.com.aquameter.domain;

public enum TipoMedidor {
    AGUA("m³"),
    ENERGIA("kWh");

    private final String unidade;

    TipoMedidor(String unidade) {
        this.unidade = unidade;
    }

    public String unidade() {
        return unidade;
    }
}
