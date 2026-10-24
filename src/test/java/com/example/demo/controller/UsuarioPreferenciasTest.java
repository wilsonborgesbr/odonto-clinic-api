package com.example.demo.controller;

import com.example.demo.dto.AtualizarPreferenciasDTO;
import com.example.demo.dto.UsuarioDTO;
import com.example.demo.enums.RoleEnum;
import com.example.demo.model.User;
import com.example.demo.model.UserPreferences;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantTestSupport;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.bson.Document;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioPreferenciasTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private UserRepository userRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private UsuarioController usuarioController;

    @BeforeEach
    void setUp() {
        TenantTestSupport.loginTestUser();
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    private User usuario(UserPreferences prefs) {
        return User.builder()
                .id("user-test").name("Test User").email("test@bokka.dev")
                .clinicaId(TEST_CLINICA_ID).role(RoleEnum.PROPRIETARIO).ativo(true)
                .preferences(prefs).build();
    }

    private void mongoDevolve(User atualizado) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(atualizado);
    }

    private Update updateEnviado() {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(User.class));
        return update.getValue();
    }

    private Document camposGravados() {
        return updateEnviado().getUpdateObject().get("$set", Document.class);
    }

    private AtualizarPreferenciasDTO dto(Boolean hide, String theme) {
        AtualizarPreferenciasDTO dto = new AtualizarPreferenciasDTO();
        dto.setHideFinancialInfo(hide);
        dto.setTheme(theme);
        return dto;
    }

    @Test
    void soTheme_FazSetApenasDoThemeSemTocarNoHide() {
        mongoDevolve(usuario(UserPreferences.builder().hideFinancialInfo(true).theme("dark").build()));

        UsuarioDTO r = usuarioController.atualizarPreferencias(dto(null, "dark")).getBody();

        Document set = camposGravados();
        assertEquals("dark", set.get("preferences.theme"));
        assertFalse(set.containsKey("preferences.hideFinancialInfo"));
        assertFalse(set.containsKey("preferences"), "nunca grava o subdocumento inteiro");
        assertEquals("dark", r.getPreferences().getTheme());
        assertTrue(r.getPreferences().getHideFinancialInfo());
    }

    @Test
    void soHideFinancialInfo_ComoOFrontendAtualEnviaFazSetApenasDele() {
        mongoDevolve(usuario(UserPreferences.builder().hideFinancialInfo(true).theme("system").build()));

        UsuarioDTO r = usuarioController.atualizarPreferencias(dto(true, null)).getBody();

        Document set = camposGravados();
        assertEquals(true, set.get("preferences.hideFinancialInfo"));
        assertFalse(set.containsKey("preferences.theme"));
        assertTrue(r.getPreferences().getHideFinancialInfo());
        assertEquals("system", r.getPreferences().getTheme());
    }

    @Test
    void ambosCampos_FazSetDosDoisCampos() {
        mongoDevolve(usuario(UserPreferences.builder().hideFinancialInfo(true).theme("dark").build()));

        UsuarioDTO r = usuarioController.atualizarPreferencias(dto(true, "dark")).getBody();

        Document set = camposGravados();
        assertEquals(true, set.get("preferences.hideFinancialInfo"));
        assertEquals("dark", set.get("preferences.theme"));
        assertTrue(r.getPreferences().getHideFinancialInfo());
        assertEquals("dark", r.getPreferences().getTheme());
    }

    @Test
    void hideFinancialInfoFalse_ValeComoCampoEnviadoENaoComoVazio() {
        mongoDevolve(usuario(UserPreferences.builder().hideFinancialInfo(false).build()));

        UsuarioDTO r = usuarioController.atualizarPreferencias(dto(false, null)).getBody();

        assertEquals(false, camposGravados().get("preferences.hideFinancialInfo"));
        assertFalse(r.getPreferences().getHideFinancialInfo());
    }

    @Test
    void filtroDaConsulta_UsaIdEClinicaDoJwtEIgnoraInativo() {
        mongoDevolve(usuario(null));

        usuarioController.atualizarPreferencias(dto(null, "dark"));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(User.class));
        Document filtro = query.getValue().getQueryObject();
        assertEquals("user-test", filtro.get("id"));
        assertEquals(TEST_CLINICA_ID, filtro.get("clinicaId"));
        assertEquals(new Document("$ne", false), filtro.get("ativo"));
    }

    @Test
    void usaReturnNewEUpdatedAtNoMesmoUpdateAtomico() {
        mongoDevolve(usuario(null));

        usuarioController.atualizarPreferencias(dto(true, null));

        ArgumentCaptor<FindAndModifyOptions> opcoes = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(any(Query.class), any(Update.class), opcoes.capture(), eq(User.class));
        assertTrue(opcoes.getValue().isReturnNew());
        assertTrue(camposGravados().containsKey("updatedAt"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void corpoVazio_Retorna400SemTocarNoBanco() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> usuarioController.atualizarPreferencias(dto(null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(userRepository, mongoTemplate);
    }

    @Test
    void usuarioAntigoSemPreferencias_RespostaTemThemeLightEHideFalse() {
        mongoDevolve(usuario(null));

        UsuarioDTO r = usuarioController.atualizarPreferencias(dto(null, "dark")).getBody();

        assertEquals("light", r.getPreferences().getTheme());
        assertFalse(r.getPreferences().getHideFinancialInfo());
    }

    @Test
    void usuarioInativo_Retorna403() {
        User inativo = usuario(null);
        inativo.setAtivo(false);
        mongoDevolve(null);
        when(userRepository.findById("user-test")).thenReturn(Optional.of(inativo));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> usuarioController.atualizarPreferencias(dto(null, "dark")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Usuário inativo.", ex.getReason());
    }

    @Test
    void usuarioInexistenteOuDeOutraClinica_Retorna404() {
        mongoDevolve(null);
        when(userRepository.findById("user-test")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> usuarioController.atualizarPreferencias(dto(null, "dark"))).getStatusCode());

        User deOutra = usuario(null);
        deOutra.setClinicaId("outra-clinica");
        when(userRepository.findById("user-test")).thenReturn(Optional.of(deOutra));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> usuarioController.atualizarPreferencias(dto(true, null))).getStatusCode());
    }

    @Test
    void usuarioDTO_SemPreferenciasOuSemOCampoThemeDevolveLight() {
        assertEquals("light", UsuarioDTO.from(usuario(null)).getPreferences().getTheme());

        UserPreferences antigo = new UserPreferences();
        antigo.setTheme(null);
        assertEquals("light", UsuarioDTO.from(usuario(antigo)).getPreferences().getTheme());
        assertEquals("light", new UserPreferences().getTheme());
        assertFalse(new UserPreferences().getHideFinancialInfo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"light", "dark", "system"})
    void validacao_AceitaOsTresTemas(String tema) {
        assertTrue(VALIDATOR.validate(dto(null, tema)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DARK", "Light", "azul", "", " ", "dark ", "auto"})
    void validacao_RejeitaTemaInvalido(String tema) {
        var erros = VALIDATOR.validate(dto(null, tema));

        assertEquals(1, erros.size(), tema);
        assertEquals("theme", erros.iterator().next().getPropertyPath().toString());
    }

    @Test
    void validacao_CamposAusentesSaoValidosParaAValidacaoDeCampo() {
        assertTrue(VALIDATOR.validate(dto(null, null)).isEmpty());
        assertTrue(dto(null, null).vazio());
        assertFalse(dto(false, null).vazio());
        assertFalse(dto(null, "dark").vazio());
    }
}
