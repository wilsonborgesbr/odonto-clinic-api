package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ValidarCodigoRecuperacaoDTO {

    @NotBlank(message = "O email não pode estar vazio")
    @Email(message = "O formato do email deve ser válido")
    private String email;

    private String clinicaCodigo;

    @NotBlank(message = "O código é obrigatório")
    @Pattern(regexp = "^\\d{6}$", message = "O código deve conter exatamente 6 dígitos numéricos")
    private String codigo;

    @Override
    public String toString() {
        return "ValidarCodigoRecuperacaoDTO[clinicaCodigo=" + clinicaCodigo + "]";
    }
}
