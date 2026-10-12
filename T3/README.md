# Cofre Digital

Trabalho 3 da disciplina **INF1416 – Segurança da Informação**  
Departamento de Informática – PUC-Rio  
Professor: Anderson Oliveira da Silva  

---

## 1. Visão Geral

O **Cofre Digital** é uma aplicação Java (Swing) que protege uma *pasta segura* de arquivos com:

* **Autenticação multifator em 3 etapas:** login name (e-mail) → senha pessoal digitada num *teclado virtual numérico sobrecarregado* (verificada com bcrypt) → token TOTP do Google Authenticator.
* **Sigilo, integridade e autenticidade dos arquivos:** criptograma AES/ECB/PKCS5Padding, envelope digital RSA (semente do SHA1PRNG) e assinatura digital SHA1withRSA.
* **Banco de dados relacional (SQLite)** com as tabelas `Usuarios`, `Chaveiro`, `Grupos`, `Mensagens` e `Registros`.
* **Programa de auditoria separado (`logView`)**, executável apenas pelo administrador.

---

## 2. Requisitos e Bibliotecas

* **JDK 1.8 (Java 8) update 161** ou superior. O código gera bytecode Java 8 e foi testado num runtime Java 8 (Temurin 1.8.0_504) e no JDK 23.
  * Java 8 anterior ao update 161 limita o AES a 128 bits (política de criptografia restrita). Nesse caso, o cofre e o `logView` avisam e encerram, em vez de falhar com "frase secreta inválida".
* Bibliotecas na pasta `lib/` (não versionadas no git):

| Biblioteca | Uso | Download (Maven Central) |
| :--- | :--- | :--- |
| `bcprov-jdk18on-1.86.jar` | BouncyCastle: `OpenBSDBCrypt` (hash bcrypt da senha) | https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.86/bcprov-jdk18on-1.86.jar |
| `sqlite-jdbc-3.53.4.0.jar` | Driver JDBC do SQLite (o JDK não traz driver SQLite) | https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar |

Todo o restante (AES, RSA, SHA1PRNG, assinatura, X.509, BASE64, HMAC do TOTP, interface gráfica) vem do próprio JDK. A classe `TOTP` usa **apenas** `Mac`, `SecretKeySpec`, `Date` e `Base32`, como exige o enunciado.

---

## 3. Compilação

### Linux / macOS

```bash
./build.sh
```

Gera `cofre.jar` e `logview.jar`. O `logview.jar` é um programa separado: suas classes não fazem parte do `cofre.jar`.

### Windows

No **Git Bash**, o mesmo `./build.sh` funciona (usa `;` como separador do classpath). O instalador do JDK no Windows só coloca `java`/`javac` no PATH; para o `jar`, defina `JAVA_HOME` (ex.: `C:\Program Files\Java\jdk-23`) ou coloque `<JDK>\bin` no PATH. Detalhes em [`HowToTest.md`](HowToTest.md).

### Compilação manual (sem gerar os jars)

```bat
mkdir build\classes
javac -encoding UTF-8 -cp "lib\bcprov-jdk18on-1.86.jar;lib\sqlite-jdbc-3.53.4.0.jar" -d build\classes src\*.java
```

> Todos os fontes estão em ASCII puro (acentos como escapes `\u`), então compilam igual em qualquer codificação padrão do sistema.

---

## 4. Execução

O banco `cofre.db` é criado/aberto no **diretório corrente**. Execute sempre a partir da pasta `T3`.

```bash
java -jar cofre.jar
java -jar logview.jar Pacote-T3/Keys/admin-pkcs8-aes.key
```

Sem os jars (ex.: Windows após a compilação manual):

```bat
java -cp "build\classes;lib\bcprov-jdk18on-1.86.jar;lib\sqlite-jdbc-3.53.4.0.jar" CofreDigital
java -cp "build\classes;lib\sqlite-jdbc-3.53.4.0.jar" LogView Pacote-T3\Keys\admin-pkcs8-aes.key
```

O `logView` lê a frase secreta **sem eco** (`System.console()`), por isso deve ser executado num terminal de verdade (não no console de uma IDE).

---

## 5. Roteiro de Demonstração com o Pacote de Testes

> O roteiro completo de testes manuais, com resultados e códigos de registro esperados, está em [`HowToTest.md`](HowToTest.md).

Frases secretas das chaves do pacote: `admin`, `user01` e `user02`.

1. **Primeira execução** (`cofre.db` inexistente): abre o cadastro do administrador.
   * Certificado `Pacote-T3/Keys/admin-x509.crt`, chave `Pacote-T3/Keys/admin-pkcs8-aes.key`, frase `admin`.
   * Escolha uma senha de 8 a 10 dígitos (ex.: `13572468`).
   * Cadastre no Google Authenticator a chave BASE32 apresentada ("Inserir chave de configuração", "Baseada no horário").
