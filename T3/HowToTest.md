# Como Testar o Cofre Digital

Roteiro de testes manuais do Trabalho 3. Cada caso traz os **passos**, o **resultado esperado** e os **códigos de registro** que devem aparecer no `logView`. Marque `[x]` à medida que for testando.

> As seções seguem a ordem de uso do sistema. A seção 1 só pode ser feita com o banco vazio. As demais dependem dos usuários cadastrados nas seções 1 e 3.

---

## 0. Preparação

```bash
cd T3
./build.sh          # gera cofre.jar e logview.jar
rm -f cofre.db      # banco vazio = primeira execução
```

* Execute sempre a partir da pasta `T3`: o banco `cofre.db` fica no diretório corrente.
* Tenha o **Google Authenticator** instalado no celular.
* Dados de teste (pacote do professor + senhas sugeridas):

| Usuário | Certificado | Chave privada | Frase secreta | Senha pessoal sugerida |
| :--- | :--- | :--- | :--- | :--- |
| admin@inf1416.puc-rio.br | `Pacote-T3/Keys/admin-x509.crt` | `Pacote-T3/Keys/admin-pkcs8-aes.key` | `admin` | `13572468` |
| user01@inf1416.puc-rio.br | `Pacote-T3/Keys/user01-x509.crt` | `Pacote-T3/Keys/user01-pkcs8-aes.key` | `user01` | `2468135790` |
| user02@inf1416.puc-rio.br | `Pacote-T3/Keys/user02-x509.crt` | `Pacote-T3/Keys/user02-pkcs8-aes.key` | `user02` | `9081726354` |

### Como conferir os registros

Num **segundo terminal**, também na pasta `T3`. Pode rodar com o cofre aberto, mas só depois de o admin estar cadastrado.

```bash
java -jar logview.jar Pacote-T3/Keys/admin-pkcs8-aes.key     # frase: admin
```

* Os registros saem em ordem cronológica.
* Use as **últimas linhas** para conferir os códigos esperados de cada caso.
* Registros sem usuário associado mostram `-` no lugar do login name (por exemplo, os da partida do sistema).

---

## 1. Primeira execução: cadastro do administrador

```bash
java -jar cofre.jar
```

Deve abrir a tela **"Partida do sistema: cadastro do administrador"** com:
* **Corpo 1:** "Total de usuarios do sistema: 0".
* **Grupo:** fixo em `Administrador` (desabilitado).
* **Botões:** não há botão de voltar.

### 1.1 Erros de validação do formulário

Preencha os campos com os dados corretos do admin. Em cada linha, altere **apenas** o campo indicado e clique em **Cadastrar**.

| ☐ | Alteração | Mensagem esperada | Códigos |
| :--- | :--- | :--- | :--- |
| [ ] | Senha `1357246a` (com letra) | A senha pessoal deve conter apenas digitos de 0 a 9. | 6002 6003 |
| [ ] | Senha `1357246` (7 dígitos) | A senha pessoal deve ter 8, 9 ou 10 digitos. | 6002 6003 |
| [ ] | Senha `13572446` (dígitos repetidos em sequência) | A senha pessoal nao pode conter digitos repetidos em sequencia (ex.: 11). | 6002 6003 |
| [ ] | Confirmação diferente da senha | A senha pessoal e a confirmacao nao conferem. | 6002 6003 |
| [ ] | Certificado `Pacote-T3/Keys/nao-existe.crt` | Caminho do certificado digital invalido: ... | 6002 6004 |
| [ ] | Certificado apontando para um `.key` (não é certificado) | Caminho do certificado digital invalido: ... | 6002 6004 |
| [ ] | Chave `Pacote-T3/Keys/nao-existe.key` | Caminho do arquivo da chave privada invalido. | 6002 6005 |
| [ ] | Frase secreta `errada` | Frase secreta invalida para a chave privada. | 6002 6006 |
| [ ] | Chave do user01 (frase `user01`) com o certificado do admin | A chave privada nao corresponde ao certificado digital (assinatura digital invalida). | 6002 6007 |

