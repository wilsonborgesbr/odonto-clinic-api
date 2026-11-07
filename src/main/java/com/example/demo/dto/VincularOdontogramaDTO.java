package com.example.demo.dto;

import com.example.demo.enums.CondicaoDenteEnum;
import com.example.demo.enums.FaceDenteEnum;
import com.example.demo.util.DenteFdi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class VincularOdontogramaDTO {

    /** Opcional: sem ele vale o odontograma já ligado ao procedimento, depois o mais recente do paciente. */
    private String odontogramaId;

    @NotBlank(message = "O dente é obrigatório")
    @Pattern(regexp = DenteFdi.REGEX, message = DenteFdi.MENSAGEM)
    private String dente;

    /** Pode ser vazia ou ausente. */
    private List<FaceDenteEnum> faces;

    /** Opcional: só muda a condição do dente no odontograma se for informada; nunca é deduzida do procedimento. */
    private CondicaoDenteEnum condicao;
}
