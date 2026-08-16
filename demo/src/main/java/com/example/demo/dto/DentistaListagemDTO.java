package com.example.demo.dto;

import java.util.List;
import com.example.demo.enums.EspecialidadeEnum;
import com.example.demo.enums.SexoEnum;
import com.example.demo.model.Dentista;
import com.example.demo.model.Endereco;

public record DentistaListagemDTO(
        String id,
        String nomeCompleto,
        String cro,
        List<EspecialidadeEnum> especialidades,
        String email,
        String telefoneCelular,
        Boolean ativo,
        SexoEnum sexo,
        Endereco endereco
) {
    public DentistaListagemDTO(Dentista dentista) {
        this(
                dentista.getId(),
                dentista.getNomeCompleto(),
                dentista.getCro(),
                dentista.getEspecialidades(),
                dentista.getEmail(),
                dentista.getTelefoneCelular(),
                dentista.getAtivo(),
                dentista.getSexo(),
                dentista.getEndereco()
        );
    }
}