**Observe:**
* Depois de cada erro, o formulário **continua preenchido**.
* O campo de senha aceita no máximo 10 caracteres. Uma senha com 11 dígitos nem chega a ser digitada.

### 1.2 Tela de confirmação: rejeitar

- [ ] Com todos os dados corretos, clique em **Cadastrar**. Deve aparecer a confirmação com os campos do certificado:
  * **Versão:** `3`
  * **Série:** `23 (0x17)`
  * **Validade:** de 23/04/2026 a 23/04/2027
  * **Tipo de Assinatura:** `SHA256withRSA`
  * **Emissor:** `EMAILADDRESS=ca@grad.inf.puc-rio.br, CN=AC INF1416, OU=INF1416, O=PUC, L=Rio, ST=RJ, C=BR`
  * **Sujeito (Friendly Name):** `Administrator`
  * **E-mail:** `admin@inf1416.puc-rio.br`
- [ ] Clique em **Rejeitar**: volta ao formulário **preenchido**. Códigos: `6002 6009`.

### 1.3 Cadastro com sucesso

- [ ] Clique em **Cadastrar** e depois em **Confirmar**.
- [ ] Deve aparecer a **chave TOTP em BASE32** (32 caracteres) e a URI `otpauth://totp/Cofre%20Digital:admin@inf1416.puc-rio.br?secret=...`.
- [ ] No Google Authenticator, cadastre a chave:
  1. Toque em **+**.
  2. Escolha **Inserir chave de configuração**.
  3. Em **Nome da conta**, use o e-mail; em **Chave**, cole o BASE32.
  4. Em **Tipo**, escolha **Baseada no horário**.
- [ ] Ao fechar o diálogo, deve abrir a **tela de login** (etapa 1).
- [ ] Códigos: `1001 1005 6002 6008 2001`.

---

## 2. Autenticação (3 etapas)

### 2.1 Etapa 1: login name

| ☐ | Entrada | Resultado esperado | Códigos |
| :--- | :--- | :--- | :--- |
| [ ] | `abc` | Login name invalido: informe um e-mail valido. (permanece na etapa 1) | 2005 |
| [ ] | `naoexiste@inf1416.puc-rio.br` | Login name nao identificado: ... (permanece na etapa 1) | 2005 |
| [ ] | `admin@inf1416.puc-rio.br` | Vai para a etapa 2 | 2003 2002 3001 |

### 2.2 Etapa 2: comportamento do teclado virtual

- [ ] São **5 botões com 2 dígitos cada**, cobrindo de 0 a 9 **sem repetição**.
- [ ] Os dígitos são **redistribuídos a cada clique**.
- [ ] O campo de senha mostra **apenas bolinhas** (nunca os dígitos).
- [ ] O botão **OK** só fica habilitado a partir de **8 dígitos**.
- [ ] Com **10 dígitos**, os botões numéricos ficam desabilitados.
- [ ] **LIMPAR** apaga o que foi digitado.
- [ ] Senha correta (`13572468`): vai para a etapa 3. Códigos: `3003 3002 4001`.

### 2.3 Etapa 3: token TOTP

- [ ] Digite o código atual do Google Authenticator. Deve abrir a **Tela Principal**. Códigos: `4003 4002 1003 5001`.
- [ ] **Margem de −30 s:**
  1. Anote o código.
  2. Espere o app trocar **uma vez**.
  3. Logo em seguida, digite o código **anotado**.
  4. Deve ser **aceito**.
- [ ] **Fora da margem:** anote um código e espere o app trocar **duas vezes**. O código anotado deve ser **rejeitado** e contar como erro.

### 2.4 Erros de senha e bloqueio (faça com o user01 depois da seção 3, ou com o admin)

