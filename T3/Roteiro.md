# Roteiro da Apresentação: Cofre Digital

Explicação do código, organizada na ordem em que o sistema é usado. Cada seção relaciona **o que o enunciado pede** com **como foi implementado** e **onde está** (`Arquivo.metodo()`).

---

## 1. Arquitetura

O código foi organizado em quatro camadas, cada uma com uma responsabilidade:

| Camada | Arquivos | Papel |
| :--- | :--- | :--- |
| Interface (Swing) | `CofreDigital`, `Tela*`, `Componentes` | Telas, botões e navegação; registra os eventos de tela |
| Regras | `Autenticador`, `ServicoCadastro`, `PastaSegura` | Etapas da autenticação, cadastro, acesso aos arquivos |
| Criptografia | `Criptografia`, `TOTP`, `Base32` | Operações da JCA (AES, RSA, assinatura, X.509) e TOTP |
| Persistência | `BancoDeDados`, `Usuario` | SQLite via JDBC: 5 tabelas e registros |

* **`LogView`** é um programa à parte, gerado num jar separado (`logview.jar`).
* **Bibliotecas externas:** só BouncyCastle (bcrypt) e o driver do SQLite. Todo o resto vem do JDK 8.

---

## 2. Base criptográfica (`Criptografia`)

Quase todo requisito do trabalho se apoia em quatro operações.

### 2.1 Chave AES a partir de uma semente: `gerarChaveAES()`

O enunciado pede AES de 256 bits gerado com **SHA1PRNG** a partir de uma frase, senha ou semente.

```java
SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
prng.setSeed(semente);          // semente definida antes de qualquer uso => sequencia deterministica
KeyGenerator gerador = KeyGenerator.getInstance("AES");
gerador.init(256, prng);
return gerador.generateKey();   // mesma semente => mesma chave
```

Essa mesma função é usada em três lugares:

| Semente | Para que serve a chave gerada |
| :--- | :--- |
| Frase secreta | Decriptar o arquivo da chave privada |
| Senha pessoal | Cifrar e decifrar a chave TOTP no banco |
| Semente do envelope | Decriptar os arquivos `.enc` |

### 2.2 Restaurar a chave privada: `restaurarChavePrivada()`

O caminho segue o slide 10 do detalhamento:

1. O arquivo `.key` é decriptado com AES/ECB/PKCS5Padding, usando a chave derivada da frase. O resultado é um PEM `-----BEGIN PRIVATE KEY-----`.
2. O conteúdo é decodificado de BASE64.
3. Os bytes PKCS8 viram uma `PKCS8EncodedKeySpec`.
4. `KeyFactory("RSA")` gera o `PrivateKey`.

Se a frase estiver errada, a decriptação falha (padding inválido) ou o resultado não é um PEM válido.

### 2.3 Validar a chave privada: `validarChavePrivada()`

O enunciado pede que a frase seja testada e que a chave seja verificada assinando um array aleatório:

1. Restaura a chave. Se falhar, a causa é `FRASE_SECRETA_INVALIDA` (registro 6006).
2. `verificarParDeChaves()` assina um array aleatório com **SHA1withRSA** e verifica a assinatura com a chave pública do certificado. Se não conferir, a causa é `ASSINATURA_INVALIDA` (registro 6007).

O tamanho do array muda conforme o uso:
* **10240 bytes:** cadastro, partida do sistema e consulta.
* **2048 bytes:** `logView`.

### 2.4 Certificado, envelope e assinatura

* **`extrairPEMCertificado()` + `carregarCertificado()`:** recortam só o bloco PEM (os `.crt` do pacote têm um texto antes dele) e usam `CertificateFactory("X.509")`.
* **`extrairEmail()` / `extrairNome()`:** pegam o login name (EMAILADDRESS) e o nome (CN) do campo **Sujeito**, como pede o enunciado.
* **`abrirEnvelope()`:** RSA/ECB/PKCS1Padding com a chave privada. Devolve a semente do SHA1PRNG.
* **`verificarAssinatura()`:** `Signature("SHA1withRSA")`.

---

## 3. Partida do sistema (`CofreDigital`)

O fluxo começa em `main()`:

1. Abre o banco e registra 1001.
2. Chama `partida()`, que tem dois caminhos:
   * **Banco sem usuários:** registra 1005 e 6001 e abre `TelaCadastro` em modo partida. O grupo fica fixo em Administrador e não há botão de voltar.
   * **Com usuários:** registra 1006 e chama `validarFraseAdministrador()`. Ela pede a frase do admin e valida a chave guardada no Chaveiro. Se a validação falhar, o sistema avisa e encerra.

**Por que a chave do admin fica em memória:** o `index` e o `share` são protegidos com as chaves do administrador. Sem a chave privada dele, nenhum usuário consegue listar a pasta.
* No primeiro cadastro, a chave chega por `definirChavesAdministrador()`.
* `encerrarSistema()` apaga as referências da memória e registra 1004 (se houver sessão aberta) e 1002.

---

## 4. Cadastro de usuários (`ServicoCadastro`, `TelaCadastro`)

O enunciado pede uma ordem de validação, confirmação dos dados do certificado, login único e armazenamento da senha, do token e do par chave/certificado. O processo tem duas fases.

### 4.1 `validar()`: critica os dados, sem gravar nada

1. **`criticarSenha()`:** exige 8 a 10 dígitos, sem dígitos iguais em sequência, e igual à confirmação. Falha: 6003.
2. **Certificado:** lê o arquivo e extrai e-mail e nome. Falha: 6004.
3. **Arquivo da chave privada:** lê o arquivo. Falha: 6005.
4. **Chave privada:** chama `validarChavePrivada(..., 10240)`. Falha: 6006 ou 6007.

### 4.2 Confirmação na tela: `TelaCadastro.confirmarDados()`

Mostra os campos do certificado: versão, série, validade, tipo de assinatura, emissor, sujeito e e-mail. Confirmar registra 6008; rejeitar registra 6009.

### 4.3 `efetivar()`: grava o usuário

1. **Login único:** se o login já existe, o cadastro é recusado.
2. **Senha:** `OpenBSDBCrypt.generate("2y", senha, salt de 16 bytes, custo 8)` gera `$2y$08$...` com 60 caracteres. Só o hash vai para o banco.
3. **Chave TOTP:**
   1. Gera 20 bytes aleatórios.
   2. Codifica em BASE32.
   3. Cifra com AES, usando a chave derivada da **senha pessoal**.
   4. Grava em `Usuarios.token_cifrado`.
4. **Par chave/certificado:** `BancoDeDados.inserirUsuario()` grava, numa única transação, o usuário e o Chaveiro (KID único, certificado em PEM e chave privada no formato binário original) e associa o KID ao usuário.

Depois do cadastro, `TelaCadastro.mostrarChaveTotp()` apresenta o BASE32 e a URI `otpauth://` para o Google Authenticator.

---

## 5. Autenticação em 3 etapas (`Autenticador`, `TelaLogin/Senha/Token`)

### Etapa 1: `identificarUsuario()`

* **Login inválido ou inexistente:** registra 2005 e continua na etapa 1.
* **Usuário bloqueado:** registra 2004 e continua na etapa 1.
* **Login aceito:** registra 2003 e vai para a etapa 2.

### Etapa 2: teclado virtual sobrecarregado

**Tela (`TelaSenha`):**
* `redistribuir()` embaralha os dígitos 0 a 9 com `SecureRandom` e os distribui em 5 pares, sem repetição.
* Cada clique guarda **o par**, nunca o dígito, e chama `redistribuir()` de novo.

**Verificação (`Autenticador.buscarSenha()`):** com n cliques há **2ⁿ senhas possíveis**. Todas são testadas contra o hash bcrypt.

```java
IntStream.range(0, 1 << pares.size()).parallel()
         .filter(mascara -> confere(hash, pares, mascara))   // OpenBSDBCrypt.checkPassword
         .findAny();
// montarCandidata(): o bit i da mascara escolhe qual digito do i-esimo par entra na candidata
```

* **Por que em paralelo:** bcrypt é lento de propósito. Com 10 dígitos, um teste sequencial levava 13 s; em paralelo, cerca de 1,3 s.
* **Interface sem travar:** a verificação roda num `SwingWorker` (`TelaSenha.confirmar()`).
* **A senha que confere é devolvida:** ela é necessária na etapa 3 para decifrar a chave TOTP.

### Etapa 3: TOTP (classe `TOTP`, só com `Mac`, `SecretKeySpec`, `Date` e `Base32`)

