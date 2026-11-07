package com.example.demo.model;

import com.example.demo.enums.FaceDenteEnum;
import com.example.demo.enums.NomeProcedimentoEnum;
import com.example.demo.enums.RegiaoEnum;
import com.example.demo.enums.StatusProcedimentoEnum;
import com.example.demo.enums.TipoPagamentoProcedimentoEnum;
import com.example.demo.util.DenteFdi;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "procedimentos")
public class Procedimento {

    @Id
    private String id;

    @Indexed
    private String clinicaId;

    @Indexed
    private String pacienteId;

    /** Vínculo opcional a um odontograma específico da paciente. */
    @Indexed
    private String odontogramaId;

    private NomeProcedimentoEnum nomeProcedimento;
    private String descricao;

    /** Notação FDI quando preenchido; dados legados podem ter outro formato até serem regravados. */
    @Pattern(regexp = DenteFdi.REGEX_OPCIONAL, message = DenteFdi.MENSAGEM)
    private String dente;

    /** Faces do dente tratadas no procedimento. Documentos antigos não têm o campo: valem como lista vazia. */
    private List<FaceDenteEnum> faces;

    private RegiaoEnum regiao;
    private StatusProcedimentoEnum status;
    private LocalDate dataRealizacao;
    private Double valor;
    private TipoPagamentoProcedimentoEnum tipoPagamento;
    private Integer numeroParcelas;
    private LocalDate dataPrimeiroPagamento;
    private Integer numeroDeSessoes;
    private Integer sessaoAtual;
    private String observacoesTecnicas;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public List<FaceDenteEnum> getFaces() {
        return faces == null ? List.of() : faces;
    }

    /** Sem repetidas nem nulas, preservando a ordem. */
    public void setFaces(List<FaceDenteEnum> faces) {
        this.faces = faces == null
                ? null
                : new ArrayList<>(new LinkedHashSet<>(faces.stream().filter(Objects::nonNull).toList()));
    }

    /** O valor bruto: {@code null} quando o campo não foi informado (diferente de lista vazia). */
    public List<FaceDenteEnum> facesInformadas() {
        return faces;
    }
}