- [ ] **1ª senha errada:** "Senha pessoal incorreta (erro 1 de 3)." Continua na etapa 2, com o teclado redistribuído. Código: `3004`.
- [ ] **2ª senha errada:** "(erro 2 de 3)". Código: `3005`.
- [ ] **3ª senha errada:** "Senha pessoal incorreta. Terceiro erro consecutivo: o acesso foi bloqueado por 2 minutos." Volta para a etapa 1. Códigos: `3006 3007 3002 2001`.
- [ ] **Login com o mesmo usuário:** "O acesso do usuario ... esta bloqueado. Tente novamente em N segundos." Código: `2004`.
- [ ] **Outro usuário durante o bloqueio:** consegue entrar normalmente. O bloqueio é só do usuário que errou.
- [ ] **Depois de 2 minutos:** o login volta a funcionar. Código: `2003`.

### 2.5 Erros de token e bloqueio

- [ ] Três códigos errados (ex.: `000000`) produzem "Token invalido (erro 1 de 3)", depois "(erro 2 de 3)" e então o bloqueio de 2 minutos. Volta para a etapa 1.
- [ ] Códigos: `4004 4005 4006 4007 4002 2001`.

### 2.6 "Erros consecutivos": um acerto zera a contagem

- [ ] Duas senhas erradas e depois a correta. Na próxima autenticação, um novo erro deve ser contado como **erro 1 de 3** (`3004`), e não como o terceiro.

### 2.7 A contagem de erros sobrevive ao reinício

1. [ ] Erre a senha **duas** vezes.
2. [ ] Feche a janela (X).
3. [ ] Abra o sistema de novo e entre com o mesmo usuário.
4. [ ] Erre **uma** vez: o acesso deve ser **bloqueado** (`3006 3007`).

---

## 3. Tela Principal e cadastro de usuários (logado como admin)

### 3.1 Cabeçalho e corpo 1

- [ ] O cabeçalho mostra:
  * `Login: admin@inf1416.puc-rio.br`
  * `Grupo: Administrador`
  * `Nome: Administrator`
- [ ] "Total de acessos do usuario" aumenta 1 a cada login.
- [ ] O menu mostra as opções **1, 2 e 3**.

### 3.2 Cadastrar o user01 e o user02

- [ ] Clique em **1 - Cadastrar um novo usuario**. O cabeçalho é o mesmo, e o corpo 1 mostra "Total de usuarios do sistema: 1". Códigos: `5002 6001`.
- [ ] Cadastre o **user01**:
  1. Grupo `Usuário`, frase `user01`, senha `2468135790`.
  2. Confirme os dados: série **18 (0x12)**, sujeito **Usuario 01**.
  3. Cadastre a chave TOTP no celular.
- [ ] Depois do sucesso, o **formulário volta vazio** e o total passa para **2**. Códigos: `6002 6008`.
- [ ] Cadastre o **user02** do mesmo jeito: frase `user02`, senha `9081726354`, série **19 (0x13)**.
- [ ] **Login duplicado:** cadastrar o user01 de novo e clicar em **Confirmar** deve dar "O login name user01@inf1416.puc-rio.br ja esta cadastrado no sistema." e manter o formulário preenchido.
- [ ] **Voltar ao Menu Principal:** volta para a Tela Principal. Códigos: `6010 5001`.

### 3.3 Menu do grupo Usuário

- [ ] Entre como **user01**:
  * O cabeçalho mostra `Grupo: Usuário`.
  * O menu **não** mostra a opção 1, só a **2** e a **3**.

---

## 4. Consultar pasta de arquivos secretos

Pasta: `Pacote-T3/Files`. O caminho relativo funciona porque o sistema roda a partir de `T3`; também dá para escolher pelo botão `...`.

| ☐ | Usuário (frase) | Listagem esperada | Ao clicar duas vezes |
| :--- | :--- | :--- | :--- |
| [ ] | admin (`admin`) | `teste00.docx` | abre (dono) |
| [ ] | user01 (`user01`) | `teste01.docx`, `teste02.docx` | os **dois abrem**: teste01 por ser dono, teste02 pelo compartilhamento `XXYYZZ22.1.shr` |
| [ ] | user02 (`user02`) | `teste01.docx`, `teste02.docx` | teste02 abre (dono). teste01 dá **acesso negado**: é do mesmo grupo, mas não há envelope para o user02 |

