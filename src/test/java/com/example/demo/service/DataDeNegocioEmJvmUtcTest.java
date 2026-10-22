package com.example.demo.service;

import com.example.demo.enums.CategoriaContaPagarEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.Anamnese;
import com.example.demo.model.ContaPagar;
import com.example.demo.model.Odontograma;
import com.example.demo.model.Paciente;
import com.example.demo.repository.AnamneseRepository;
import com.example.demo.repository.ContaPagarRepository;
import com.example.demo.repository.OdontogramaRepository;
import com.example.demo.repository.PacienteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.TimeZone;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * A VPS roda a JVM em UTC. Às 22:30 de 23/10 em São Paulo já são 01:30Z de 24/10: a data de negócio gravada
 * (pagamento, preenchimento da anamnese, avaliação do odontograma) tem de continuar sendo 23/10.
 */
@ExtendWith(MockitoExtension.class)
class DataDeNegocioEmJvmUtcTest {

    private static final Clock NOITE_EM_SP_JA_DIA_SEGUINTE_EM_UTC =
            Clock.fixed(Instant.parse("2026-10-24T01:30:00Z"), ZoneOffset.UTC);
    private static final LocalDate DIA_EM_SP = LocalDate.of(2026, 10, 23);

    @Mock ContaPagarRepository contaPagarRepository;
    @Mock AnamneseRepository anamneseRepository;
    @Mock OdontogramaRepository odontogramaRepository;
    @Mock PacienteRepository pacienteRepository;

    @InjectMocks ContaPagarService contaPagarService;
    @InjectMocks AnamneseService anamneseService;
    @InjectMocks OdontogramaService odontogramaService;

    private TimeZone fusoOriginal;

    @BeforeEach
    void setUp() {
        fusoOriginal = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        TenantTestSupport.loginTestUser();
        for (Object servico : new Object[]{contaPagarService, anamneseService, odontogramaService}) {
            ReflectionTestUtils.setField(servico, "relogio", NOITE_EM_SP_JA_DIA_SEGUINTE_EM_UTC);
        }
        lenient().when(contaPagarRepository.save(any(ContaPagar.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(anamneseRepository.save(any(Anamnese.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(odontogramaRepository.save(any(Odontograma.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(pacienteRepository.findByIdAndClinicaId("p1", TEST_CLINICA_ID)).thenReturn(Optional.of(
                Paciente.builder().id("p1").clinicaId(TEST_CLINICA_ID).nomeCompleto("Paciente").ativo(true).build()));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(fusoOriginal);
        TenantTestSupport.logout();
    }

    private ContaPagar despesa(StatusFinanceiroEnum status) {
        return ContaPagar.builder().id("d1").clinicaId(TEST_CLINICA_ID).descricao("Luz")
                .categoria(CategoriaContaPagarEnum.LUZ).valor(100.0)
                .dataVencimento(LocalDate.of(2026, 10, 30)).status(status).build();
    }

    @Test
    void jvmEmUtcRealmenteVeODiaSeguinte_PreCondicaoDoTeste() {
        assertEquals(LocalDate.of(2026, 10, 24), LocalDate.now(NOITE_EM_SP_JA_DIA_SEGUINTE_EM_UTC));
    }

    @Test
    void contaPagar_CriarJaPaga_DataPagamentoEODiaDeSaoPaulo() {
        ContaPagar resultado = contaPagarService.criar(despesa(StatusFinanceiroEnum.PAGO));

        assertEquals(DIA_EM_SP, resultado.getDataPagamento());
    }

    @Test
    void contaPagar_CriarJaPagaComDataInformada_MantemADataInformada() {
        ContaPagar conta = despesa(StatusFinanceiroEnum.PAGO);
        conta.setDataPagamento(LocalDate.of(2026, 10, 10));

        assertEquals(LocalDate.of(2026, 10, 10), contaPagarService.criar(conta).getDataPagamento());
    }

    @Test
    void contaPagar_QuitarSemDataInformada_DataPagamentoEODiaDeSaoPaulo() {
        when(contaPagarRepository.findByIdAndClinicaId("d1", TEST_CLINICA_ID))
                .thenReturn(Optional.of(despesa(StatusFinanceiroEnum.PENDENTE)));

        ContaPagar resultado = contaPagarService.atualizar("d1", despesa(StatusFinanceiroEnum.PAGO));

        assertEquals(StatusFinanceiroEnum.PAGO, resultado.getStatus());
        assertEquals(DIA_EM_SP, resultado.getDataPagamento());
    }

    @Test
    void contaPagar_QuitarComDataInformada_MantemADataInformada() {
        when(contaPagarRepository.findByIdAndClinicaId("d1", TEST_CLINICA_ID))
                .thenReturn(Optional.of(despesa(StatusFinanceiroEnum.PENDENTE)));
        ContaPagar dados = despesa(StatusFinanceiroEnum.PAGO);
        dados.setDataPagamento(LocalDate.of(2026, 10, 5));

        assertEquals(LocalDate.of(2026, 10, 5), contaPagarService.atualizar("d1", dados).getDataPagamento());
    }

    @Test
    void anamnese_DataPreenchimentoEODiaDeSaoPaulo() {
        Anamnese anamnese = new Anamnese();
        anamnese.setPacienteId("p1");

        assertEquals(DIA_EM_SP, anamneseService.criar(anamnese).getDataPreenchimento());
    }

    @Test
    void odontograma_DataAvaliacaoEODiaDeSaoPaulo() {
        Odontograma odontograma = new Odontograma();
        odontograma.setPacienteId("p1");

        assertEquals(DIA_EM_SP, odontogramaService.criar(odontograma).getDataAvaliacao());
    }

    @Test
    void viradaDoDiaEmSaoPaulo_As03hUtcJaE24DeOutubro() {
        for (Object servico : new Object[]{contaPagarService, anamneseService, odontogramaService}) {
            ReflectionTestUtils.setField(servico, "relogio", Clock.fixed(Instant.parse("2026-10-24T03:00:00Z"), ZoneOffset.UTC));
        }
        Anamnese anamnese = new Anamnese();
        anamnese.setPacienteId("p1");

        assertEquals(LocalDate.of(2026, 10, 24), anamneseService.criar(anamnese).getDataPreenchimento());
        assertEquals(LocalDate.of(2026, 10, 24), contaPagarService.criar(despesa(StatusFinanceiroEnum.PAGO)).getDataPagamento());
    }
}
