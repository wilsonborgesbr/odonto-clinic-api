package com.example.demo.repository;

import com.example.demo.model.Dentista;
import com.example.demo.model.Funcionario;
import com.example.demo.model.Paciente;
import com.example.demo.service.PacienteService;
import com.example.demo.service.TenantTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;

// Usa um banco temporário próprio (derrubado ao final); é ignorado quando não há Mongo local.
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_ordenacao_nome")
@EnabledIf("mongoLocalDisponivel")
class OrdenacaoPorNomeMongoLocalTest {

    static boolean mongoLocalDisponivel() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }


    @Autowired MongoTemplate mongo;
    @Autowired DentistaRepository dentistaRepository;
    @Autowired FuncionarioRepository funcionarioRepository;
    @Autowired PacienteService pacienteService;

    @Test
    void listagensOrdenamPorNomeIgnorandoAcentoECaixaEBuscaContinuaIgual() {
        assertEquals("tmp_teste_ordenacao_nome", mongo.getDb().getName());
        List<String> nomes = List.of("Álvaro", "Ana", "Érica", "Eduardo", "Bruno");
        for (String n : nomes) {
            mongo.save(Paciente.builder().clinicaId(TEST_CLINICA_ID).nomeCompleto(n).ativo(true).build());
            Dentista d = new Dentista(); d.setClinicaId(TEST_CLINICA_ID); d.setNomeCompleto(n); d.setAtivo(true);
            mongo.save(d);
            Funcionario f = new Funcionario(); f.setClinicaId(TEST_CLINICA_ID); f.setNomeCompleto(n); f.setAtivo(true);
            mongo.save(f);
        }
        List<String> esperado = List.of("Álvaro", "Ana", "Bruno", "Eduardo", "Érica");
        var pageable = PageRequest.of(0, 10, Sort.by("nomeCompleto"));

        TenantTestSupport.loginTestUser();
        try {
            assertEquals(esperado, pacienteService.listar(null, null, pageable).map(p -> p.nomeCompleto()).getContent());
            assertEquals(esperado, dentistaRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID, pageable)
                    .map(Dentista::getNomeCompleto).getContent());
            assertEquals(esperado, funcionarioRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID, pageable)
                    .map(Funcionario::getNomeCompleto).getContent());
            // paginacao + count
            var pag = dentistaRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID, PageRequest.of(1, 2, Sort.by("nomeCompleto")));
            assertEquals(5, pag.getTotalElements());
            assertEquals(List.of("Bruno", "Eduardo"), pag.map(Dentista::getNomeCompleto).getContent());
            // busca continua igual (acento e caixa tolerantes via regex)
            assertEquals(List.of("Álvaro"), pacienteService.listar("alvaro", null, pageable).map(p -> p.nomeCompleto()).getContent());
            assertEquals(List.of("Érica"), pacienteService.listar("erica", null, pageable).map(p -> p.nomeCompleto()).getContent());
            // referência: sem collation o Mongo ordena em binário e acentuados vão para o fim
            var binaria = mongo.find(new org.springframework.data.mongodb.core.query.Query().with(pageable), Paciente.class)
                    .stream().map(Paciente::getNomeCompleto).toList();
            assertEquals(List.of("Ana", "Bruno", "Eduardo", "Álvaro", "Érica"), binaria);
        } finally {
            TenantTestSupport.logout();
            mongo.getDb().drop();
        }
    }
}
