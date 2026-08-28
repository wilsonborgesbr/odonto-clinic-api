package com.example.demo.config;

import com.example.demo.enums.RoleEnum;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Seed idempotente para bootstrap de contas de desenvolvimento/demonstração.
 * Roda apenas quando {@code bokka.seed.dev-users=true} (env var {@code BOKKA_SEED_DEV_USERS}).
 *
 * <p>Cria 3 logins em 2 clínicas:
 * <ul>
 *   <li><b>bokka-dev</b> — proprietário Wilson (playground do desenvolvedor)</li>
 *   <li><b>consultoriotainahsantos</b> — proprietária Tainah</li>
 *   <li>Wilson como SÓCIO em consultoriotainahsantos (controle equivalente ao proprietário)</li>
 * </ul>
 *
 * <p>Roda DEPOIS do {@link CleanDbOnStartup} (que dropa o banco se ativado).
 * Se um user ou clínica já existir, é pulado — pode rodar quantas vezes quiser.
 */
@Component
@Order(2)
public class DevSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    @Value("${bokka.seed.dev-users:false}")
    private boolean seedEnabled;

    @Value("${bokka.seed.wilson-password:bokka123}")
    private String wilsonPassword;

    @Value("${bokka.seed.tainah-password:tainah123}")
    private String tainahPassword;

    private final ClinicaRepository clinicaRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DevSeedRunner(ClinicaRepository clinicaRepository, UserRepository userRepository) {
        this.clinicaRepository = clinicaRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;

        log.warn("=".repeat(70));
        log.warn("[bokka-seed] bokka.seed.dev-users=true — criando contas dev");
        log.warn("=".repeat(70));

        // 1) Clínica "bokka-dev" — playground do desenvolvedor
        Clinica bokkaDev = ensureClinica(
                "bokka-dev",
                "Bokka · Desenvolvimento",
                "will.boorges3838@gmail.com"
        );
        ensureUser(
                bokkaDev,
                "Wilson Borges",
                "wilson@bokka.dev",
                wilsonPassword,
                RoleEnum.PROPRIETARIO,
                "Proprietário da clínica de desenvolvimento (playground)"
        );

        // 2) Clínica "consultoriotainahsantos" — proprietária Tainah
        Clinica consultorioTainah = ensureClinica(
                "consultoriotainahsantos",
                "Consultório Dra. Tainah Santos",
                "tainah@consultoriotainahsantos.com"
        );
        ensureUser(
                consultorioTainah,
                "Dra. Tainah Santos",
                "tainah@consultoriotainahsantos.com",
                tainahPassword,
                RoleEnum.PROPRIETARIO,
                "Proprietária do consultório"
        );

        // 3) Wilson como SÓCIO no consultório da Tainah — controle total equivalente
        ensureUser(
                consultorioTainah,
                "Wilson Borges",
                "wilson@consultoriotainahsantos.com",
                wilsonPassword,
                RoleEnum.SOCIO,
                "Sócio (mesmo poder do proprietário)"
        );

        log.warn("=".repeat(70));
        log.warn("[bokka-seed] Contas dev criadas. Faça login com:");
        log.warn("");
        log.warn("  Clínica de dev (playground)");
        log.warn("    código:  bokka-dev");
        log.warn("    email:   wilson@bokka.dev");
        log.warn("    senha:   {}", wilsonPassword);
        log.warn("");
        log.warn("  Consultório da Tainah (proprietária)");
        log.warn("    código:  consultoriotainahsantos");
        log.warn("    email:   tainah@consultoriotainahsantos.com");
        log.warn("    senha:   {}", tainahPassword);
        log.warn("");
        log.warn("  Consultório da Tainah (Wilson como sócio)");
        log.warn("    código:  consultoriotainahsantos");
        log.warn("    email:   wilson@consultoriotainahsantos.com");
        log.warn("    senha:   {}", wilsonPassword);
        log.warn("");
        log.warn("[bokka-seed] Depois de logar, TROQUE as senhas em Meu Perfil.");
        log.warn("[bokka-seed] Volte bokka.seed.dev-users=false pra evitar log ruidoso.");
        log.warn("=".repeat(70));
    }

    private Clinica ensureClinica(String codigo, String nome, String emailContato) {
        Optional<Clinica> existente = clinicaRepository.findByCodigo(codigo);
        if (existente.isPresent()) {
            log.info("[bokka-seed] clínica '{}' já existe — pulando", codigo);
            return existente.get();
        }
        Clinica c = Clinica.builder()
                .codigo(codigo)
                .nome(nome)
                .emailContato(emailContato)
                .ativo(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Clinica salva = clinicaRepository.save(c);
        log.warn("[bokka-seed] clínica CRIADA · código='{}' · nome='{}'", codigo, nome);
        return salva;
    }

    private void ensureUser(Clinica clinica, String nome, String email, String senha,
                            RoleEnum role, String descricao) {
        boolean exists = userRepository.existsByEmailAndClinicaId(email, clinica.getId());
        if (exists) {
            log.info("[bokka-seed] usuário '{}' na clínica '{}' já existe — pulando",
                    email, clinica.getCodigo());
            return;
        }
        User u = User.builder()
                .name(nome)
                .email(email)
                .password(encoder.encode(senha))
                .clinicaId(clinica.getId())
                .role(role)
                .ativo(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(u);
        log.warn("[bokka-seed] usuário CRIADO · {} · clínica='{}' · {}",
                email, clinica.getCodigo(), descricao);
    }
}
