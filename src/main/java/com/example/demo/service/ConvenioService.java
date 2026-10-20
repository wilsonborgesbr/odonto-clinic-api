package com.example.demo.service;

import com.example.demo.model.Convenio;
import com.example.demo.repository.ConvenioRepository;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.util.FormatadorTexto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
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
        FormatadorTexto.normalizar(convenio);
        String clinicaId = AuthContextHelper.currentClinicaId();
        if (cnpjEmUso(convenio.getCnpj(), null, clinicaId)) {
            throw cnpjDuplicado();
        }

        convenio.setClinicaId(clinicaId);
        convenio.setAtivo(true);
        convenio.setCreatedAt(LocalDateTime.now());
        convenio.setUpdatedAt(LocalDateTime.now());

        return salvar(convenio);
    }

    private boolean cnpjEmUso(String cnpj, String cnpjAtual, String clinicaId) {
        return cnpj != null && !cnpj.isBlank() && !cnpj.equals(cnpjAtual)
                && convenioRepository.findByCnpjAndClinicaId(cnpj, clinicaId).isPresent();
    }

    private ResponseStatusException cnpjDuplicado() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ já cadastrado nesta clínica.");
    }

    private Convenio salvar(Convenio convenio) {
        try {
            return convenioRepository.save(convenio);
        } catch (DuplicateKeyException e) {
            throw cnpjDuplicado();
        }
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
        FormatadorTexto.normalizar(dadosAtualizados);
        String clinicaId = AuthContextHelper.currentClinicaId();
        Convenio existente = convenioRepository.findByIdAndClinicaId(id, clinicaId)
                .filter(Convenio::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convênio não encontrado."));

        if (cnpjEmUso(dadosAtualizados.getCnpj(), existente.getCnpj(), clinicaId)) {
            throw cnpjDuplicado();
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

        return salvar(existente);
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
