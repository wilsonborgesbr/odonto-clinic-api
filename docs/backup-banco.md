# Backup do banco (MongoDB Atlas) — como funciona e como restaurar

> **ATENÇÃO, Wilson: a CHAVE PRIVADA é a única forma de abrir os backups.**
> Arquivo: `C:\Users\Wilson\Desktop\CODE\bokka-backup-chave-privada` (fora do Git, só o seu usuário do Windows lê).
> Ela **nunca** esteve na VPS, e **não existe cópia dela em nenhum outro lugar**. Se você perder esse arquivo, **todos os backups
> viram lixo ilegível** e não há como recuperar. Faça agora:
> 1. guarde uma cópia no **gerenciador de senhas** (como anexo ou texto seguro);
> 2. guarde uma **cópia offline** (pen drive ou HD externo guardado em lugar seguro, fora do computador);
> 3. **nunca** envie a chave por chat, e-mail, Git, nem coloque em pasta sincronizada com a nuvem sem proteção.
>
> Se alguém roubar a chave privada **e** um arquivo de backup, consegue ler o banco. Trate como a senha mestra do banco.

## 1. Como funciona

| Item | Valor |
|---|---|
| O que é salvo | o banco inteiro `sistema_clinica` do Atlas (15 coleções), com índices |
| Quando | **todo domingo às 03:00 (horário de Brasília, `America/Sao_Paulo`)**, por um timer do systemd na VPS |
| Se a VPS estiver desligada na hora | roda assim que ligar (`Persistent=true`) |
| Ferramenta de dump | `mongodump --archive --gzip` (MongoDB Database Tools 100.13, em `/opt/bokka/tools`) |
| Ferramenta de criptografia | `openssl cms -encrypt` (OpenSSL 3.0.13, **já vinha na VPS**; nada foi instalado), AES-256 + RSA-4096 |
| Chave **pública** (cifra) | na VPS, `/etc/bokka-backup/backup-public.pem` (pode vazar sem risco: só serve para trancar) |
| Chave **privada** (abre) | **só** na máquina do Wilson: `C:\Users\Wilson\Desktop\CODE\bokka-backup-chave-privada` |
| Onde ficam os backups | `/opt/bokka/backups-semanais` (pasta `700`, dono `root`, arquivos `600`) |
| Nome do arquivo | `bokka-sistema_clinica-AAAAMMDD-HHMMSS.archive.gz.cms` (data/hora em **UTC**) |
| Retenção | os **4 mais recentes**; os mais antigos são apagados pelo nome exato, nada além do padrão acima |
| Segurança do processo | o dump **nunca** toca o disco sem criptografia (vai direto por um pipe para o `openssl`); a URI do Mongo vai para um arquivo temporário `600` em `/dev/shm`, apagado no fim, nunca em argumento de comando |
| Registro de sucesso/falha | journal do systemd e arquivo `ULTIMO-STATUS.txt` na pasta dos backups |

Arquivos na VPS:

- Script: `/usr/local/sbin/bokka-backup-semanal.sh` (700, root)
- Service/Timer: `/etc/systemd/system/bokka-backup-semanal.service` e `.timer`
- Chave pública: `/etc/bokka-backup/backup-public.pem`

> **Limite importante:** os backups ficam **na própria VPS**. Se a VPS inteira for perdida, os backups vão junto.
> Como eles são criptografados, é seguro copiá-los para fora (Google Drive, HD externo): baixe de vez em quando
> (passo 1 abaixo) e guarde uma cópia. O Atlas M0 (gratuito) **não** faz backup automático.

## 2. Como saber se o backup rodou (comandos para o DevOps)

```
systemctl list-timers bokka-backup-semanal.timer
journalctl -u bokka-backup-semanal.service --since "-8days" --no-pager -o cat
sudo cat /opt/bokka/backups-semanais/ULTIMO-STATUS.txt
sudo ls -la /opt/bokka/backups-semanais
```

- Sucesso: linha `bokka-backup: OK: <arquivo> tamanho=... colecoes=15 documentos=...` e o status começa com `OK`.
- Falha: linha `bokka-backup: FALHA: ...`, o service fica `failed` e `ULTIMO-STATUS.txt` começa com `FALHA`
  (também registrada com prioridade de erro: `journalctl -t bokka-backup -p err`).