**Códigos esperados:**
* **Listagem com sucesso:** `5003 7001 7003 7005 7006 7010 7011 7009 7014`.
* **Arquivo aberto:** `7015 7016 7018 7019`. A mensagem informa onde o arquivo foi gravado (`Pacote-T3/Files/<nome>.docx`). **Abra o .docx** para confirmar que o conteúdo é legível.
* **Acesso negado:** `7015 7017`, com a mensagem "Voce nao tem permissao de acesso ao arquivo teste01.docx: ...".

**Outros casos:**

| ☐ | Caso | Resultado esperado | Códigos |
| :--- | :--- | :--- | :--- |
| [ ] | Frase secreta errada (ex.: user01 digitando `admin`) | Frase secreta invalida: ela nao decripta a sua chave privada. (tabela vazia) | 7003 6006 |
| [ ] | Pasta `/nao/existe` ou campo vazio | Caminho de pasta invalido: ... | 7003 7004 |
| [ ] | Pasta que existe mas não é cofre (ex.: `Pacote-T3/Keys`) | Falha na decriptacao do arquivo de indice ... | 7003 7007 |
| [ ] | Clicar em Listar várias vezes | "Total de consultas do usuario" aumenta 1 a cada clique | 7003 |
| [ ] | Voltar ao Menu Principal | Volta para a Tela Principal | 7002 5001 |

---

## 5. Integridade e autenticidade (arquivos adulterados)

Use uma **cópia** da pasta. O pacote original nunca deve ser alterado.

```bash
# copia limpa (sem os .docx decriptados na secao 4)
rm -rf /tmp/FilesTeste && cp -r Pacote-T3/Files /tmp/FilesTeste && rm -f /tmp/FilesTeste/*.docx

# inverte os bits de 1 byte (posicao 10) de um arquivo
adulterar() { python3 -c "import sys; p=sys.argv[1]; b=bytearray(open(p,'rb').read()); b[10]^=0xFF; open(p,'wb').write(b)" "$1"; }
```

Para cada linha:
1. Recrie a cópia limpa.
2. Aplique a alteração.
3. Entre como **user01** (frase `user01`) e liste a pasta `/tmp/FilesTeste`.

| ☐ | Alteração | Ação | Resultado esperado | Últimos códigos |
| :--- | :--- | :--- | :--- | :--- |
| [ ] | `adulterar /tmp/FilesTeste/index.asd` | Listar | Falha na verificacao ... arquivo de indice | 7005 7008 |
| [ ] | `adulterar /tmp/FilesTeste/index.enc` | Listar | Falha na verificacao ... arquivo de indice | 7005 7008 |
| [ ] | `rm /tmp/FilesTeste/index.env` | Listar | Falha na decriptacao do arquivo de indice | 7007 |
| [ ] | `adulterar /tmp/FilesTeste/share.asd` | Listar | Falha na verificacao ... arquivo de share | 7005 7006 7010 7013 |
| [ ] | `adulterar /tmp/FilesTeste/share.env` | Listar | Falha na decriptacao do arquivo de share | 7005 7006 7012 |
| [ ] | `adulterar /tmp/FilesTeste/XXYYZZ11.asd` | Abrir teste01.docx | Falha na verificacao de integridade e autenticidade | 7015 7016 7018 7021 |
| [ ] | `adulterar /tmp/FilesTeste/XXYYZZ11.enc` | Abrir teste01.docx | Falha na verificacao de integridade e autenticidade | 7015 7016 7018 7021 |
| [ ] | `adulterar /tmp/FilesTeste/XXYYZZ11.env` | Abrir teste01.docx | Falha na decriptacao do arquivo (sigilo) | 7015 7016 7020 |
| [ ] | `adulterar /tmp/FilesTeste/XXYYZZ22.1.shr` | Abrir teste02.docx | Falha na decriptacao do arquivo (sigilo) | 7015 7016 7020 |

**Observe:**
* Em nenhum caso de falha o arquivo decriptado é gravado na pasta.
* Alterar o `.enc` faz a decriptação "funcionar" (7018), mas o conteúdo sai corrompido, e a assinatura detecta isso (7021).

---

## 6. Tela de Saída

