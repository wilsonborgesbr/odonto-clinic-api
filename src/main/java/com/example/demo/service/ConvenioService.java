package com.example.demo.service;

import com.example.demo.model.Convenio;
import com.example.demo.repository.ConvenioRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConvenioService {

    @Autowired
    private ConvenioRepository convenioRepository;

    public Convenio criar(Convenio convenio) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        if (convenio.getCnpj() != null && !convenio.getCnpj().isBlank()
                && convenioRepository.findByCnpjAndClinicaId(convenio.getCnpj(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ já cadastrado nesta clínica.");
        }

        convenio.setClinicaId(clinicaId);
        convenio.setAtivo(true);
        convenio.setCreatedAt(LocalDateTime.now());
        convenio.setUpdatedAt(LocalDateTime.now());

        return convenioRepository.save(convenio);
    }

    public List<Convenio> listarTodos() {
        return convenioRepository.findByClinicaId(AuthContextHelper.currentClinicaId());
    }

    public Convenio buscarPorId(String id) {
        return convenioRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .filter(Convenio::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convênio não encontrado."));
    }

    public Convenio atualizar(String id, Convenio dadosAtualizados) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        Convenio existente = convenioRepository.findByIdAndClinicaId(id, clinicaId)
                .filter(Convenio::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convênio não encontrado."));

        if (dadosAtualizados.getCnpj() != null && !dadosAtualizados.getCnpj().isBlank()
                && !dadosAtualizados.getCnpj().equals(existente.getCnpj())
                && convenioRepository.findByCnpjAndClinicaId(dadosAtualizados.getCnpj(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ já cadastrado nesta clínica.");
        }

        existente.setNome(dadosAtualizados.getNome());
        existente.setCnpj(dadosAtualizados.getCnpj());
        existente.setRegistroANS(dadosAtualizados.getRegistroANS());
        existente.setTelefone(dadosAtualizados.getTelefone());
        existente.setEmail(dadosAtualizados.getEmail());
        existente.setWebsite(dadosAtualizados.getWebsite());
        existente.setNomeContato(dadosAtualizados.getNomeContato());
        existente.setEndereco(dadosAtualizados.getEndereco());
        existente.setTabelaDePrecos(dadosAtualizados.getTabelaDePrecos());
        existente.setCoberturas(dadosAtualizados.getCoberturas());
        existente.setPercentualCobertura(dadosAtualizados.getPercentualCobertura());
        existente.setCarenciaDias(dadosAtualizados.getCarenciaDias());
        existente.setDataInicioContrato(dadosAtualizados.getDataInicioContrato());
        existente.setDataFimContrato(dadosAtualizados.getDataFimContrato());
        existente.setLimiteConsultasMes(dadosAtualizados.getLimiteConsultasMes());
        existente.setObservacoes(dadosAtualizados.getObservacoes());
        existente.setUpdatedAt(LocalDateTime.now());

        return convenioRepository.save(existente);
    }

    public void deletar(String id) {
        Convenio c = convenioRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .filter(Convenio::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convênio não encontrado."));
        c.setAtivo(false);
        c.setUpdatedAt(LocalDateTime.now());
        convenioRepository.save(c);
    }

    public Convenio reativar(String id) {
        Convenio c = convenioRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convênio não encontrado."));
        c.setAtivo(true);
        c.setUpdatedAt(LocalDateTime.now());
        return convenioRepository.save(c);
    }
}
