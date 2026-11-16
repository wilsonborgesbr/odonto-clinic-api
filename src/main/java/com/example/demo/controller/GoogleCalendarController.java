package com.example.demo.controller;

import com.example.demo.dto.GoogleCalendarDTOs.AuthUrlResponse;
import com.example.demo.dto.GoogleCalendarDTOs.ConexaoResponse;
import com.example.demo.dto.GoogleCalendarDTOs.SincronizarResponse;
import com.example.demo.dto.GoogleCalendarDTOs.StatusResponse;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.googlecalendar.ConexaoGoogleService;
import com.example.demo.googlecalendar.GoogleCalendarProperties;
import com.example.demo.googlecalendar.SincronizacaoManualService;
import com.example.demo.model.User;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.service.RateLimitService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;

/**
 * Integração com o Google Calendar. Tudo exige a permissão {@code INTEGRACAO_GOOGLE_CALENDAR}, exceto o retorno
 * do Google ({@code /callback}, que chega do navegador sem JWT e é autenticado pelo {@code state} assinado),
 * a consulta de status (qualquer usuário logado) e desconectar a própria conexão.
 */
@RestController
@RequestMapping("/api/google-calendar")
@Tag(name = "Google Calendar", description = "Conexão OAuth e sincronização de agendamentos com o Google Calendar")
public class GoogleCalendarController {


    private static final Duration JANELA_CALLBACK = Duration.ofMinutes(10);

    @org.springframework.beans.factory.annotation.Value("${google.calendar.callback-max-per-ip:30}")
    private int maxCallbacksPorIp;

    @Autowired
    private ConexaoGoogleService conexaoService;
    @Autowired
    private SincronizacaoManualService sincronizacaoManual;
    @Autowired
    private GoogleCalendarProperties props;
    @Autowired
    private RateLimitService rateLimitService;

    @GetMapping("/auth-url")
    public ResponseEntity<AuthUrlResponse> authUrl() {
        return ResponseEntity.ok(conexaoService.gerarUrlAutorizacao(AuthContextHelper.currentUser()));
    }

    /**
     * O Google devolve o navegador para cá. Em qualquer resultado, redireciona para a tela de configurações com
     * {@code ?googleCalendar=conectado} ou {@code ?googleCalendar=erro&motivo=<codigo>}.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "error", required = false) String error,
                                         HttpServletRequest request) {
        ConexaoGoogleService.Resultado resultado;
        if (!rateLimitService.registrarSePermitido("gcal-callback:ip:" + request.getRemoteAddr(),
                maxCallbacksPorIp, JANELA_CALLBACK)) {
            resultado = new ConexaoGoogleService.Resultado(false, "muitas_tentativas");
        } else {
            resultado = conexaoService.concluirAutorizacao(code, state, error);
        }

        UriComponentsBuilder destino = UriComponentsBuilder
                .fromUriString(props.frontendUrl().replaceAll("/+$", "") + props.caminhoRetornoFrontend())
                .queryParam("googleCalendar", resultado.sucesso() ? "conectado" : "erro");
        if (!resultado.sucesso()) {
            destino.queryParam("motivo", resultado.motivo());
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, destino.build().encode().toUriString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @GetMapping("/status")
    public ResponseEntity<StatusResponse> status() {
        return ResponseEntity.ok(conexaoService.status(AuthContextHelper.currentUser()));
    }

    /** Todas as conexões da clínica (sem tokens), para a tela de usuários do dono. */
    @GetMapping("/conexoes")
    public ResponseEntity<List<ConexaoResponse>> conexoes() {
        User atual = AuthContextHelper.currentUser();
        if (!atual.permissoesEfetivas().contains(PermissaoEnum.USUARIOS_E_PERMISSOES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para gerenciar usuários.");
        }
        return ResponseEntity.ok(conexaoService.listar(AuthContextHelper.currentClinicaId()));
    }

    /**
     * "Sincronizar agora" da conexão do usuário logado (só com syncStatus ATIVO). Responde 202 na hora; o trabalho roda
     * em segundo plano e o frontend acompanha em GET /status (conexao.sincronizando, ultimaSincronizacaoEm).
     */
    @PostMapping("/sincronizar")
    public ResponseEntity<SincronizarResponse> sincronizar() {
        return ResponseEntity.accepted().body(sincronizacaoManual.iniciar(AuthContextHelper.currentUser()));
    }

    @PostMapping("/desconectar")
    public ResponseEntity<Void> desconectar() {
        conexaoService.desconectar(AuthContextHelper.currentUser());
        return ResponseEntity.noContent().build();
    }
}
