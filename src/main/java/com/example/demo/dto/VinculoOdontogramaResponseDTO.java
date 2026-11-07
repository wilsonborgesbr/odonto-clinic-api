package com.example.demo.dto;

import com.example.demo.model.DenteStatus;
import com.example.demo.model.Odontograma;
import com.example.demo.model.Procedimento;

/**
 * Resultado do vínculo: o procedimento já atualizado, o odontograma usado, o dente como ficou nele e se o
 * odontograma foi criado nesta chamada (paciente sem nenhum).
 */
public record VinculoOdontogramaResponseDTO(Procedimento procedimento, Odontograma odontograma, DenteStatus dente,
                                            boolean odontogramaCriado) {}