2. **Login do admin**: e-mail, senha pelo teclado virtual e código TOTP.
3. **Opção 1**: cadastrar `user01` e `user02` (frases `user01` e `user02`).
4. **Opção 2** com a pasta `Pacote-T3/Files` e a frase `admin`: lista `teste00.docx`. Clique duas vezes para decriptar (o arquivo é gravado na própria pasta).
5. **Encerrar Sessão** e entrar como `user01`: lista `teste01.docx` (dono) e `teste02.docx` (compartilhado via `XXYYZZ22.1.shr`). Ambos abrem.
6. Entrar como `user02`: lista `teste01.docx` e `teste02.docx` (mesmo grupo). `teste01.docx` dá **acesso negado**, pois não há envelope para a chave do user02.
7. **Erros**:
   * 3 senhas erradas, ou 3 tokens errados, bloqueiam o usuário por 2 minutos.
   * Frase secreta errada é rejeitada no cadastro e na consulta.
   * Certificado com a chave de outro usuário é rejeitado (assinatura digital inválida).
8. **Reiniciar o sistema**: a partida pede a frase do admin. Frase errada encerra o sistema.
9. **Auditoria**: `java -jar logview.jar Pacote-T3/Keys/admin-pkcs8-aes.key` (frase `admin`).

---

## 6. Decisões de Interpretação

| Ponto do enunciado | Decisão |
| :--- | :--- |
| "Não podem ser aceitas sequências de números repetidos" | Rejeita dígitos iguais em sequência (ex.: `11`), o que também cobre `11111111`. |
| Grupo que pode ver x grupo que pode abrir | A listagem mostra os arquivos do dono, do grupo e os compartilhados. Só abre quem tem envelope: o dono (`.env`) ou o usuário do compartilhamento (`.N.shr`). Os demais recebem "acesso negado" (7017). |
| Frase secreta inválida na consulta (sem código próprio) | Registra 6006/6007, cujos textos descrevem exatamente o evento. |
| Total de acessos / consultas | Acesso = sessão iniciada (1003). Consulta = cada clique em Listar (7003). |
| Corpo 1 / corpo 2 da tela de consulta | O texto pede "o mesmo cabeçalho e corpo 1 da Tela Principal" e o total de consultas no corpo 2: o corpo 1 mostra "Total de acessos do usuário" e o corpo 2 começa com "Total de consultas do usuário". |
| Registro 6001 na partida | A tela de cadastro do administrador também grava 6001 (sem login name, pois ainda não há usuário). |
| Administrador do sistema | O usuário cadastrado na partida (menor UID do grupo Administrador). O `logView` aceita a chave de qualquer usuário do grupo Administrador. |
| Erros consecutivos e bloqueio | Ficam no banco (`erros_senha`, `erros_token`, `bloqueado_ate`). Continuam valendo se o sistema for reiniciado e são zerados no bloqueio. |
| Ordem do cadastro | Senha → certificado → chave privada → confirmação → login único → gravação. |
| "Frase secreta do administrador mantida em memória" | A frase é validada na partida e usada uma única vez para restaurar a chave privada do administrador. A memória guarda o objeto `PrivateKey` (o que o sistema realmente precisa para abrir `index.env` e `share.env`), e o `char[]` da frase é zerado logo em seguida. No encerramento, a referência à chave é apagada. |

---

## 7. Estrutura

| Arquivo | Responsabilidade |
| :--- | :--- |
| `CofreDigital.java` | `main`, partida do sistema, navegação entre telas, chave do admin em memória |
| `Autenticador.java` | Regras das 3 etapas (bcrypt em paralelo sobre as 2^n combinações, TOTP, bloqueio) |
| `ServicoCadastro.java` | Crítica do formulário, validação da chave privada, bcrypt `$2y$08$`, chave TOTP cifrada |
| `PastaSegura.java` | Decriptação/verificação de `index`, `share` e dos arquivos protegidos |
| `Criptografia.java` | Operações JCA: SHA1PRNG→AES, PKCS8, X.509, envelope e assinatura |
| `BancoDeDados.java` | SQLite: esquema, tabela de mensagens, consultas e registros |
| `TOTP.java`, `Base32.java` | TOTP (RFC 6238/4226) e BASE32 (anexo do enunciado) |
| `Tela*.java`, `Componentes.java` | Interface Swing (cabeçalho, corpo 1 e corpo 2) |
| `LogView.java` | Programa de auditoria separado |
