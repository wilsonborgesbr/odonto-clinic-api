package com.example.demo.controller;

import com.example.demo.dto.AtualizarPreferenciasDTO;
import com.example.demo.dto.AtualizarUsuarioDTO;
import com.example.demo.dto.CriarUsuarioDTO;
import com.example.demo.dto.UsuarioDTO;
import com.example.demo.enums.RoleEnum;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.service.UserLookupService;
import com.example.demo.util.FormatadorTexto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Gestão dos usuários (logins) da clínica. Só usuários com permissão
 * {@code USUARIOS_E_PERMISSOES} podem operar — na prática, PROPRIETARIO
 * e SOCIO (e ADMINISTRADOR se explicitamente delegado).
 */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários", description = "CRUD de usuários da clínica + RBAC")
public class UsuarioController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLookupService userLookupService;

    @Autowired
    private MongoTemplate mongoTemplate;

    private void requireGestaoUsuarios() {
        var atual = AuthContextHelper.currentUser();
        if (!atual.permissoesEfetivas().contains(com.example.demo.enums.PermissaoEnum.USUARIOS_E_PERMISSOES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para gerenciar usuários.");
        }
    }

    @GetMapping
    public ResponseEntity<List<UsuarioDTO>> listar() {
        requireGestaoUsuarios();
        String clinicaId = AuthContextHelper.currentClinicaId();
        List<UsuarioDTO> lista = userRepository.findByClinicaId(clinicaId).stream()
                .map(UsuarioDTO::from)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> me() {
        // Endpoint aberto a qualquer usuário logado — usado pelo frontend
        // para reconciliar suas permissões após ações que alterem o próprio user.
        return ResponseEntity.ok(UsuarioDTO.from(AuthContextHelper.currentUser()));
    }

    @PatchMapping("/me/preferences")
    public ResponseEntity<UsuarioDTO> atualizarPreferencias(@RequestBody @Valid AtualizarPreferenciasDTO dto) {
        if (dto.vazio()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos uma preferência: hideFinancialInfo ou theme.");
        }

        String userId = AuthContextHelper.currentUserId();
        String clinicaId = AuthContextHelper.currentClinicaId();

        // $set por campo e atômico: PATCHs simultâneos de campos diferentes não se sobrescrevem.
        Update update = new Update().set("updatedAt", LocalDateTime.now());
        if (dto.getHideFinancialInfo() != null) {
            update.set("preferences.hideFinancialInfo", dto.getHideFinancialInfo());
        }
        if (dto.getTheme() != null) {
            update.set("preferences.theme", dto.getTheme());
        }

        Query ativoDaClinica = Query.query(Criteria.where("id").is(userId)
                .and("clinicaId").is(clinicaId)
                .and("ativo").ne(false));
        User atualizado = mongoTemplate.findAndModify(ativoDaClinica, update,
                FindAndModifyOptions.options().returnNew(true), User.class);
        if (atualizado != null) {
            return ResponseEntity.ok(UsuarioDTO.from(atualizado));
        }

        User existente = userRepository.findById(userId)
                .filter(u -> clinicaId.equals(u.getClinicaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                Boolean.TRUE.equals(existente.getAtivo()) ? "Não foi possível atualizar as preferências." : "Usuário inativo.");
    }

    @PostMapping
    public ResponseEntity<UsuarioDTO> criar(@RequestBody @Valid CriarUsuarioDTO dto) {
        requireGestaoUsuarios();
        String clinicaId = AuthContextHelper.currentClinicaId();

        String email = FormatadorTexto.email(dto.getEmail());
        if (userLookupService.emailEmUso(email, clinicaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail já cadastrado nesta clínica.");
        }
        if (dto.getRole() == RoleEnum.PROPRIETARIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Não é possível criar outro proprietário. Use SOCIO para dar controle equivalente.");
        }

        String encoded = new BCryptPasswordEncoder().encode(dto.getPassword());
        User novo = User.builder()
                .name(dto.getName())
                .email(email)
                .password(encoded)
                .clinicaId(clinicaId)
                .role(dto.getRole())
                .permissoes(dto.getPermissoes())
                .ativo(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User salvo = userRepository.save(novo);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioDTO.from(salvo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioDTO> atualizar(@PathVariable String id, @RequestBody @Valid AtualizarUsuarioDTO dto) {
        requireGestaoUsuarios();
        String clinicaId = AuthContextHelper.currentClinicaId();
        User currentUser = AuthContextHelper.currentUser();

        User user = userRepository.findById(id)
                .filter(u -> clinicaId.equals(u.getClinicaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        // Regras de negócio de segurança:
        boolean editandoProprietario = user.getRole() == RoleEnum.PROPRIETARIO;
        boolean editandoASiMesmo = user.getId().equals(currentUser.getId());
        if (editandoProprietario && !editandoASiMesmo) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o proprietário pode editar seus próprios dados.");
        }
        if (editandoProprietario && dto.getRole() != RoleEnum.PROPRIETARIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível remover o papel de proprietário.");
        }
        if (!editandoProprietario && dto.getRole() == RoleEnum.PROPRIETARIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só existe um proprietário por clínica.");
        }

        String email = FormatadorTexto.email(dto.getEmail());
        if (!user.getEmail().equalsIgnoreCase(email) && userLookupService.emailEmUso(email, clinicaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail já cadastrado nesta clínica.");
        }

        user.setName(dto.getName());
        user.setEmail(email);
        user.setRole(dto.getRole());
        user.setPermissoes(dto.getPermissoes());
        if (dto.getNovaSenha() != null && !dto.getNovaSenha().isBlank()) {
            user.setPassword(new BCryptPasswordEncoder().encode(dto.getNovaSenha()));
        }
        user.setUpdatedAt(LocalDateTime.now());

        return ResponseEntity.ok(UsuarioDTO.from(userRepository.save(user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable String id) {
        requireGestaoUsuarios();
        String clinicaId = AuthContextHelper.currentClinicaId();

        User user = userRepository.findById(id)
                .filter(u -> clinicaId.equals(u.getClinicaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        if (user.getRole() == RoleEnum.PROPRIETARIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível inativar o proprietário.");
        }
        if (user.getId().equals(AuthContextHelper.currentUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível inativar a si mesmo.");
        }

        user.setAtivo(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reativar")
    public ResponseEntity<UsuarioDTO> reativar(@PathVariable String id) {
        requireGestaoUsuarios();
        String clinicaId = AuthContextHelper.currentClinicaId();

        User user = userRepository.findById(id)
                .filter(u -> clinicaId.equals(u.getClinicaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        user.setAtivo(true);
        user.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(UsuarioDTO.from(userRepository.save(user)));
    }
}