- [ ] Clique em **3 - Sair do Sistema**. A tela mostra:
  * o mesmo cabeçalho;
  * "Total de acessos do usuario";
  * a mensagem "Pressione o botao Encerrar Sessao ou o botao Encerrar Sistema para confirmar.";
  * os três botões.
  * Códigos: `5004 8001`.
- [ ] **Voltar ao Menu Principal:** códigos `8004 5001`.
- [ ] **Encerrar Sessão:** volta para o login (etapa 1). Códigos: `8002 1004 2001`.
- [ ] **Encerrar Sistema:** a janela fecha. Códigos: `8003 1004 1002`.
- [ ] **X da janela com sessão aberta:** códigos `1004 1002`.
- [ ] **X da janela durante a autenticação:** código `1002`.

---

## 7. Reinício do sistema (partida normal)

```bash
java -jar cofre.jar
```

- [ ] Abre o diálogo pedindo a **frase secreta do administrador** (admin@inf1416.puc-rio.br).
- [ ] **Frase errada:** "Chave privada do administrador verificada negativamente: ... O sistema sera encerrado." O sistema fecha. Códigos: `1001 1006 1002`.
- [ ] **Cancelar:** "Partida do sistema cancelada." O sistema fecha.
- [ ] **Frase `admin`:** abre a tela de login. Códigos: `1001 1006 2001`.
- [ ] **Persistência:**
  * Os usuários continuam cadastrados.
  * "Total de acessos" continua de onde parou.
  * O bloqueio ainda em andamento continua valendo.

---

## 8. Programa de auditoria `logView`

| ☐ | Comando / entrada | Resultado esperado |
| :--- | :--- | :--- |
| [ ] | `java -jar logview.jar Pacote-T3/Keys/admin-pkcs8-aes.key`, frase `admin` | "Administrador autenticado", depois os registros com data, hora, código e mensagem, em ordem cronológica. A frase **não aparece** ao digitar. |
| [ ] | Mesmo comando, frase `errada` | Autenticacao negada: frase secreta invalida para a chave privada informada. |
| [ ] | `java -jar logview.jar Pacote-T3/Keys/user01-pkcs8-aes.key`, frase `user01` | Autenticacao negada: a chave privada nao pertence a um administrador do sistema ... |
| [ ] | `java -jar logview.jar /nao/existe.key` | Erro: caminho do arquivo da chave privada invalido |
| [ ] | `java -jar logview.jar Pacote-T3/Keys/admin-pkcs8-aes.key < /dev/null` (sem terminal) | Erro: execute o logView em um terminal ... |
| [ ] | `unzip -l cofre.jar \| grep LogView` | Nenhuma saída: o logView **não** faz parte do programa do cofre. |

### 8.1 (Opcional) A tabela Registros não guarda o texto das mensagens

```bash
python3 -c "import sqlite3; c=sqlite3.connect('cofre.db'); print([d[1] for d in c.execute('PRAGMA table_info(Registros)')]); [print(r) for r in c.execute('SELECT * FROM Registros ORDER BY RID DESC LIMIT 5')]"
```

As colunas são `RID, MID, data_hora, UID, login_name, arquivo`. O texto fica só na tabela `Mensagens`, ligado pelo `MID`.

---

## 9. Checklist rápido para a apresentação

- [ ] Primeira execução cadastra o admin; reinício pede a frase do admin.
- [ ] Teclado virtual sobrecarregado: redistribui a cada clique, nunca mostra os dígitos.
- [ ] TOTP do Google Authenticator aceito, inclusive o código anterior (−30 s).
- [ ] Bloqueio de 2 minutos após 3 erros de senha e após 3 erros de token.
- [ ] Admin cadastra usuários; o grupo Usuário não vê a opção 1.
- [ ] user01 abre o arquivo compartilhado; user02 recebe acesso negado no arquivo do grupo.
- [ ] Arquivo adulterado é detectado (integridade/autenticidade).
- [ ] Encerrar Sessão × Encerrar Sistema.
- [ ] `logView` separado, só para o admin, frase sem eco, registros em ordem cronológica.