* **`TOTPCode(t)`:** o contador t vira 8 bytes big-endian e passa por `HMAC_SHA1(contador, chave)`.
* **`getTOTPCodeFromHash()`:** faz o truncamento dinâmico da RFC 4226.

  ```java
  int offset = hash[hash.length - 1] & 0x0F;                 // 4 bits finais indicam a posicao
  int binary = ((hash[offset] & 0x7F) << 24) | ... ;          // 31 bits a partir dali
  String code = Integer.toString(binary % 1000000);          // 6 digitos + zeros a esquerda
  ```

* **`validateCode()`:** t = segundos desde 1970 ÷ 30. Compara o código com **t, t−1 e t+1**, o que dá a margem de ±30 s pedida.
* **`Autenticador.tokenConfere()`:**
  1. Deriva a chave AES da senha.
  2. Decifra `token_cifrado`.
  3. Usa `new TOTP(base32, 30).validateCode(codigo)`.

A implementação foi validada com os vetores de teste da RFC 6238.

### Erros e bloqueio: `verificarSenha()` / `verificarToken()` / `bloquear()`

* **Contadores de erro:** cada erro incrementa `erros_senha` ou `erros_token` no banco (3004 a 3006, 4004 a 4006). Um acerto zera o contador, o que garante a regra de "erros consecutivos".
* **No 3º erro:** `bloqueado_ate = agora + 2 min`, os contadores são zerados e são registrados 3007 ou 4007. O sistema volta para a etapa 1.
* **Persistência:** como fica no banco, o bloqueio vale para aquele usuário mesmo após reiniciar. Os outros usuários continuam podendo entrar.

Com a autenticação completa, `CofreDigital.iniciarSessao()` incrementa `total_acessos` e registra 1003.

---

## 6. Telas da sessão (`Componentes.montarTela()`)

Todas as telas seguem o formato do enunciado:
* **Cabeçalho:** login, grupo e nome.
* **Corpo 1:** um total.
* **Corpo 2:** o conteúdo da tela.

| Tela | Corpo 1 | Detalhe |
| :--- | :--- | :--- |
| `TelaPrincipal` | Total de acessos | A opção 1 só aparece se `usuario.isAdministrador()` |
| `TelaCadastro` | Total de usuários | Formulário vazio após sucesso, preenchido após erro |
| `TelaConsulta` | Total de acessos | O corpo 2 começa com o total de consultas, que conta 1 a cada clique em Listar |
| `TelaSaida` | Total de acessos | Encerrar Sessão volta à etapa 1; Encerrar Sistema fecha |

* A navegação fica em `CofreDigital.mostrarTela*()`, e cada método registra o código da tela (5001, 6001, 7001, 8001).
* Os campos têm limite de tamanho (255 e 10 caracteres) via `Componentes.limitar()`.

---

## 7. Pasta segura (`PastaSegura`, `TelaConsulta`)

### 7.1 Listar: `TelaConsulta.listar()` → `PastaSegura.listar()`

1. **Valida a frase do usuário** com `validarChaveDoUsuario()`, do mesmo jeito que no cadastro. Isso também restaura a chave privada dele, que será usada para abrir os envelopes.
2. **Verifica o caminho da pasta.** Falha: 7004.
3. **Abre o `index` e o `share`** com `abrirArquivoDeControle()`, usando as chaves do **administrador** (slides 8 e 9):
   * `.env` + chave privada do admin → semente → `gerarChaveAES` → decripta `.enc`. Registra 7005 ou 7010; falha 7007 ou 7012.
   * Verifica `.asd` com a chave pública do admin. Registra 7006 ou 7011; falha 7008 ou 7013.
4. **Filtra a lista.** Um arquivo aparece se o usuário for o **dono**, se for do **mesmo grupo** (`normalizar()` compara "Usuário" com "usuario"), ou se houver uma linha no `share` com o login dele.

### 7.2 Abrir: `PastaSegura.decriptarArquivo()`

1. **Escolhe o envelope** conforme a relação do usuário com o arquivo:
   * **Dono:** usa `codigo.env`.
   * **Compartilhado:** usa `codigo.N.shr`.
   * **Outro caso:** **acesso negado** (7017). Quem é só do mesmo grupo vê o arquivo, mas não tem envelope cifrado para a sua chave.
