package com.example.demo.model;

import com.example.demo.enums.TipoDocumentoEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "documentos")
public class Documento {

    @Id
    private String id;

    @Indexed
    private String pacienteId;

    private TipoDocumentoEnum tipo;
    private String urlArquivo;
    private String descricao;

    private LocalDateTime dataUpload;
}
