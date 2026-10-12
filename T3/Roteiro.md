# Roteiro da Apresentação: Cofre Digital

* **Parte 1:** o que mostrar, na ordem, e o que falar em cada passo.
* **Parte 2:** como funciona por dentro, para quando o professor perguntar (`Arquivo.metodo()`).
* **Partes 3 a 5:** decisões de interpretação, como o trabalho foi validado e perguntas prováveis.

---

## Parte 1. Demonstração

**Antes de começar:** `./build.sh`, `rm -f cofre.db`, Google Authenticator aberto e um PowerShell na pasta `T3` para o `logView`. Os passos detalhados e os códigos esperados estão no `HowToTest.md`.

| # | Mostrar | O que dizer |
| :--- | :--- | :--- |
| 1 | `java -jar cofre.jar` com banco vazio: cadastro do admin (grupo fixo, sem botão voltar) | Sem usuários, a partida cadastra o administrador. A chave privada dele fica em memória porque `index` e `share` são protegidos com as chaves do admin. |
| 2 | Cadastro com frase errada e depois com a chave de outro usuário | A frase é testada decriptando a chave. O par chave/certificado é testado assinando 10240 bytes aleatórios e verificando com a chave pública do certificado. |
| 3 | Dados corretos → tela de confirmação → Confirmar → chave BASE32 no celular | Mostra versão, série, validade, tipo de assinatura, emissor, sujeito e e-mail. A chave TOTP tem 20 bytes aleatórios, vai em BASE32 e é guardada cifrada com AES derivado da senha. |
| 4 | Login do admin: e-mail → teclado virtual → TOTP | 5 botões com 2 dígitos, redistribuídos a cada clique. O sistema testa as 2ⁿ senhas possíveis no bcrypt. O TOTP aceita ±30 s. |
| 5 | Opção 1: cadastrar user01 e user02 | Login extraído do certificado e único. O formulário volta vazio após o sucesso. |
| 6 | Opção 2 como admin (`Pacote-T3/Files`, frase `admin`): abrir `teste00.docx` | `index` e `share` abertos com as chaves do admin. O arquivo abre pelo envelope do dono e tem a assinatura verificada. |
| 7 | Encerrar Sessão → user01: abrir `teste01` e `teste02` | `teste01` abre pelo `.env` (dono) e `teste02` pelo `.1.shr` (compartilhamento). |
| 8 | user02: abrir `teste01` | Aparece por ser do mesmo grupo, mas dá **acesso negado**: não há envelope cifrado para a chave do user02. |
| 9 | 3 senhas erradas → bloqueio; outro usuário entra normalmente | Erros consecutivos contados no banco; bloqueio de 2 min só para quem errou. |
| 10 | Pasta `FilesTeste` com um `.enc` adulterado | A decriptação "funciona", mas a assinatura do dono não confere (7021) e nada é gravado. |
| 11 | Opção 3 → Encerrar Sistema → reiniciar | A partida pede a frase do admin. Com frase errada, o sistema avisa e encerra. |
| 12 | `java -jar logview.jar Pacote-T3\Keys\admin-pkcs8-aes.key` | Programa separado, só para o admin, frase sem eco, teste de 2048 bytes, registros em ordem cronológica. |

---

## Parte 2. Como funciona

### 2.1 Arquitetura

| Camada | Arquivos | Papel |
| :--- | :--- | :--- |
| Interface (Swing) | `CofreDigital`, `Tela*`, `Componentes` | Telas e navegação; cada tela registra seu código |
| Regras | `Autenticador`, `ServicoCadastro`, `PastaSegura` | Autenticação, cadastro, acesso aos arquivos |
| Criptografia | `Criptografia`, `TOTP`, `Base32` | JCA do JDK (AES, RSA, assinatura, X.509) e TOTP |
| Persistência | `BancoDeDados`, `Usuario` | SQLite via JDBC: 5 tabelas |

* **Bibliotecas externas:** só BouncyCastle (`OpenBSDBCrypt`) e o driver JDBC do SQLite. O resto é do JDK.
* **`LogView`:** sai num jar próprio (`logview.jar`). O `cofre.jar` não contém essa classe.

### 2.2 Blocos criptográficos (`Criptografia`)

Quase todo requisito se apoia em cinco operações:

| Operação | Como | Método |
| :--- | :--- | :--- |
| Chave AES de 256 bits a partir de uma semente | `SHA1PRNG` + `KeyGenerator("AES").init(256, prng)` | `gerarChaveAES()` |
| Restaurar a chave privada | AES/ECB/PKCS5 com a chave da frase → PEM → BASE64 → `PKCS8EncodedKeySpec` → `KeyFactory("RSA")` | `restaurarChavePrivada()` |
| Validar a chave privada | Assina N bytes aleatórios (SHA1withRSA) e verifica com a chave pública do certificado | `validarChavePrivada()` |
| Abrir envelope | RSA/ECB/PKCS1Padding com a chave privada → semente do SHA1PRNG | `abrirEnvelope()` |
| Verificar assinatura | `Signature("SHA1withRSA")` com a chave pública | `verificarAssinatura()` |

