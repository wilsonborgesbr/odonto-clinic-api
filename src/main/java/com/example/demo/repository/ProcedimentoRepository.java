package com.example.demo.repository;

import com.example.demo.enums.NomeProcedimentoEnum;
import com.example.demo.enums.StatusProcedimentoEnum;
import com.example.demo.model.Procedimento;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProcedimentoRepository extends MongoRepository<Procedimento, String> {

    List<Procedimento> findByClinicaId(String clinicaId);

    List<Procedimento> findByClinicaIdAndPacienteId(String clinicaId, String pacienteId);

    List<Procedimento> findByClinicaIdAndPacienteIdAndStatus(String clinicaId, String pacienteId, StatusProcedimentoEnum status);

    java.util.Optional<Procedimento> findByIdAndClinicaId(String id, String clinicaId);
}
