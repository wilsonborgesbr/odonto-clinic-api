package com.example.demo.service;

import com.example.demo.dto.ProdutividadeDTO;
import com.example.demo.dto.ProdutividadeDTO.Ponto;
import com.example.demo.enums.MetricaProdutividadeEnum;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.UnidadeMetricaEnum;
import com.example.demo.exception.CampoInvalidoException;
import com.example.demo.model.Dentista;
import com.example.demo.model.User;
import com.example.demo.repository.DentistaRepository;
import com.example.demo.service.AgregacaoDiariaService.ResumoPagamentos;
import com.example.demo.service.AgregacaoDiariaService.SaldoAberto;
import com.example.demo.util.JanelaProdutividade.Granularidade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutividadeServiceTest {

    // 17:00Z = 14:00 em São Paulo, sexta-feira 23/10/2026
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-23T17:00:00Z"), ZoneOffset.UTC);

    @Mock AgregacaoDiariaService agregacao;
    @Mock DentistaRepository dentistaRepository;
    @InjectMocks ProdutividadeService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "relogio", RELOGIO);
        logar(RoleEnum.PROPRIETARIO, null);
        // por padrão a clínica teve movimento em todos os dias; os testes de "sem expediente" trocam este stub
        lenient().when(agregacao.diasComMovimento(anyString(), any(), any())).thenAnswer(i -> {
            Set<LocalDate> dias = new java.util.TreeSet<>();
            for (LocalDate d = i.getArgument(1); !d.isAfter(i.getArgument(2)); d = d.plusDays(1)) {
                dias.add(d);
            }
            return dias;
        });
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    private void logar(RoleEnum role, Set<PermissaoEnum> permissoes) {
        User user = User.builder().id("u1").name("U").email("u@x.com").clinicaId(TEST_CLINICA_ID).role(role)
                .permissoes(permissoes).ativo(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private static Map<StatusAgendamentoEnum, Long> dia(long realizadas, long faltas, long canceladas, long agendadas, long confirmadas) {
        Map<StatusAgendamentoEnum, Long> m = new EnumMap<>(StatusAgendamentoEnum.class);
        if (realizadas > 0) m.put(StatusAgendamentoEnum.REALIZADO, realizadas);
        if (faltas > 0) m.put(StatusAgendamentoEnum.FALTOU, faltas);
        if (canceladas > 0) m.put(StatusAgendamentoEnum.CANCELADO, canceladas);
        if (agendadas > 0) m.put(StatusAgendamentoEnum.AGENDADO, agendadas);
        if (confirmadas > 0) m.put(StatusAgendamentoEnum.CONFIRMADO, confirmadas);
        return m;
    }

    private static final LocalDate D5 = LocalDate.of(2026, 10, 5);
    private static final LocalDate D6 = LocalDate.of(2026, 10, 6);

    private void agendaOutubro(Map<LocalDate, Map<StatusAgendamentoEnum, Long>> outubro,
                               Map<LocalDate, Map<StatusAgendamentoEnum, Long>> setembro) {
        when(agregacao.contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), any(), any(), any())).thenAnswer(i -> {
            LocalDateTime inicio = i.getArgument(1);
            return inicio.getMonthValue() == 10 ? new TreeMap<>(outubro) : new TreeMap<>(setembro);
        });
    }

    private Ponto ponto(ProdutividadeDTO dto, String chave) {
        return dto.pontos().stream().filter(p -> p.chave().equals(chave)).findFirst().orElseThrow();
    }

    // ---------- permissão por métrica (403) ----------

    @Test
    void matrizDePermissao_CadaPerfilSoVeAsMetricasDaSuaPermissaoEOServidorNaoCalculaAsDemais() {
        when(agregacao.saldoEmAbertoVencidoAntesDe(anyString(), any())).thenReturn(new SaldoAberto(BigDecimal.ZERO.setScale(2), 0));
        for (RoleEnum role : RoleEnum.values()) {
            logar(role, null);
            for (MetricaProdutividadeEnum metrica : MetricaProdutividadeEnum.values()) {
                boolean permitido = role.permissoesPadrao().contains(metrica.permissao());
                if (permitido) {
                    ProdutividadeDTO dto = service.consultar(metrica.name(), "MES", null, null);
                    assertEquals(metrica.name(), dto.metrica());
                    assertTrue(dto.metricasPermitidas().contains(metrica.name()), role + " " + metrica);
                } else {
                    ResponseStatusException e = assertThrows(ResponseStatusException.class,
                            () -> service.consultar(metrica.name(), "MES", null, null), role + " " + metrica);
                    assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode(), role + " " + metrica);
                }
            }
        }
    }

    @Test
    void perfisDoQa_QuaisMetricasCadaUmVe() {
        lenient().when(agregacao.saldoEmAbertoVencidoAntesDe(anyString(), any())).thenReturn(new SaldoAberto(BigDecimal.ZERO.setScale(2), 0));
        logar(RoleEnum.PROPRIETARIO, null);
        assertEquals(9, service.consultar("FALTAS", null, null, null).metricasPermitidas().size());
        logar(RoleEnum.DENTISTA, null);
        assertEquals(Set.of("CONSULTAS_REALIZADAS", "AGENDAMENTOS", "FALTAS", "COMPARECIMENTO", "CANCELAMENTOS", "NOVOS_PACIENTES"),
                Set.copyOf(service.consultar("FALTAS", null, null, null).metricasPermitidas()));
        logar(RoleEnum.RECEPCIONISTA, null);
        assertEquals(6, service.consultar("FALTAS", null, null, null).metricasPermitidas().size());
        logar(RoleEnum.FINANCEIRO, null);
        assertEquals(java.util.List.of("FATURAMENTO", "TICKET_MEDIO", "A_RECEBER"),
                service.consultar("FATURAMENTO", null, null, null).metricasPermitidas());
        logar(RoleEnum.ESTOQUISTA, null);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.consultar("CONSULTAS_REALIZADAS", null, null, null)).getStatusCode());
    }

    @Test
    void permissoesPersonalizadasSobrescrevemOPadraoInclusiveDoProprietario() {
        logar(RoleEnum.PROPRIETARIO, EnumSet.of(PermissaoEnum.PACIENTES));

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.consultar("FATURAMENTO", null, null, null)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.consultar("FALTAS", null, null, null)).getStatusCode());
        assertEquals(java.util.List.of("NOVOS_PACIENTES"), service.consultar("NOVOS_PACIENTES", null, null, null).metricasPermitidas());
        verifyNoInteractions(dentistaRepository);
    }

    @Test
    void semPermissao_403AntesDeValidarOsDemaisParametrosENaoTocaNoBanco() {
        logar(RoleEnum.ESTOQUISTA, null);

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.consultar("FATURAMENTO", "XYZ", "lixo", "d1")).getStatusCode());
        verifyNoInteractions(agregacao, dentistaRepository);
    }

    // ---------- parâmetros ----------

    @Test
    void parametrosInvalidos_400PorCampo() {
        CampoInvalidoException semMetrica = assertThrows(CampoInvalidoException.class, () -> service.consultar(null, null, null, null));
        assertEquals("metrica", semMetrica.getCampo());
        assertEquals("metrica", assertThrows(CampoInvalidoException.class, () -> service.consultar("LUCRO", null, null, null)).getCampo());
        assertEquals("periodo", assertThrows(CampoInvalidoException.class, () -> service.consultar("FALTAS", "DECADA", null, null)).getCampo());
        for (String ruim : new String[]{"23/10/2026", "2026-10-1", "2026-02-30", "abc", "0099-01-01", "2026-10-23T10:00"}) {
            assertEquals("referencia", assertThrows(CampoInvalidoException.class,
                    () -> service.consultar("FALTAS", "MES", ruim, null), ruim).getCampo(), ruim);
        }
        verifyNoInteractions(agregacao);
    }

    @Test
    void padroes_PeriodoMesReferenciaHojeEMinusculasAceitas() {
        ProdutividadeDTO dto = service.consultar(" faltas ", " semana ", "", null);

        assertEquals("FALTAS", dto.metrica());
        assertEquals("SEMANA", dto.periodo());
        assertEquals("2026-10-23", dto.referencia());
        assertEquals("MES", service.consultar("FALTAS", null, null, null).periodo());
        assertEquals("2026-10-23", service.consultar("FALTAS", "MES", null, null).referencia());
    }

    @Test
    void dentistaId_ValidadoNaClinicaSoParaAgendaEIgnoradoNasDemais() {
        when(dentistaRepository.findByIdAndClinicaId("d9", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.consultar("FALTAS", "MES", null, "d9"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());

        when(dentistaRepository.findByIdAndClinicaId("d1", TEST_CLINICA_ID)).thenReturn(Optional.of(new Dentista()));
        service.consultar("FALTAS", "MES", null, " d1 ");
        verify(agregacao, atLeastOnce()).contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), any(), any(), eq("d1"));

        service.consultar("NOVOS_PACIENTES", "MES", null, "qualquer");
        service.consultar("FATURAMENTO", "MES", null, "qualquer");
        verify(dentistaRepository, never()).findByIdAndClinicaId(eq("qualquer"), anyString());
        service.consultar("FALTAS", "MES", null, "  ");
        verify(agregacao, atLeastOnce()).contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), any(), any(), isNull());
    }

    // ---------- agenda ----------

    @Test
    void comparecimento_TotalEoDoPeriodoInteiroNaoAMediaDasTaxasDiariasEDiaSemBaseENulo() {
        agendaOutubro(Map.of(D5, dia(3, 1, 0, 0, 0), D6, dia(1, 0, 0, 0, 0)), Map.of());

        ProdutividadeDTO dto = service.consultar("COMPARECIMENTO", "MES", null, null);

        assertEquals(UnidadeMetricaEnum.PERCENTUAL.name(), dto.unidade());
        assertEquals(new BigDecimal("75.00"), ponto(dto, "2026-10-05").valor());
        assertEquals(new BigDecimal("100.00"), ponto(dto, "2026-10-06").valor());
        assertNull(ponto(dto, "2026-10-07").valor(), "dia sem atendimento passado: null, não zero");
        assertEquals(new BigDecimal("80.00"), dto.total(), "4/5, e não a média 87,5 das taxas diárias");
        assertEquals(Map.of("realizadas", 3L, "faltas", 1L, "previstos", 4L), ponto(dto, "2026-10-05").detalhes());
        assertNull(dto.totalAnterior(), "setembro sem atendimentos: sem base");
        assertNull(dto.variacaoPercentual());
    }

    @Test
    void comparecimento_SoContaHorariosQueJaComecaram_OLimiteEAgoraEmSaoPaulo() {
        service.consultar("COMPARECIMENTO", "MES", null, null);
        ArgumentCaptor<LocalDateTime> fim = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(agregacao, times(2)).contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), any(), fim.capture(), any());
        assertEquals(LocalDateTime.of(2026, 10, 23, 14, 0, 1), fim.getAllValues().get(0), "atual: até agora (14:00 de SP)");
        assertEquals(LocalDateTime.of(2026, 9, 24, 0, 0), fim.getAllValues().get(1), "anterior: até o fim do dia 23/09");

        clearInvocations(agregacao);
        service.consultar("CONSULTAS_REALIZADAS", "MES", null, null);
        verify(agregacao, times(2)).contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), any(), fim.capture(), any());
        assertEquals(LocalDateTime.of(2026, 10, 24, 0, 0), fim.getAllValues().get(2), "as demais contam o dia de hoje inteiro");
    }

    @Test
    void agendamentos_ContaTodosOsStatusEODetalheFechaComOTotal() {
        agendaOutubro(Map.of(D5, dia(9, 2, 1, 1, 1)), Map.of());

        ProdutividadeDTO dto = service.consultar("AGENDAMENTOS", "MES", null, null);

        Ponto p = ponto(dto, "2026-10-05");
        assertEquals(14L, p.valor());
        assertEquals(Map.of("realizadas", 9L, "faltas", 2L, "canceladas", 1L, "agendadas", 2L), p.detalhes());
        assertEquals(14L, dto.total());
        assertEquals(0L, ponto(dto, "2026-10-06").valor());
    }

    @Test
    void consultasFaltasECancelamentos_CadaUmContaSoOSeuStatusEMostraAgendamentosNoDetalhe() {
        agendaOutubro(Map.of(D5, dia(9, 2, 1, 1, 1)), Map.of());

        assertEquals(9L, ponto(service.consultar("CONSULTAS_REALIZADAS", "MES", null, null), "2026-10-05").valor());
        ProdutividadeDTO faltas = service.consultar("FALTAS", "MES", null, null);
        assertEquals(2L, ponto(faltas, "2026-10-05").valor());
        assertEquals(Map.of("agendamentos", 14L), ponto(faltas, "2026-10-05").detalhes());
        ProdutividadeDTO canceladas = service.consultar("CANCELAMENTOS", "MES", null, null);
        assertEquals(1L, ponto(canceladas, "2026-10-05").valor());
        assertEquals(1L, canceladas.total());
    }

    @Test
    void pontosFuturos_ValorNuloEFuturoTrueMasOEixoVemCompleto() {
        agendaOutubro(Map.of(D5, dia(1, 0, 0, 0, 0)), Map.of());

        ProdutividadeDTO dto = service.consultar("CONSULTAS_REALIZADAS", "MES", null, null);

        assertEquals(31, dto.pontos().size());
        assertEquals(0L, ponto(dto, "2026-10-23").valor(), "hoje conta e não é futuro");
        assertFalse(ponto(dto, "2026-10-23").futuro());
        assertNull(ponto(dto, "2026-10-24").valor());
        assertTrue(ponto(dto, "2026-10-24").futuro());
        assertNull(ponto(dto, "2026-10-31").valor());
        assertNull(ponto(dto, "2026-10-31").detalhes(), "ponto futuro sem valor não tem detalhes");
    }

    @Test
    void anterior_AlinhadoPorPosicaoESoAteOMesmoIntervaloEVariacaoSobreEle() {
        Map<LocalDate, Map<StatusAgendamentoEnum, Long>> setembro = Map.of(
                LocalDate.of(2026, 9, 5), dia(5, 0, 0, 0, 0),
                LocalDate.of(2026, 9, 25), dia(40, 0, 0, 0, 0)); // depois do dia 23: fora do intervalo
        agendaOutubro(Map.of(D5, dia(11, 0, 0, 0, 0)), setembro);
        // o stub devolve o dia 25/09 mesmo fora do corte; o serviço deve ignorá-lo apenas se o agregador respeitar o fim
        // pedido (o corte do anterior é 23/09): aqui validamos o pedido, não o filtro do Mongo
        ProdutividadeDTO dto = service.consultar("CONSULTAS_REALIZADAS", "MES", null, null);

        ArgumentCaptor<LocalDateTime> inicio = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fim = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(agregacao, times(2)).contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), inicio.capture(), fim.capture(), any());
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), inicio.getAllValues().get(1));
        assertEquals(LocalDateTime.of(2026, 9, 24, 0, 0), fim.getAllValues().get(1));
        assertEquals(5L, ponto(dto, "2026-10-05").anterior());
        assertNull(ponto(dto, "2026-10-24").anterior(), "além do corte do anterior não há comparação");
        assertEquals("vs mês anterior (mesmo intervalo: dias 1 a 23)", dto.textoVariacao());
        assertEquals("Outubro de 2026", dto.rotuloPeriodo());
    }

    @Test
    void semana_SeteDiasSegundaADomingo() {
        ProdutividadeDTO dto = service.consultar("FALTAS", "SEMANA", null, null);

        assertEquals(7, dto.pontos().size());
        assertEquals("2026-10-19", dto.pontos().get(0).chave());
        assertEquals("Seg", dto.pontos().get(0).rotulo());
        assertEquals("19", dto.pontos().get(0).rotuloSecundario());
        assertEquals("2026-10-25", dto.pontos().get(6).chave());
        assertEquals("19 a 25 de outubro de 2026", dto.rotuloPeriodo());
        assertEquals("vs semana anterior (mesmo intervalo: seg a sex)", dto.textoVariacao());
        assertTrue(dto.pontos().get(6).futuro());
        assertNull(dto.pontos().get(6).valor());
    }

    @Test
    void ano_DozePontosMensaisSomandoOsDiasDoMes() {
        when(agregacao.contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), eq(LocalDateTime.of(2026, 1, 1, 0, 0)), any(), any()))
                .thenReturn(new TreeMap<>(Map.of(D5, dia(3, 0, 0, 0, 0), D6, dia(4, 0, 0, 0, 0))));
        when(agregacao.faixaDeAnosAgendamentos(TEST_CLINICA_ID, null)).thenReturn(new int[]{2025, 2026});

        ProdutividadeDTO dto = service.consultar("CONSULTAS_REALIZADAS", "ANO", null, null);

        assertEquals(12, dto.pontos().size());
        assertEquals(7L, ponto(dto, "2026-10-01").valor());
        assertEquals(0L, ponto(dto, "2026-01-01").valor());
        assertNull(ponto(dto, "2026-11-01").valor());
        assertEquals("Out", ponto(dto, "2026-10-01").rotulo());
        assertEquals(7L, dto.total());
        assertEquals(java.util.List.of(2025, 2026), dto.anosDisponiveis());
    }

    @Test
    void todos_UmPontoPorAnoSemPeriodoAnterior() {
        when(agregacao.faixaDeAnosAgendamentos(TEST_CLINICA_ID, null)).thenReturn(new int[]{2024, 2026});
        when(agregacao.contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), any(), any(), any())).thenReturn(new TreeMap<>(Map.of(
                LocalDate.of(2024, 3, 1), dia(2, 0, 0, 0, 0),
                LocalDate.of(2026, 10, 5), dia(5, 0, 0, 0, 0))));

        ProdutividadeDTO dto = service.consultar("CONSULTAS_REALIZADAS", "TODOS", null, null);

        assertEquals(3, dto.pontos().size());
        assertEquals(2L, ponto(dto, "2024-01-01").valor());
        assertEquals(0L, ponto(dto, "2025-01-01").valor(), "ano sem dado entre dois com dado vale 0");
        assertEquals(5L, ponto(dto, "2026-01-01").valor());
        assertEquals("2026", dto.pontos().get(2).rotulo());
        assertEquals(7L, dto.total());
        assertNull(dto.totalAnterior());
        assertNull(dto.variacaoPercentual());
        assertNull(dto.textoVariacao());
        assertNull(dto.pontos().get(0).anterior());
        assertEquals("Todos os anos", dto.rotuloPeriodo());
        verify(agregacao, times(1)).contarAgendamentosPorDiaEStatus(any(), any(), any(), any());
    }

    @Test
    void anosDisponiveis_SemDadoSoOCorrenteEComFuturoEPassadoSaoContiguos() {
        assertEquals(java.util.List.of(2026), service.consultar("FALTAS", null, null, null).anosDisponiveis());
        when(agregacao.faixaDeAnosAgendamentos(TEST_CLINICA_ID, null)).thenReturn(new int[]{2019, 2027});
        assertEquals(java.util.List.of(2019, 2020, 2021, 2022, 2023, 2024, 2025, 2026),
                service.consultar("FALTAS", null, null, null).anosDisponiveis(), "métrica de fato passado para no ano corrente");
        assertEquals(java.util.List.of(2019, 2020, 2021, 2022, 2023, 2024, 2025, 2026, 2027),
                service.consultar("AGENDAMENTOS", null, null, null).anosDisponiveis(), "agenda marcada inclui o ano futuro com dados");
    }

    @Test
    void periodoFuturo_ZeroSemBaseEPontosSemValor() {
        ProdutividadeDTO dto = service.consultar("CONSULTAS_REALIZADAS", "MES", "2026-12-10", null);

        assertEquals(0L, dto.total());
        assertTrue(dto.pontos().stream().allMatch(p -> p.valor() == null && p.futuro()));
        assertNull(dto.variacaoPercentual());
        verify(agregacao, never()).contarAgendamentosPorDiaEStatus(any(), any(), any(), any());
    }

    // ---------- pacientes ----------

    @Test
    void novosPacientes_SomaPorDiaSemDetalheEComIntervaloAteHoje() {
        when(agregacao.contarNovosPacientesPorDia(eq(TEST_CLINICA_ID), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 23))))
                .thenReturn(new TreeMap<>(Map.of(D5, 2L, D6, 1L)));
        when(agregacao.contarNovosPacientesPorDia(eq(TEST_CLINICA_ID), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 23))))
                .thenReturn(new TreeMap<>(Map.of(LocalDate.of(2026, 9, 2), 2L)));

        ProdutividadeDTO dto = service.consultar("NOVOS_PACIENTES", "MES", null, null);

        assertEquals(3L, dto.total());
        assertEquals(2L, dto.totalAnterior());
        assertEquals(new BigDecimal("50.0"), dto.variacaoPercentual());
        assertEquals(2L, ponto(dto, "2026-10-05").valor());
        assertTrue(ponto(dto, "2026-10-05").detalhes().isEmpty());
        assertEquals(2L, ponto(dto, "2026-10-02").anterior(), "anterior alinhado por posição: dia 2 de setembro");
    }

    // ---------- financeiro ----------

    private static ResumoPagamentos pag(String valor, long recebimentos, long pacientes) {
        return new ResumoPagamentos(new BigDecimal(valor), recebimentos, pacientes);
    }

    @Test
    void faturamento_SomaDoPeriodoDiaSemRecebimentoValeZeroEDetalheTemRecebimentos() {
        when(agregacao.resumirPagamentos(eq(TEST_CLINICA_ID), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 23)), eq(Granularidade.DIA)))
                .thenReturn(Map.of("2026-10-05", pag("300.00", 3, 2), "2026-10-06", pag("100.50", 1, 1)));
        when(agregacao.resumirPagamentos(eq(TEST_CLINICA_ID), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 23)), eq(Granularidade.DIA)))
                .thenReturn(Map.of("2026-09-05", pag("200.25", 2, 2)));

        ProdutividadeDTO dto = service.consultar("FATURAMENTO", "MES", null, null);

        assertEquals(UnidadeMetricaEnum.MOEDA.name(), dto.unidade());
        assertEquals(new BigDecimal("400.50"), dto.total());
        assertEquals(new BigDecimal("200.25"), dto.totalAnterior());
        assertEquals(new BigDecimal("100.0"), dto.variacaoPercentual());
        assertEquals(new BigDecimal("300.00"), ponto(dto, "2026-10-05").valor());
        assertEquals(new BigDecimal("0.00"), ponto(dto, "2026-10-07").valor());
        assertEquals(Map.of("recebimentos", 3L), ponto(dto, "2026-10-05").detalhes());
        assertNull(ponto(dto, "2026-10-24").valor());
        assertNull(dto.inadimplenciaHistorica());
    }

    @Test
    void faturamento_AnoAgrupaPorMesETodosPorAno() {
        when(agregacao.resumirPagamentos(eq(TEST_CLINICA_ID), any(), any(), eq(Granularidade.MES)))
                .thenReturn(Map.of("2026-10", pag("900.00", 9, 5)));
        when(agregacao.resumirPagamentos(eq(TEST_CLINICA_ID), any(), any(), eq(Granularidade.ANO)))
                .thenReturn(Map.of("2025", pag("100.00", 1, 1), "2026", pag("900.00", 9, 5)));
        when(agregacao.faixaDeAnosPagamentos(TEST_CLINICA_ID)).thenReturn(new int[]{2025, 2026});

        assertEquals(new BigDecimal("900.00"), ponto(service.consultar("FATURAMENTO", "ANO", null, null), "2026-10-01").valor());
        ProdutividadeDTO todos = service.consultar("FATURAMENTO", "TODOS", null, null);
        assertEquals(new BigDecimal("1000.00"), todos.total());
        assertEquals(new BigDecimal("100.00"), ponto(todos, "2025-01-01").valor());
    }

    @Test
    void ticketMedio_TotalEFaturamentoDivididoPorPacientesDistintosDoPeriodoNaoMediaDosDias() {
        when(agregacao.resumirPagamentos(eq(TEST_CLINICA_ID), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 23)), eq(Granularidade.DIA)))
                .thenReturn(Map.of("2026-10-05", pag("300.00", 3, 2), "2026-10-06", pag("100.00", 1, 1)));
        // o mesmo paciente pagou nos dois dias: no período inteiro são 2 pacientes distintos, não 3
        when(agregacao.resumirPagamentos(eq(TEST_CLINICA_ID), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 23)), isNull()))
                .thenReturn(Map.of("total", pag("400.00", 4, 2)));

        ProdutividadeDTO dto = service.consultar("TICKET_MEDIO", "MES", null, null);

        assertEquals(new BigDecimal("150.00"), ponto(dto, "2026-10-05").valor());
        assertEquals(new BigDecimal("100.00"), ponto(dto, "2026-10-06").valor());
        assertNull(ponto(dto, "2026-10-07").valor(), "sem pagamento no dia: null");
        assertEquals(Map.of("pacientesPagantes", 2L), ponto(dto, "2026-10-05").detalhes());
        assertEquals(new BigDecimal("200.00"), dto.total(), "400 / 2 pacientes distintos (a média dos dias seria 125)");
        assertNull(dto.totalAnterior(), "setembro sem pagamentos");
        assertNull(dto.variacaoPercentual());
    }

    @Test
    void aReceber_VaiAteOFimDoPeriodoMostraFuturoMarcaAtrasoEInformaInadimplenciaHistorica() {
        LocalDate futuro = LocalDate.of(2026, 10, 28);
        when(agregacao.somarAReceberPorDia(eq(TEST_CLINICA_ID), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(Map.of(LocalDate.of(2026, 10, 10), new SaldoAberto(new BigDecimal("500.00"), 2),
                        futuro, new SaldoAberto(new BigDecimal("1200.00"), 1)));
        when(agregacao.somarAReceberPorDia(eq(TEST_CLINICA_ID), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(Map.of(LocalDate.of(2026, 9, 15), new SaldoAberto(new BigDecimal("850.00"), 1)));
        when(agregacao.saldoEmAbertoVencidoAntesDe(TEST_CLINICA_ID, LocalDate.of(2026, 10, 1)))
                .thenReturn(new SaldoAberto(new BigDecimal("12500.00"), 8));

        ProdutividadeDTO dto = service.consultar("A_RECEBER", "MES", null, null);

        assertEquals(new BigDecimal("1700.00"), dto.total());
        assertEquals(new BigDecimal("850.00"), dto.totalAnterior(), "anterior inteiro, não só até o dia 23");
        assertEquals(new BigDecimal("100.0"), dto.variacaoPercentual());
        assertEquals("vs mês anterior (período inteiro)", dto.textoVariacao());
        Ponto passado = ponto(dto, "2026-10-10");
        assertEquals(new BigDecimal("500.00"), passado.valor());
        assertEquals(new BigDecimal("500.00"), passado.detalhes().get("atrasado"));
        assertFalse(passado.futuro());
        Ponto previsto = ponto(dto, "2026-10-28");
        assertEquals(new BigDecimal("1200.00"), previsto.valor());
        assertEquals(new BigDecimal("0.00"), previsto.detalhes().get("atrasado"));
        assertTrue(previsto.futuro());
        assertEquals(new BigDecimal("0.00"), ponto(dto, "2026-10-31").valor(), "A receber mostra os dias futuros sem saldo como zero");
        assertEquals(new BigDecimal("12500.00"), dto.inadimplenciaHistorica().totalAtrasado());
        assertEquals(8L, dto.inadimplenciaHistorica().quantidadeTitulos());
        assertEquals(new BigDecimal("850.00"), ponto(dto, "2026-10-15").anterior());
    }

    // ---------- v2.2: agenda marcada e a receber projetam o futuro ----------

    @Test
    void agendamentos_PontosFuturosTemValorRealETotalEAnteriorSaoDoPeriodoInteiro() {
        agendaOutubro(
                Map.of(D5, dia(9, 2, 1, 1, 1), LocalDate.of(2026, 10, 28), dia(0, 0, 0, 2, 1)),
                Map.of(LocalDate.of(2026, 9, 5), dia(5, 0, 0, 0, 0), LocalDate.of(2026, 9, 25), dia(1, 0, 0, 0, 0)));

        ProdutividadeDTO dto = service.consultar("AGENDAMENTOS", "MES", null, null);

        Ponto futuroComAgenda = ponto(dto, "2026-10-28");
        assertEquals(3L, futuroComAgenda.valor(), "agendamentos de dias futuros contam e não são null");
        assertTrue(futuroComAgenda.futuro());
        assertEquals(Map.of("realizadas", 0L, "faltas", 0L, "canceladas", 0L, "agendadas", 3L), futuroComAgenda.detalhes());
        assertEquals(0L, ponto(dto, "2026-10-24").valor(), "futuro com movimento na clínica mas sem agenda: zero numérico");
        assertTrue(ponto(dto, "2026-10-24").futuro());
        assertEquals(17L, dto.total(), "total do período inteiro: 14 + 3");
        assertEquals(6L, dto.totalAnterior(), "setembro inteiro, inclusive o dia 25 depois do dia 23");
        assertEquals(new BigDecimal("183.3"), dto.variacaoPercentual());
        assertEquals("vs mês anterior (período inteiro)", dto.textoVariacao());
        assertEquals(1L, ponto(dto, "2026-10-25").anterior(), "anterior alinhado também depois do dia 23");

        ArgumentCaptor<LocalDateTime> fim = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(agregacao, times(2)).contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), any(), fim.capture(), any());
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), fim.getAllValues().get(0), "atual vai até o fim do mês");
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), fim.getAllValues().get(1), "anterior vai até o fim de setembro");
    }

    @Test
    void agendamentos_MesesEAnosFuturosSaoNavegaveisEAnoFuturoApareceEmAnosDisponiveis() {
        lenient().when(agregacao.contarAgendamentosPorDiaEStatus(any(), any(), any(), any())).thenReturn(new TreeMap<>());
        when(agregacao.faixaDeAnosAgendamentos(TEST_CLINICA_ID, null)).thenReturn(new int[]{2026, 2027});
        when(agregacao.contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), eq(LocalDateTime.of(2026, 12, 1, 0, 0)), any(), any()))
                .thenReturn(new TreeMap<>(Map.of(LocalDate.of(2026, 12, 10), dia(0, 0, 0, 4, 0))));
        when(agregacao.contarAgendamentosPorDiaEStatus(eq(TEST_CLINICA_ID), eq(LocalDateTime.of(2027, 1, 1, 0, 0)), any(), any()))
                .thenReturn(new TreeMap<>(Map.of(LocalDate.of(2027, 1, 15), dia(0, 0, 0, 2, 1))));

        ProdutividadeDTO dezembro = service.consultar("AGENDAMENTOS", "MES", "2026-12-10", null);
        assertEquals(4L, ponto(dezembro, "2026-12-10").valor());
        assertTrue(ponto(dezembro, "2026-12-10").futuro());
        assertEquals(4L, dezembro.total());
        assertEquals("Dezembro de 2026", dezembro.rotuloPeriodo());

        ProdutividadeDTO ano2027 = service.consultar("AGENDAMENTOS", "ANO", "2027-03-01", null);
        assertEquals(3L, ponto(ano2027, "2027-01-01").valor());
        assertEquals(3L, ano2027.total());
        assertEquals(java.util.List.of(2026, 2027), ano2027.anosDisponiveis());

        assertEquals(java.util.List.of(2026), service.consultar("FALTAS", "MES", null, null).anosDisponiveis(),
                "faltas não projeta o futuro: 2027 não aparece");
    }

    @Test
    void aReceber_AnoFuturoComVencimentosEhNavegavelEComparaComOAnoAnteriorInteiro() {
        when(agregacao.faixaDeAnosAReceber(TEST_CLINICA_ID)).thenReturn(new int[]{2026, 2027});
        when(agregacao.somarAReceberPorDia(TEST_CLINICA_ID, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)))
                .thenReturn(Map.of(LocalDate.of(2027, 3, 5), new SaldoAberto(new BigDecimal("700.00"), 1)));
        when(agregacao.somarAReceberPorDia(TEST_CLINICA_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(Map.of(LocalDate.of(2026, 11, 20), new SaldoAberto(new BigDecimal("350.00"), 1)));
        when(agregacao.saldoEmAbertoVencidoAntesDe(TEST_CLINICA_ID, LocalDate.of(2027, 1, 1)))
                .thenReturn(new SaldoAberto(new BigDecimal("900.00"), 3));

        ProdutividadeDTO dto = service.consultar("A_RECEBER", "ANO", "2027-02-01", null);

        assertEquals(new BigDecimal("700.00"), ponto(dto, "2027-03-01").valor());
        assertTrue(ponto(dto, "2027-03-01").futuro());
        assertEquals(new BigDecimal("700.00"), dto.total());
        assertEquals(new BigDecimal("350.00"), dto.totalAnterior(), "2026 inteiro");
        assertEquals(new BigDecimal("100.0"), dto.variacaoPercentual());
        assertEquals(java.util.List.of(2026, 2027), dto.anosDisponiveis());
        assertEquals(3L, dto.inadimplenciaHistorica().quantidadeTitulos());
        assertEquals("vs ano anterior (período inteiro)", dto.textoVariacao());
    }

    @Test
    void metricasDeFatoPassado_ContinuamComPontosFuturosNulosETotalAnteriorNoMesmoIntervalo() {
        for (String metrica : new String[]{"CONSULTAS_REALIZADAS", "FALTAS", "COMPARECIMENTO", "CANCELAMENTOS", "NOVOS_PACIENTES", "FATURAMENTO", "TICKET_MEDIO"}) {
            ProdutividadeDTO dto = service.consultar(metrica, "MES", null, null);
            assertNull(ponto(dto, "2026-10-28").valor(), metrica);
            assertTrue(ponto(dto, "2026-10-28").futuro(), metrica);
            assertEquals("vs mês anterior (mesmo intervalo: dias 1 a 23)", dto.textoVariacao(), metrica);
        }
    }

    // ---------- v2.2: semExpediente ----------

    private void movimentoApenasEm(LocalDate... dias) {
        Set<LocalDate> ativos = Set.of(dias);
        doReturn(ativos).when(agregacao).diasComMovimento(anyString(), any(), any());
    }

    @Test
    void semExpediente_DiaSemMovimentoFicaSemValorSemDetalhesEOAnteriorDoDiaParelhoTambemNulo() {
        agendaOutubro(Map.of(D5, dia(3, 0, 0, 0, 0)), Map.of(LocalDate.of(2026, 9, 5), dia(2, 0, 0, 0, 0)));
        movimentoApenasEm(D5, D6, LocalDate.of(2026, 10, 23), LocalDate.of(2026, 9, 5));

        ProdutividadeDTO dto = service.consultar("CONSULTAS_REALIZADAS", "MES", null, null);

        Ponto domingo = ponto(dto, "2026-10-04");
        assertTrue(domingo.semExpediente());
        assertNull(domingo.valor(), "sem expediente: valor null, não zero");
        assertNull(domingo.detalhes());
        assertNull(domingo.anterior());
        assertFalse(domingo.futuro());
        Ponto trabalhado = ponto(dto, "2026-10-05");
        assertFalse(trabalhado.semExpediente());
        assertEquals(3L, trabalhado.valor());
        assertEquals(2L, trabalhado.anterior());
        assertEquals(0L, ponto(dto, "2026-10-06").valor(), "dia com movimento mas sem consulta realizada vale 0, não null");
        assertFalse(ponto(dto, "2026-10-06").semExpediente());
        assertNull(ponto(dto, "2026-10-07").anterior(), "07/09 não teve movimento");
        assertEquals(3L, dto.total(), "o total não muda: dia sem expediente não tem nada a somar");
    }

    @Test
    void semExpediente_ValeParaAsNoveMetricasNaSemana() {
        when(agregacao.saldoEmAbertoVencidoAntesDe(anyString(), any())).thenReturn(new SaldoAberto(BigDecimal.ZERO.setScale(2), 0));
        movimentoApenasEm(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 12));

        for (MetricaProdutividadeEnum metrica : MetricaProdutividadeEnum.values()) {
            ProdutividadeDTO dto = service.consultar(metrica.name(), "SEMANA", null, null);
            Ponto quarta = ponto(dto, "2026-10-21");
            assertTrue(quarta.semExpediente(), metrica.name());
            assertNull(quarta.valor(), metrica.name());
            assertNull(quarta.detalhes(), metrica.name());
            assertNull(quarta.anterior(), metrica + ": 14/10 também não teve movimento");
            assertFalse(ponto(dto, "2026-10-23").semExpediente(), metrica.name());
            assertFalse(ponto(dto, "2026-10-19").semExpediente(), metrica.name());
        }
    }

    @Test
    void semExpediente_DiaFuturoSemMovimentoVemNuloMasFuturoComMovimentoMantemOValorNumericoDeAgendamentosEAReceber() {
        when(agregacao.saldoEmAbertoVencidoAntesDe(anyString(), any())).thenReturn(new SaldoAberto(BigDecimal.ZERO.setScale(2), 0));
        LocalDate sabado = LocalDate.of(2026, 10, 24);
        LocalDate domingo = LocalDate.of(2026, 10, 25);
        movimentoApenasEm(LocalDate.of(2026, 10, 23), sabado);

        for (String metrica : new String[]{"AGENDAMENTOS", "A_RECEBER"}) {
            ProdutividadeDTO dto = service.consultar(metrica, "SEMANA", null, null);
            Ponto sab = ponto(dto, sabado.toString());
            assertFalse(sab.semExpediente(), metrica);
            assertTrue(sab.futuro(), metrica);
            assertNotNull(sab.valor(), metrica + ": futuro com movimento tem valor numérico (zero)");
            Ponto dom = ponto(dto, domingo.toString());
            assertTrue(dom.semExpediente(), metrica);
            assertTrue(dom.futuro(), metrica);
            assertNull(dom.valor(), metrica);
        }
        ProdutividadeDTO faltas = service.consultar("FALTAS", "SEMANA", null, null);
        assertFalse(ponto(faltas, sabado.toString()).semExpediente());
        assertNull(ponto(faltas, sabado.toString()).valor(), "fato passado: o futuro segue sem valor mesmo com movimento");
    }

    @Test
    void semExpediente_EmAnoETodosESempreFalsoESemConsultarMovimento() {
        when(agregacao.faixaDeAnosAgendamentos(TEST_CLINICA_ID, null)).thenReturn(new int[]{2025, 2026});

        for (String periodo : new String[]{"ANO", "TODOS"}) {
            ProdutividadeDTO dto = service.consultar("CONSULTAS_REALIZADAS", periodo, null, null);
            assertTrue(dto.pontos().stream().noneMatch(Ponto::semExpediente), periodo);
            assertTrue(dto.pontos().stream().filter(p -> !p.futuro()).allMatch(p -> p.valor() != null), periodo + ": zero, não null");
        }
        verify(agregacao, never()).diasComMovimento(any(), any(), any());
    }

    @Test
    void semExpediente_ConsultaOMovimentoDaJanelaInteiraEDaAnterior() {
        service.consultar("FALTAS", "MES", null, null);

        verify(agregacao).diasComMovimento(TEST_CLINICA_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        verify(agregacao).diasComMovimento(TEST_CLINICA_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    // ---------- variação ----------

    @Test
    void variacao_RelativaComUmaCasaPontosPercentuaisParaATaxaENulaSemBase() {
        assertEquals(new BigDecimal("10.0"), ProdutividadeService.variacao(UnidadeMetricaEnum.CONTAGEM, 110L, 100L));
        assertEquals(new BigDecimal("-100.0"), ProdutividadeService.variacao(UnidadeMetricaEnum.CONTAGEM, 0L, 50L));
        assertEquals(new BigDecimal("-8.3"), ProdutividadeService.variacao(UnidadeMetricaEnum.CONTAGEM, 11L, 12L));
        assertNull(ProdutividadeService.variacao(UnidadeMetricaEnum.CONTAGEM, 5L, 0L), "anterior zero: sem selo");
        assertNull(ProdutividadeService.variacao(UnidadeMetricaEnum.CONTAGEM, 0L, 0L));
        assertNull(ProdutividadeService.variacao(UnidadeMetricaEnum.MOEDA, null, new BigDecimal("10.00")));
        assertNull(ProdutividadeService.variacao(UnidadeMetricaEnum.MOEDA, new BigDecimal("10.00"), null));
        assertEquals(new BigDecimal("5.0"), ProdutividadeService.variacao(UnidadeMetricaEnum.PERCENTUAL, new BigDecimal("80.00"), new BigDecimal("75.00")));
        assertEquals(new BigDecimal("-2.1"), ProdutividadeService.variacao(UnidadeMetricaEnum.PERCENTUAL, new BigDecimal("84.30"), new BigDecimal("86.40")));
        assertEquals(new BigDecimal("12.5"), ProdutividadeService.variacao(UnidadeMetricaEnum.MOEDA, new BigDecimal("112.50"), new BigDecimal("100.00")));
    }
}