```java
SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");
prng.setSeed(semente);        // semente antes de qualquer uso => sequencia deterministica
KeyGenerator gerador = KeyGenerator.getInstance("AES");
gerador.init(256, prng);      // mesma semente => mesma chave
```

* **Sementes usadas:**
  * **Frase secreta:** gera a chave que decripta o arquivo `.key`.
  * **Senha pessoal:** gera a chave que cifra e decifra a chave TOTP guardada no banco.
  * **Semente do envelope:** gera a chave que decripta os arquivos `.enc`.
* **Tamanho do teste de assinatura:**
  * **10240 bytes:** cadastro, partida e consulta.
  * **2048 bytes:** `logView`.
* **Motivo da falha na validação:**
  * Falha ao decriptar → frase inválida (6006).
  * Assinatura que não confere → par inválido (6007).
* **Checagem de AES-256 (`suportaAES256()`):** Java 8 anterior ao update 161 limita o AES a 128 bits. Sem essa checagem, a frase **certa** apareceria como "frase secreta inválida". Agora o cofre e o `logView` avisam a versão do Java e encerram.

### 2.3 Partida (`CofreDigital`)

1. `main()` checa o AES-256, abre o banco e registra 1001.
2. `partida()` segue um de dois caminhos:
   * **Sem usuários:** registra 1005 e 6001 e abre `TelaCadastro` em modo partida.
   * **Com usuários:** registra 1006, e `validarFraseAdministrador()` valida a frase do admin com a chave guardada no Chaveiro. Se a validação falhar, o sistema avisa e encerra (1002).
3. A chave privada do admin fica em memória para abrir `index.env` e `share.env`. `encerrarSistema()` apaga a referência e registra 1004 (se houver sessão) e 1002.

### 2.4 Cadastro (`ServicoCadastro`, `TelaCadastro`)

| Fase | O que faz | Registros |
| :--- | :--- | :--- |
| `validar()` | Senha (`criticarSenha()`: 8 a 10 dígitos, sem iguais seguidos, igual à confirmação) → certificado (e-mail e CN do **Sujeito**) → arquivo da chave → `validarChavePrivada(10240)` | 6003 a 6007 |
| `confirmarDados()` | Mostra os campos do certificado; Confirmar ou Rejeitar | 6008 / 6009 |
| `efetivar()` | Login único → bcrypt → chave TOTP cifrada → usuário + Chaveiro numa transação | — |

* **Senha:** `OpenBSDBCrypt.generate("2y", senha, salt de 16 bytes, 8)` gera `$2y$08$...` (60 caracteres). Só o hash é gravado.
* **Chave TOTP:** 20 bytes aleatórios → BASE32 → AES com a chave derivada da senha → `Usuarios.token_cifrado`.
* **Chaveiro:** KID único, certificado em PEM e chave privada no binário cifrado original. O KID também vai para `Usuarios`.
* **Depois do cadastro:** `mostrarChaveTotp()` mostra o BASE32 e a URI `otpauth://totp/Cofre%20Digital:<login>?secret=...`. Formulário vazio após sucesso; preenchido após erro ou rejeição.

### 2.5 Autenticação (`Autenticador`, `TelaLogin`, `TelaSenha`, `TelaToken`)

**Etapa 1, `identificarUsuario()`:**
* E-mail inválido ou inexistente → 2005, fica na etapa 1.
* Usuário bloqueado → 2004, fica na etapa 1.
* Login aceito → 2003 e vai para a etapa 2.

**Etapa 2, teclado sobrecarregado:**
* **Tela:** `TelaSenha.redistribuir()` embaralha 0 a 9 com `SecureRandom` em 5 pares. Cada clique guarda **o par** (nunca o dígito) e redistribui.
* **Verificação:** com n cliques há 2ⁿ senhas possíveis. `buscarSenha()` testa todas no bcrypt, em paralelo e num `SwingWorker`. Leva menos de 1 s com 8 dígitos e cerca de 1 s com 10.

  ```java
  IntStream.range(0, 1 << pares.size()).parallel()
           .filter(mascara -> confere(hash, pares, mascara))  // OpenBSDBCrypt.checkPassword
           .findAny();
  // montarCandidata(): o bit i da mascara escolhe o digito do i-esimo par
  ```

* **A senha que confere é devolvida**, porque a etapa 3 precisa dela para decifrar a chave TOTP.