- Rodar um backup agora (sem esperar domingo): `sudo systemctl start bokka-backup-semanal.service`.

## 3. Como RESTAURAR, passo a passo (Windows)

Faça tudo na **sua máquina**, onde está a chave privada. Use o **Prompt de Comando (cmd)** para o passo 1: o PowerShell
estraga arquivos binários ao redirecionar com `>`.

**Passo 1 — baixar o backup (continua criptografado).** Escolha o arquivo (o mais recente é o de data maior):

```
ssh -i C:\Users\Wilson\.ssh\bokka_vps_ed25519 ubuntu@151.247.211.31 "sudo ls -1 /opt/bokka/backups-semanais"
```

```
cmd /c "ssh -i C:\Users\Wilson\.ssh\bokka_vps_ed25519 ubuntu@151.247.211.31 "sudo cat /opt/bokka/backups-semanais/bokka-sistema_clinica-AAAAMMDD-HHMMSS.archive.gz.cms" > C:\restore\backup.cms"
```

(crie antes a pasta `C:\restore`). Confira o tamanho: deve ser igual ao do arquivo na VPS.

**Passo 2 — abrir (decifrar) com a chave privada.** Usa o OpenSSL que vem com o Git for Windows:

```
"C:\Program Files\Git\usr\bin\openssl.exe" cms -decrypt -binary -inform DER -in C:\restore\backup.cms -inkey C:\Users\Wilson\Desktop\CODE\bokka-backup-chave-privada -out C:\restore\dump.archive.gz
```

O resultado `dump.archive.gz` **está em texto claro** (dados pessoais de pacientes): **apague depois de usar**.

**Passo 3 — restaurar num banco TEMPORÁRIO para conferir (recomendado sempre):**

```
docker exec -i bokka-mongo mongorestore --archive --gzip --nsFrom "sistema_clinica.*" --nsTo "restaurado_teste.*" < C:\restore\dump.archive.gz
```

Confira no mongosh (`docker exec -it bokka-mongo mongosh restaurado_teste`): `db.getCollectionNames()` e `db.pacientes.countDocuments({})`.
Depois apague o temporário: `db.dropDatabase()`.

**Passo 4 — recuperar uma coleção/documento no Atlas (só com autorização do Wilson).**
Restaurar no Atlas **sobrescreve dados reais**. Nunca use `--drop` sem entender o efeito. O caminho seguro é: restaurar no banco
temporário (passo 3), conferir, e então copiar só o que falta (por exemplo `mongodump` de uma coleção do temporário e
`mongorestore` dela no Atlas), com o app avisado e um backup novo feito antes. Se for um desastre total (banco perdido),
chame o DevOps: ele restaura com `mongorestore --archive --gzip` apontando para o Atlas, usando a URI por arquivo de configuração
(`--config`), nunca na linha de comando.

**Passo 5 — limpeza.** Apague `C:\restore` inteiro (contém dados em texto claro depois do passo 2).

## 4. Testes já feitos (2026-10-26)

- Dump imediato criptografado: 15 coleções, 1136 documentos; contagem por coleção **idêntica** à do Atlas.
- Arquivo conferido: sem nenhuma palavra do conteúdo legível; só o cabeçalho CMS com `CN=bokka-backup` (nome da chave).
- Restauração em banco temporário do Docker local (`tmp_restore_...`): **1136 documentos restaurados, 0 falhas**; 14 das 15 coleções
  idênticas ao Atlas (contagem, hash do conteúdo e índices); a 15ª (`auth_tokens`) diferiu só porque um token de teste expirou e o
  Atlas o apagou sozinho (índice TTL), o que é esperado. Banco temporário apagado em seguida.
- Retenção testada numa cópia: com 6 arquivos válidos mais arquivos estranhos, apagou só os 2 mais antigos do padrão e manteve os 4 recentes e todo o resto.

## 5. Trocar a chave (se suspeitar de vazamento da privada)

1. Gere um novo par na sua máquina (`openssl req -x509 -newkey rsa:4096 -nodes -keyout NOVA-privada -out NOVA-publica.pem -days 36500 -subj "/CN=bokka-backup"`).
2. Peça ao DevOps para trocar `/etc/bokka-backup/backup-public.pem` pela nova pública e rodar um backup novo.
3. **Os backups antigos continuam abríveis só com a chave antiga**: guarde a antiga enquanto ainda precisar deles, ou descarte os antigos.