2. **Decripta com a chave privada do usuário.** Registra 7018; falha 7020 (**sigilo**).
3. **Verifica a assinatura com o certificado do dono**, buscado no Chaveiro. Registra 7019; falha 7021 (**integridade e autenticidade**).
4. **Grava o arquivo** com o nome secreto na pasta. `nomeSeguro()` impede caminhos como `../`.

---

## 8. Banco de dados (`BancoDeDados`)

| Tabela | Conteúdo principal |
| :--- | :--- |
| `Usuarios` | UID, login (único), nome, GID, KID, `senha_hash` (bcrypt), `token_cifrado`, totais, contadores de erro, `bloqueado_ate` |
| `Chaveiro` | KID, UID, certificado PEM, chave privada cifrada (BLOB) |
| `Grupos` | 1 = Administrador, 2 = Usuário |
| `Mensagens` | As 64 mensagens da tabela do anexo (MID → texto) |
| `Registros` | RID, MID, data/hora, UID, login, arquivo. **Sem o texto da mensagem**, como exige o enunciado |

* **`registrar(mid, login, arquivo)`:** grava cada operação e preenche o UID por subconsulta quando o login existe. O código 2005 (login não identificado) também guarda o login digitado.
* **Proteção contra injeção de SQL:** toda consulta que recebe dados digitados usa `PreparedStatement`.

---

## 9. Programa de auditoria (`LogView`)

O enunciado pede um programa separado, que só o admin executa, autenticado com chave e certificado, com a frase lida sem eco e um teste de 2048 bytes.

1. Recebe o caminho da chave pela linha de comando.
2. Lê a frase com `System.console().readPassword()`, que não mostra o que é digitado.
3. **`autenticarAdministrador()`:**
   1. Restaura a chave.
   2. Testa a chave contra o certificado de cada usuário do grupo Administrador, com 2048 bytes.
   3. Se nenhum conferir, encerra.
4. **`exibirRegistros()`:** mostra os registros em ordem cronológica, juntando `Registros` com `Mensagens` e trocando `<login_name>` e `<arq_name>` pelos valores gravados.
5. **Separação real:** o `build.sh` gera dois jars. O `logview.jar` leva só o `LogView` e as classes de apoio (banco e criptografia), sem nenhuma tela do cofre. O `cofre.jar` não contém o `LogView`.

---

## 10. Cuidados extras de segurança

* **Senhas, frases e sementes como `char[]`/`byte[]`:** são apagadas com `Arrays.fill` depois do uso. Uma `String` não pode ser apagada.
* **Teclado virtual:** guarda só os pares pressionados; o dígito escolhido nunca existe na memória da tela.
* **Arquivos adulterados:** quando a verificação falha, o conteúdo decriptado **não é gravado**.
* **Compatibilidade:** os fontes estão em ASCII puro e foram compilados com `--release 8`, o que garante compatibilidade com o JDK 1.8 do professor (o sistema foi testado num runtime Java 8 real).

---

## 11. Perguntas prováveis

* **Por que o admin digita a frase ao iniciar o sistema?**
  O `index` e o `share` têm envelope e assinatura do admin. Sem a chave privada dele em memória, ninguém lista a pasta.

* **Como funciona o teclado com dois dígitos por botão?**
  Guardamos os pares clicados e testamos as 2ⁿ combinações no bcrypt, em paralelo. Quem olha a tela não descobre os dígitos.

* **Onde fica a chave do TOTP e como ela é protegida?**
  Fica no banco em BASE32, cifrada com AES. A chave AES é derivada da senha pessoal, por isso a etapa 3 depende da etapa 2.

* **Como funciona a tolerância de ±30 s?**
  `validateCode()` compara o código com os intervalos t−1, t e t+1.

* **Por que o user02 vê o `teste01.docx` mas não consegue abrir?**
  Ele vê por ser do mesmo grupo. Mas o envelope `.env` foi cifrado com a chave pública do dono (user01), e não existe `.shr` para o user02.

* **E se alguém alterar um `.enc`?**
  A decriptação pode até funcionar, mas a assinatura do dono não confere: registra 7021 e o arquivo não é gravado.

* **Por que bcrypt, com custo 8?**
  É exigência do enunciado. O salt aleatório impede ataques com tabelas pré-calculadas, e o custo (2⁸ iterações) torna cada tentativa de força bruta cara.

* **Como a chave AES é recriada a partir da semente?**
  O SHA1PRNG é determinístico quando recebe a semente antes de qualquer uso. A mesma semente gera a mesma chave.