**Etapa 3, TOTP:** a classe `TOTP` segue a estrutura do anexo e usa só `Mac`, `SecretKeySpec`, `Date` e `Base32`.
* **`TOTPCode(t)`:** HMAC-SHA1 do contador t em 8 bytes big-endian.
* **`getTOTPCodeFromHash()`:** truncamento dinâmico da RFC 4226. Os 4 bits finais dão o offset; 31 bits a partir dali, `% 1000000` e zeros à esquerda.
* **`validateCode()`:** t = segundos desde 1970 ÷ 30. Compara com t−1, t e t+1, o que dá a margem de ±30 s.
* **`tokenConfere()`:** chave AES da senha → decifra `token_cifrado` → `new TOTP(base32, 30).validateCode()`.

**Erros e bloqueio, `verificarSenha()` / `verificarToken()` / `bloquear()`:**
* **Cada erro:** incrementa `erros_senha` ou `erros_token` no banco (3004 a 3006, 4004 a 4006). Um acerto zera o contador, o que garante os "erros consecutivos".
* **3º erro:** `bloqueado_ate = agora + 2 min`, contadores zerados, registra 3007 ou 4007 e volta à etapa 1.
* **Persistência:** como fica no banco, vale para aquele usuário mesmo após reiniciar. Os outros continuam entrando.
* **Sessão:** com a autenticação completa, `iniciarSessao()` incrementa `total_acessos` e registra 1003.

### 2.6 Telas (`Componentes.montarTela()`: cabeçalho, corpo 1, corpo 2)

| Tela | Corpo 1 | Corpo 2 |
| :--- | :--- | :--- |
| Principal | Total de acessos do usuário | Menu: a opção 1 só aparece para o grupo Administrador |
| Cadastro | Total de usuários do sistema | Formulário de Cadastro |
| Consulta | Total de acessos do usuário (igual à Principal, como pede o texto) | Total de consultas, pasta, frase, Listar, tabela (nome, dono, grupo) |
| Saída | Total de acessos do usuário | Mensagem do enunciado + Encerrar Sessão / Encerrar Sistema / Voltar |

* Os textos e os nomes dos botões seguem o enunciado (ex.: "Voltar de Cadastrar para o Menu Principal").
* Os campos têm limite de 255 caracteres (10 para a senha).

### 2.7 Pasta segura (`PastaSegura`, `TelaConsulta`)

**Listar (`TelaConsulta.listar()` → `PastaSegura.listar()`):**
1. Valida a frase do usuário como no cadastro e restaura a chave privada dele. Falha: 6006 ou 6007.
2. Verifica o caminho da pasta. Falha: 7004.
3. `abrirArquivoDeControle()` abre o `index` e o `share` com as chaves do **admin**:
   * **Decriptação:** `.env` + chave privada do admin → semente → AES → decripta o `.enc`. Registra 7005 / 7010; falha 7007 / 7012.
   * **Verificação:** confere o `.asd` com a chave pública do admin. Registra 7006 / 7011; falha 7008 / 7013.
4. Mostra os arquivos de que o usuário é **dono**, os do **mesmo grupo** (`normalizar()` iguala "Usuário" e "usuario") e os que estão no `share` com o login dele.

**Abrir (`decriptarArquivo()`):**

| Relação com o arquivo | Envelope usado | Resultado |
| :--- | :--- | :--- |
| Dono | `codigo.env` | 7016 → decripta |
| Compartilhado | `codigo.N.shr` | 7016 → decripta |
| Só do mesmo grupo / outro | nenhum | 7017, acesso negado |

* **Decriptação com a chave privada do usuário:** registra 7018; falha 7020 (**sigilo**).
* **Assinatura verificada com o certificado do dono:** registra 7019; falha 7021 (**integridade e autenticidade**).
* **Gravação:** só se tudo conferir, com o nome secreto, na própria pasta. `nomeSeguro()` bloqueia caminhos como `../`.

### 2.8 Banco e registros (`BancoDeDados`)

| Tabela | Conteúdo |
| :--- | :--- |
| `Usuarios` | UID, login único, nome, GID, KID, hash bcrypt, token cifrado, totais, contadores de erro, `bloqueado_ate` |
| `Chaveiro` | KID, UID, certificado PEM, chave privada cifrada |
| `Grupos` | 1 = Administrador, 2 = Usuário |
| `Mensagens` | As 64 mensagens do anexo (MID → texto) |
| `Registros` | RID, MID, data/hora, UID, login, arquivo. **Sem o texto da mensagem** |

* **`registrar()`:** grava cada operação. O código 2005 guarda o login digitado.
* **Injeção de SQL:** todas as consultas com dados digitados usam `PreparedStatement`.

### 2.9 `LogView`

