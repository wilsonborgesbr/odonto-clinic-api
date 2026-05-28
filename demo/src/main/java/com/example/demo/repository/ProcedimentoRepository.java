package com.example.demo.repository;

import com.example.demo.enums.NomeProcedimentoEnum;
import com.example.demo.enums.StatusProcedimentoEnum;
import com.example.demo.model.Procedimento;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProcedimentoRepository extends MongoRepository<Procedimento, String> {

    List<Procedimento> findByPacienteId(String pacienteId);

    List<Procedimento> findByPacienteIdAndStatus(String pacienteId, StatusProcedimentoEnum status);

    List<Procedimento> findByNomeProcedimento(NomeProcedimentoEnum nomeProcedimento);
}
