package com.example.demo.service;

import com.example.demo.dto.PendenciasCadastroDTO;
import com.example.demo.dto.PendenciasCadastroDTO.Campo;
import com.example.demo.dto.PendenciasCadastroDTO.Item;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.ConvenioRepository;
import com.example.demo.repository.DentistaRepository;
import com.example.demo.repository.FuncionarioRepository;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.util.FormatadorTexto;
import com.example.demo.util.FormatadorTexto.ErroCampo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PendenciaCadastroService {

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private FuncionarioRepository funcionarioRepository;

    @Autowired
    private DentistaRepository dentistaRepository;

    @Autowired
    private ConvenioRepository convenioRepository;

    @Autowired
    private ClinicaRepository clinicaRepository;

    public PendenciasCadastroDTO listar() {
        User usuario = AuthContextHelper.currentUser();
        String clinicaId = AuthContextHelper.currentClinicaId();
        Set<PermissaoEnum> permissoes = usuario.permissoesEfetivas();

        boolean pacientes = permissoes.contains(PermissaoEnum.PACIENTES);
        boolean funcionarios = permissoes.contains(PermissaoEnum.FUNCIONARIOS);
        boolean dentistas = permissoes.contains(PermissaoEnum.DENTISTAS);
        boolean convenios = permissoes.contains(PermissaoEnum.CONVENIOS);
        boolean clinica = permissoes.contains(PermissaoEnum.CONFIGURACOES);
        if (!(pacientes || funcionarios || dentistas || convenios || clinica)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para ver pendências de cadastro.");
        }

        List<Item> itens = new ArrayList<>();
        if (pacientes) {
            pacienteRepository.findByClinicaIdAndAtivoTrue(clinicaId).forEach(p ->
                    adicionar(itens, "PACIENTE", p.getId(), p.getNomeCompleto(), FormatadorTexto.erros(p)));
        }
        if (funcionarios) {
            funcionarioRepository.findByClinicaIdAndAtivoTrue(clinicaId).forEach(f ->
                    adicionar(itens, "FUNCIONARIO", f.getId(), f.getNomeCompleto(), FormatadorTexto.erros(f)));
        }
        if (dentistas) {
            dentistaRepository.findByClinicaIdAndAtivoTrue(clinicaId).forEach(d ->
                    adicionar(itens, "DENTISTA", d.getId(), d.getNomeCompleto(), FormatadorTexto.erros(d)));
        }
        if (convenios) {
            convenioRepository.findByClinicaId(clinicaId).stream()
                    .filter(c -> !Boolean.FALSE.equals(c.getAtivo()))
                    .forEach(c -> adicionar(itens, "CONVENIO", c.getId(), c.getNome(), FormatadorTexto.erros(c)));
        }
        if (clinica) {
            clinicaRepository.findById(clinicaId).ifPresent(c ->
                    adicionar(itens, "CLINICA", c.getId(), c.getNome(), FormatadorTexto.erros(c)));
        }

        itens.sort(Comparator.comparing(Item::entidade)
                .thenComparing(i -> i.nome() == null ? "" : i.nome().toLowerCase()));

        Map<String, Integer> porEntidade = new LinkedHashMap<>();
        itens.forEach(i -> porEntidade.merge(i.entidade(), 1, Integer::sum));
        return new PendenciasCadastroDTO(itens.size(), porEntidade, itens);
    }

    private void adicionar(List<Item> itens, String entidade, String id, String nome, List<ErroCampo> erros) {
        if (erros.isEmpty()) {
            return;
        }
        List<Campo> campos = erros.stream()
                .map(e -> new Campo(e.campo(), e.valor(), e.motivo()))
                .toList();
        itens.add(new Item(entidade, id, nome, campos));
    }
}