1. Recebe o caminho da chave privada na linha de comando.
2. Lê a frase com `System.console().readPassword()`, sem eco.
3. `autenticarAdministrador()` restaura a chave e testa com 2048 bytes contra o certificado de cada administrador. Se nenhum conferir, encerra.
4. `exibirRegistros()` junta `Registros` com `Mensagens`, troca `<login_name>` e `<arq_name>` pelos valores e mostra tudo em ordem cronológica.

---

## Parte 3. Decisões de interpretação

| Ponto do enunciado | Decisão e justificativa |
| :--- | :--- |
| Arquivo do grupo: ver × abrir | Aparece na lista (política de listagem), mas só abre quem tem envelope: o dono (`.env`) ou o compartilhado (`.shr`). Os demais recebem acesso negado (7017). Tecnicamente não há como abrir sem envelope. |
| "Sequências de números repetidos" | Rejeita dígitos iguais seguidos (`11`), o que também cobre `11111111`. |
| "Frase do admin mantida em memória" | A frase é usada uma vez para restaurar a `PrivateKey`, que é o que o sistema precisa. O `char[]` da frase é zerado; a chave é apagada ao encerrar. |
| Corpo da tela de consulta | O texto pede "o mesmo corpo 1 da Tela Principal": total de acessos no corpo 1 e total de consultas no início do corpo 2. |
| Frase errada na consulta (sem código próprio) | Usa 6006 / 6007, cujos textos descrevem exatamente o evento. |
| Total de acessos / consultas | Acesso = sessão iniciada (1003). Consulta = cada clique em Listar. |
| Administrador do sistema | O primeiro cadastrado (partida). O `logView` aceita a chave de qualquer usuário do grupo Administrador. |
| Registro 6001 na partida | A tela de cadastro do admin também registra 6001, sem login (ainda não há usuário). |
| QR Code | Opcional no enunciado; não foi feito. O sistema mostra o BASE32 e a URI `otpauth`. |

---

## Parte 4. Como foi validado

* **Teste automatizado da interface (190 verificações):** dirigiu a interface Swing do início ao fim (cadastros, 3 etapas, bloqueios, consultas com os três usuários, saída e reinício) e conferiu os códigos gravados em `Registros` a cada passo.
* **TOTP:** confere com os vetores de teste da RFC 6238.
* **bcrypt:** confere o exemplo do enunciado (`$2y$08$ajDZ...`).
* **Base32:** idêntica, token a token, ao anexo do enunciado.
* **Adulteração:** 10 casos (alterar `.asd`, `.enc`, `.env`, `.shr`, `index`, `share`) detectados com o código certo.
* **Compatibilidade:** compilado com `--release 8` (bytecode Java 8, assim como as bibliotecas). Requer Java 8u161 ou superior, por causa do AES-256.

---

## Parte 5. Perguntas prováveis

* **Por que o admin digita a frase ao iniciar?**
  `index` e `share` têm envelope e assinatura do admin. Sem a chave privada dele em memória, ninguém lista a pasta.
* **Por que guardar a `PrivateKey` e não a frase?**
  O objetivo do enunciado é ter acesso à chave durante a execução. Guardar a chave já restaurada evita manter a frase em texto, e ela é apagada ao encerrar.
* **Como o teclado com 2 dígitos por botão verifica a senha?**
  Guardamos os pares clicados e testamos as 2ⁿ combinações no bcrypt, em paralelo. Quem olha a tela não descobre os dígitos.
* **Onde fica a chave do TOTP?**
  No banco, em BASE32, cifrada com AES derivado da senha pessoal. Por isso a etapa 3 depende da senha descoberta na etapa 2.
* **Como funciona a margem de ±30 s?**
  `validateCode()` compara com os intervalos t−1, t e t+1.
* **Por que o user02 vê `teste01` e não abre?**
  Vê por ser do mesmo grupo, mas o `.env` foi cifrado com a chave pública do user01 e não há `.shr` para o user02.
* **E se alterarem um `.enc`?**
  A decriptação pode funcionar, mas a assinatura do dono não confere: 7021, e o arquivo não é gravado.
* **Por que bcrypt com custo 8?**
  Exigência do enunciado. O salt aleatório impede tabelas pré-calculadas, e 2⁸ iterações tornam cada tentativa cara.
* **Como a mesma semente gera sempre a mesma chave AES?**
  O SHA1PRNG é determinístico quando recebe a semente antes de qualquer uso.
* **E se rodarem num Java 8 antigo?**
  Antes do update 161 o AES fica limitado a 128 bits. O sistema detecta isso na partida e explica, em vez de acusar "frase secreta inválida".
* **Como garantem que o `logView` não faz parte do cofre?**
  São dois jars: `jar tf cofre.jar` não lista `LogView`, e o `logview.jar` não tem nenhuma tela do cofre.
