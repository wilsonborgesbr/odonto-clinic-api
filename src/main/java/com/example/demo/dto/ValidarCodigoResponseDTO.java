package com.example.demo.dto;

public record ValidarCodigoResponseDTO(boolean valido, String ticket, String nomeUsuario, String clinicaNome,
                                       String clinicaCodigo) {

    @Override
    public String toString() {
        return "ValidarCodigoResponseDTO[valido=" + valido + ", clinicaCodigo=" + clinicaCodigo + "]";
    }
}
