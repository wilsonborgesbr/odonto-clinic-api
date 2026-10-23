package com.example.demo.util;

import com.example.demo.enums.PeriodoProdutividadeEnum;
import com.example.demo.util.JanelaProdutividade.Bucket;
import com.example.demo.util.JanelaProdutividade.Granularidade;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class JanelaProdutividadeTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 23); // sexta-feira

    private static JanelaProdutividade janela(PeriodoProdutividadeEnum periodo, LocalDate referencia, LocalDate hoje) {
        return JanelaProdutividade.de(periodo, referencia, hoje, false, 2023, 2026);
    }

    @Test
    void semana_VaiDeSegundaADomingoEEmQualquerDiaDaSemanaDaOMesmaJanela() {
        for (LocalDate dia : new LocalDate[]{LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 25)}) {
            JanelaProdutividade j = janela(PeriodoProdutividadeEnum.SEMANA, dia, HOJE);
            assertEquals(LocalDate.of(2026, 10, 19), j.inicio(), dia.toString());
            assertEquals(LocalDate.of(2026, 10, 25), j.fim(), dia.toString());
            assertEquals(7, j.buckets().size());
            assertEquals(Granularidade.DIA, j.granularidade());
        }
        JanelaProdutividade j = janela(PeriodoProdutividadeEnum.SEMANA, HOJE, HOJE);
        Bucket segunda = j.buckets().get(0);
        assertEquals("2026-10-19", segunda.chave());
        assertEquals("Seg", segunda.rotulo());
        assertEquals("19", segunda.rotuloSecundario());
        assertEquals("Dom", j.buckets().get(6).rotulo());
        assertEquals("Sáb", j.buckets().get(5).rotulo());
        assertEquals("19 a 25 de outubro de 2026", j.rotuloPeriodo());
    }

    @Test
    void semana_CruzandoMesEAnoTemRotuloComAsDuasPontas() {
        JanelaProdutividade mes = janela(PeriodoProdutividadeEnum.SEMANA, LocalDate.of(2026, 10, 29), HOJE);
        assertEquals(LocalDate.of(2026, 10, 26), mes.inicio());
        assertEquals(LocalDate.of(2026, 11, 1), mes.fim());
        assertEquals("26 de outubro a 1 de novembro de 2026", mes.rotuloPeriodo());

        JanelaProdutividade ano = janela(PeriodoProdutividadeEnum.SEMANA, LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 5));
        assertEquals(LocalDate.of(2026, 12, 28), ano.inicio());
        assertEquals(LocalDate.of(2027, 1, 3), ano.fim());
        assertEquals("28 de dezembro de 2026 a 3 de janeiro de 2027", ano.rotuloPeriodo());
        assertEquals("2027-01-01", ano.buckets().get(4).chave());
    }

    @Test
    void semanaEmAndamento_CortaEmHojeEComparaComOMesmoTrechoDaSemanaAnterior() {
        JanelaProdutividade j = janela(PeriodoProdutividadeEnum.SEMANA, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 22)); // quinta
        assertEquals(LocalDate.of(2026, 10, 22), j.corte());
        JanelaProdutividade anterior = j.anterior();
        assertEquals(LocalDate.of(2026, 10, 12), anterior.inicio());
        assertEquals(LocalDate.of(2026, 10, 18), anterior.fim());
        assertEquals(LocalDate.of(2026, 10, 15), anterior.corte());
        assertEquals("vs semana anterior (mesmo intervalo: seg a qui)", j.textoVariacao());
    }

    @Test
    void mesEmAndamento_ComparaDia1Ao23ComSetembro() {
        JanelaProdutividade j = janela(PeriodoProdutividadeEnum.MES, HOJE, HOJE);
        assertEquals(LocalDate.of(2026, 10, 1), j.inicio());
        assertEquals(LocalDate.of(2026, 10, 31), j.fim());
        assertEquals(31, j.buckets().size());
        assertEquals(HOJE, j.corte());
        assertEquals("Outubro de 2026", j.rotuloPeriodo());
        assertEquals(LocalDate.of(2026, 9, 1), j.anterior().inicio());
        assertEquals(LocalDate.of(2026, 9, 30), j.anterior().fim());
        assertEquals(LocalDate.of(2026, 9, 23), j.anterior().corte());
        assertEquals("vs mês anterior (mesmo intervalo: dias 1 a 23)", j.textoVariacao());
        assertEquals(30, j.anterior().buckets().size());
    }

    @Test
    void mesMaisLongoQueOAnterior_LimitaAoUltimoDiaDoAnterior() {
        JanelaProdutividade j = janela(PeriodoProdutividadeEnum.MES, LocalDate.of(2026, 3, 30), LocalDate.of(2026, 3, 30));
        assertEquals(LocalDate.of(2026, 2, 28), j.anterior().corte(), "fevereiro tem 28 dias");
        assertEquals(LocalDate.of(2026, 2, 28), j.anterior().fim());
    }

    @Test
    void mesJaEncerrado_ComparaOMesAnteriorInteiroMesmoQueSejaMaisCurto() {
        JanelaProdutividade fev = janela(PeriodoProdutividadeEnum.MES, LocalDate.of(2026, 2, 10), HOJE);
        assertEquals(LocalDate.of(2026, 2, 28), fev.corte());
        assertEquals(LocalDate.of(2026, 1, 31), fev.anterior().corte(), "período passado compara inteiro, não só 28 dias");
        assertEquals("vs mês anterior", fev.textoVariacao());

        JanelaProdutividade mar = janela(PeriodoProdutividadeEnum.MES, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 31));
        assertEquals(LocalDate.of(2026, 2, 28), mar.anterior().corte());
    }

    @Test
    void anoBissexto_FevereiroTem29Dias() {
        JanelaProdutividade j = janela(PeriodoProdutividadeEnum.MES, LocalDate.of(2028, 2, 10), LocalDate.of(2028, 2, 10));
        assertEquals(LocalDate.of(2028, 2, 29), j.fim());
        assertEquals(29, j.buckets().size());
        assertEquals(LocalDate.of(2028, 2, 10), j.corte());

        JanelaProdutividade marco = janela(PeriodoProdutividadeEnum.MES, LocalDate.of(2028, 3, 15), LocalDate.of(2028, 3, 15));
        assertEquals(LocalDate.of(2028, 2, 15), marco.anterior().corte());
        JanelaProdutividade mar31 = janela(PeriodoProdutividadeEnum.MES, LocalDate.of(2028, 3, 31), LocalDate.of(2028, 3, 31));
        assertEquals(LocalDate.of(2028, 2, 29), mar31.anterior().corte());
    }

    @Test
    void ano_TemDozePontosMensaisECompara1JanA23OutComOAnoAnterior() {
        JanelaProdutividade j = janela(PeriodoProdutividadeEnum.ANO, HOJE, HOJE);
        assertEquals(Granularidade.MES, j.granularidade());
        assertEquals(12, j.buckets().size());
        assertEquals("2026", j.rotuloPeriodo());
        assertEquals("2026-01-01", j.buckets().get(0).chave());
        assertEquals("Jan", j.buckets().get(0).rotulo());
        assertNull(j.buckets().get(0).rotuloSecundario());
        assertEquals(LocalDate.of(2026, 10, 31), j.buckets().get(9).fim());
        assertEquals(HOJE, j.corte());
        assertEquals(LocalDate.of(2025, 10, 23), j.anterior().corte());
        assertEquals("vs ano anterior (mesmo intervalo: 1 jan a 23 out)", j.textoVariacao());
        assertTrue(j.buckets().get(10).futuro(HOJE), "novembro ainda não começou");
        assertFalse(j.buckets().get(9).futuro(HOJE), "outubro está em andamento");
    }

    @Test
    void anoPassado_ComparaInteiro() {
        JanelaProdutividade j = janela(PeriodoProdutividadeEnum.ANO, LocalDate.of(2025, 6, 1), HOJE);
        assertEquals(LocalDate.of(2025, 12, 31), j.corte());
        assertEquals(LocalDate.of(2024, 12, 31), j.anterior().corte());
        assertEquals("vs ano anterior", j.textoVariacao());
    }

    @Test
    void todos_UmPontoPorAnoESemPeriodoAnterior() {
        JanelaProdutividade j = janela(PeriodoProdutividadeEnum.TODOS, HOJE, HOJE);
        assertEquals(4, j.buckets().size());
        assertEquals("2023", j.buckets().get(0).rotulo());
        assertEquals("2023-01-01", j.buckets().get(0).chave());
        assertEquals(Granularidade.ANO, j.granularidade());
        assertEquals(LocalDate.of(2023, 1, 1), j.inicio());
        assertEquals(LocalDate.of(2026, 12, 31), j.fim());
        assertEquals(HOJE, j.corte());
        assertNull(j.anterior());
        assertNull(j.textoVariacao());
        assertEquals("Todos os anos", j.rotuloPeriodo());
    }

    @Test
    void periodoFuturo_NaoTemNadaDecorrido() {
        JanelaProdutividade j = janela(PeriodoProdutividadeEnum.MES, LocalDate.of(2026, 12, 10), HOJE);
        assertTrue(j.semDecorrido());
        assertEquals(LocalDate.of(2026, 11, 30), j.corte());
        assertTrue(j.anterior().semDecorrido(), "o anterior alinhado também não tem nada decorrido");
        assertEquals("vs mês anterior", j.textoVariacao());
        assertTrue(j.buckets().get(0).futuro(HOJE));
    }

    @Test
    void metricaQueProjetaOFuturo_VaiAteOFimEComparaComOAnteriorInteiro() {
        JanelaProdutividade j = JanelaProdutividade.de(PeriodoProdutividadeEnum.MES, HOJE, HOJE, true, 2023, 2026);
        assertEquals(LocalDate.of(2026, 10, 31), j.corte());
        assertEquals(LocalDate.of(2026, 9, 30), j.anterior().corte());
        assertEquals("vs mês anterior (período inteiro)", j.textoVariacao());
        assertTrue(j.buckets().get(30).futuro(HOJE), "dia 31 é futuro mesmo com o corte no fim");
    }
}
